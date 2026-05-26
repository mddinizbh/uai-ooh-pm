package com.uai.buslines.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.domain.model.BoundaryParseException;
import com.uai.buslines.domain.model.Neighborhood;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

        // Validate CRS if present (old-style GeoJSON 2008 CRS member)
        JsonNode crsNode = root.get("crs");
        if (crsNode != null && !crsNode.isNull()) {
            validateCrs(crsNode);
        }

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
            Neighborhood n = parseFeature(features.get(i), i);
            if (n != null) {
                neighborhoods.add(n);
            }
        }
        return neighborhoods;
    }

    // ── CRS validation ──────────────────────────────────────────────────────────

    private void validateCrs(JsonNode crsNode) {
        // Old GeoJSON CRS object: {"type": "name", "properties": {"name": "..."}}
        String crsName = crsNode.path("properties").path("name").asText("").trim();
        if (crsName.isEmpty()) {
            // Present but unreadable format — treat as lenient (do not reject)
            return;
        }
        if (!isWgs84Crs(crsName)) {
            throw new BoundaryParseException(
                    "Boundary file CRS is not WGS84 (EPSG:4326). Found: '" + crsName
                    + "'. Only WGS84 boundaries are supported (ADR-003).");
        }
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

    private Neighborhood parseFeature(JsonNode feature, int index) {
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
