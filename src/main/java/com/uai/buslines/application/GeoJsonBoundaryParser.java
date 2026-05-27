package com.uai.buslines.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.uai.buslines.domain.model.BoundaryParseException;
import com.uai.buslines.domain.model.Neighborhood;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless parser that converts a raw GeoJSON {@code FeatureCollection} byte array
 * into a list of {@link Neighborhood} domain objects.
 *
 * <p>Validates that:
 * <ul>
 *   <li>The root type is {@code FeatureCollection}.</li>
 *   <li>If a CRS property is present (old-style GeoJSON 2008 format), it identifies WGS84
 *       (EPSG:4326). RFC 7946 GeoJSON has no CRS property and defaults to WGS84.</li>
 *   <li>Each feature with a non-null geometry has type {@code Polygon} or
 *       {@code MultiPolygon}. Features with null geometry are silently skipped.</li>
 * </ul>
 *
 * <p>Lives in the application layer; no HTTP, JPA, or Spring Boot-specific dependencies.
 */
@Component
public class GeoJsonBoundaryParser {

    /**
     * CRS name patterns that identify WGS84 (EPSG:4326).
     * Matched case-insensitively as substrings of the CRS name value.
     */
    private static final List<String> WGS84_CRS_PATTERNS = List.of(
            "4326",     // EPSG:4326, urn:ogc:def:crs:EPSG::4326
            "CRS84",    // urn:ogc:def:crs:OGC:1.3:CRS84, OGC:CRS84
            "WGS84",    // direct name variant
            "WGS 84"    // human-readable variant
    );

    /**
     * Property field names to try when extracting the neighbourhood name from GeoJSON
     * feature properties. Tried in order; first non-blank match wins.
     */
    private static final List<String> NAME_FIELDS = List.of(
            "NOME", "nome", "Nome", "NAME", "name"
    );

    /** Extracts the numeric EPSG code from a CRS name (e.g. "urn:ogc:def:crs:EPSG::32723"). */
    private static final Pattern EPSG_PATTERN =
            Pattern.compile("EPSG[^0-9]*([0-9]{4,6})", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public GeoJsonBoundaryParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Parses a GeoJSON {@code FeatureCollection} byte array into a list of
     * {@link Neighborhood} objects.
     *
     * @param geoJsonBytes raw bytes of the GeoJSON file (UTF-8 encoded)
     * @return list of parsed neighbourhoods; never {@code null}, may be empty
     * @throws BoundaryParseException on structural errors, non-WGS84 CRS, or unsupported
     *         geometry types
     */
    public List<Neighborhood> parse(byte[] geoJsonBytes) {
        JsonNode root;
        try {
            root = objectMapper.readTree(geoJsonBytes);
        } catch (IOException e) {
            throw new BoundaryParseException(
                    "Failed to parse boundary file as JSON: " + e.getMessage(), e);
        }

        // Determine the source CRS (old-style GeoJSON 2008 CRS member). RFC 7946
        // GeoJSON has no CRS member and defaults to WGS84. Non-WGS84 sources are
        // reprojected to WGS84 at parse time (ADR-003).
        JsonNode crsNode = root.get("crs");
        CrsReprojector reproj = (crsNode != null && !crsNode.isNull())
                ? buildReprojector(crsNode)
                : CrsReprojector.identity();

        String type = root.path("type").asText("");
        if (!"FeatureCollection".equals(type)) {
            throw new BoundaryParseException(
                    "Expected a GeoJSON FeatureCollection but got type: '" + type + "'");
        }

        JsonNode features = root.path("features");
        if (features.isMissingNode() || !features.isArray()) {
            throw new BoundaryParseException(
                    "GeoJSON FeatureCollection has no 'features' array");
        }

        List<Neighborhood> neighborhoods = new ArrayList<>();
        for (int i = 0; i < features.size(); i++) {
            Neighborhood n = parseFeature(features.get(i), i, reproj);
            if (n != null) {
                neighborhoods.add(n);
            }
        }
        return neighborhoods;
    }

    // ── CRS handling ──────────────────────────────────────────────────────────

    /**
     * Builds a {@link CrsReprojector} from a GeoJSON 2008 CRS member. WGS84 CRS
     * names yield an identity reprojector; recognised projected CRS yield a real
     * transform; unreadable or unsupported names fail.
     */
    private CrsReprojector buildReprojector(JsonNode crsNode) {
        // Old GeoJSON CRS object: {"type": "name", "properties": {"name": "..."}}
        String crsName = crsNode.path("properties").path("name").asText("").trim();
        if (crsName.isEmpty() || isWgs84Crs(crsName)) {
            // Absent/unreadable or already WGS84 — pass coordinates through unchanged.
            return CrsReprojector.identity();
        }
        Integer epsg = extractEpsgCode(crsName);
        if (epsg == null) {
            throw new BoundaryParseException(
                    "Boundary file declares an unrecognised CRS '" + crsName
                    + "'. Cannot determine how to reproject to WGS84 (ADR-003).");
        }
        return CrsReprojector.forEpsg(epsg);
    }

    /** Extracts the EPSG numeric code from a CRS name, or {@code null} if none. */
    static Integer extractEpsgCode(String crsName) {
        Matcher m = EPSG_PATTERN.matcher(crsName);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /**
     * Returns {@code true} if {@code crsName} identifies WGS84.
     * Package-private for unit testability.
     */
    boolean isWgs84Crs(String crsName) {
        String upper = crsName.toUpperCase();
        for (String pattern : WGS84_CRS_PATTERNS) {
            if (upper.contains(pattern.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    // ── Feature parsing ─────────────────────────────────────────────────────────

    private Neighborhood parseFeature(JsonNode feature, int index, CrsReprojector reproj) {
        JsonNode geometry = feature.path("geometry");

        // Skip features with null or missing geometry (common in PBH summary rows)
        if (geometry.isMissingNode() || geometry.isNull()) {
            return null;
        }

        String geomType = geometry.path("type").asText("");
        if (!"Polygon".equals(geomType) && !"MultiPolygon".equals(geomType)) {
            throw new BoundaryParseException(
                    "Feature at index " + index + " has unsupported geometry type: '"
                    + geomType + "'. Only Polygon and MultiPolygon are supported.");
        }

        String name = extractName(feature.path("properties"), index);

        // Reproject coordinates to WGS84 in place when the source CRS is projected.
        if (!reproj.isIdentity()) {
            reprojectCoordinates(geometry.path("coordinates"), reproj);
        }

        String boundaryGeoJson;
        try {
            boundaryGeoJson = objectMapper.writeValueAsString(geometry);
        } catch (IOException e) {
            throw new BoundaryParseException(
                    "Failed to serialize geometry for feature '" + name + "': "
                    + e.getMessage(), e);
        }

        return new Neighborhood(name, boundaryGeoJson);
    }

    /**
     * Recursively reprojects a GeoJSON {@code coordinates} node to WGS84 in place.
     * Leaf positions ({@code [x, y]}) are transformed; nested arrays are recursed.
     */
    private void reprojectCoordinates(JsonNode coordinates, CrsReprojector reproj) {
        if (!coordinates.isArray()) {
            return;
        }
        ArrayNode arr = (ArrayNode) coordinates;
        if (arr.size() >= 2 && arr.get(0).isNumber() && arr.get(1).isNumber()) {
            double[] lonLat = reproj.toWgs84(arr.get(0).asDouble(), arr.get(1).asDouble());
            arr.set(0, DoubleNode.valueOf(round7(lonLat[0])));
            arr.set(1, DoubleNode.valueOf(round7(lonLat[1])));
            return;
        }
        for (JsonNode child : arr) {
            reprojectCoordinates(child, reproj);
        }
    }

    private static double round7(double v) {
        return Math.round(v * 1e7) / 1e7;
    }

    /**
     * Extracts the neighbourhood name from feature properties.
     *
     * <p>Tries {@link #NAME_FIELDS} in order; falls back to a positional label when
     * none of the well-known property names are present. The fallback ensures the
     * parser is robust against unconventional attribute schemas.
     */
    private String extractName(JsonNode props, int index) {
        if (!props.isMissingNode() && !props.isNull()) {
            for (String field : NAME_FIELDS) {
                JsonNode node = props.get(field);
                if (node != null && !node.isNull()) {
                    String value = node.asText("").trim();
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
        }
        return "neighborhood_" + index;
    }
}
