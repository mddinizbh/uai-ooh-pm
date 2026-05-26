package com.uai.buslines.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.domain.model.BoundaryParseException;
import com.uai.buslines.domain.model.Coordinate;
import com.uai.buslines.domain.model.GtfsDataset;
import com.uai.buslines.domain.model.GtfsRouteShape;
import com.uai.buslines.domain.model.GtfsStop;
import com.uai.buslines.domain.model.LineNeighborhoodRelation;
import com.uai.buslines.domain.model.LineRelation;
import com.uai.buslines.domain.model.Neighborhood;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application service that classifies every GTFS route against every loaded
 * neighbourhood polygon using the Java Topology Suite (JTS).
 *
 * <p>Classification rules (ADR-003, applied per route shape/direction):
 * <ul>
 *   <li>{@link LineRelation.DepartsFrom} — the shape's <em>first</em> point is inside
 *       the neighbourhood polygon.</li>
 *   <li>{@link LineRelation.ArrivesAt}   — the shape's <em>last</em> point is inside
 *       the neighbourhood polygon.</li>
 *   <li>{@link LineRelation.PassesThrough} — any stop in the shape's ordered stop list
 *       is inside the neighbourhood polygon.</li>
 * </ul>
 *
 * <p>Each (routeId, neighbourhoodName, relationType) triple is unique in the result;
 * multiple shapes for the same route that produce duplicate triples are deduplicated.
 *
 * <p><strong>Boundary rule</strong>: points that fall exactly on a polygon boundary
 * are classified as "inside" (JTS {@code covers()} semantics). This is deterministic
 * and documented; callers may rely on this behaviour.
 *
 * <p>JTS objects (geometries) are transient — built at classification time and not
 * retained after this method returns. Geometries are not stored (ADR-003).
 *
 * <p><strong>Coordinate convention</strong>: JTS {@code Coordinate(x, y)} maps to
 * (longitude, latitude) in geographic context. GeoJSON {@code [lng, lat]} and domain
 * {@link Coordinate}{@code (lat, lon)} are both converted accordingly before JTS use.
 */
@Component
public class NeighborhoodClassifier {

    private static final Logger log = LoggerFactory.getLogger(NeighborhoodClassifier.class);

    /** JTS geometry factory with WGS84 SRID (4326). */
    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);

    private final ObjectMapper objectMapper;

    public NeighborhoodClassifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Classifies all routes in {@code dataset} against all loaded {@code neighborhoods}
     * and returns a new, enriched {@link GtfsDataset} containing both the neighbourhood
     * list and the computed {@link LineNeighborhoodRelation} list.
     *
     * @param dataset       parsed GTFS dataset (from task_02)
     * @param neighborhoods neighbourhood polygons (from {@code BoundaryGateway})
     * @return a new {@code GtfsDataset} with {@code neighborhoods} and
     *         {@code lineRelations} populated; all other fields are unchanged
     */
    public GtfsDataset classify(GtfsDataset dataset, List<Neighborhood> neighborhoods) {

        // Build transient JTS geometry cache (skips malformed neighbourhoods with a warning)
        Map<String, Geometry> geometryByName = buildGeometryCache(neighborhoods);

        // Collect unique (routeId, neighbourhoodName, relation) triples
        Set<LineNeighborhoodRelation> relations = new LinkedHashSet<>();

        for (GtfsRouteShape shape : dataset.routeShapes()) {
            for (Neighborhood neighborhood : neighborhoods) {
                Geometry poly = geometryByName.get(neighborhood.name());
                if (poly == null) {
                    continue; // geometry build failed — warning already logged
                }
                classifyShape(shape, neighborhood.name(), poly, relations);
            }
        }

        List<LineNeighborhoodRelation> relList = List.copyOf(relations);

        log.info("Classification complete: {} routes, {} neighbourhoods, {} relations produced",
                dataset.routes().size(), neighborhoods.size(), relList.size());

        return new GtfsDataset(
                dataset.feedEtag(),
                dataset.fetchedAt(),
                dataset.routes(),
                dataset.stops(),
                dataset.routeShapes(),
                List.copyOf(neighborhoods),
                relList
        );
    }

    // ── Classification rules ────────────────────────────────────────────────────

    /**
     * Applies the three classification rules for a single (shape, neighbourhood) pair
     * and adds any matching relations to {@code relations}.
     */
    private void classifyShape(GtfsRouteShape shape, String neighborhoodName,
                                Geometry poly, Set<LineNeighborhoodRelation> relations) {

        List<Coordinate> shapePoints = shape.shapePoints();

        // Rule 1: DEPARTS_FROM — first shape point inside polygon
        if (!shapePoints.isEmpty()) {
            Coordinate first = shapePoints.get(0);
            if (coversPoint(poly, first.lon(), first.lat())) {
                relations.add(new LineNeighborhoodRelation(
                        shape.routeId(), neighborhoodName, new LineRelation.DepartsFrom()));
            }
        }

        // Rule 2: ARRIVES_AT — last shape point inside polygon
        // Only evaluated when there is more than one shape point so first ≠ last
        if (shapePoints.size() > 1) {
            Coordinate last = shapePoints.get(shapePoints.size() - 1);
            if (coversPoint(poly, last.lon(), last.lat())) {
                relations.add(new LineNeighborhoodRelation(
                        shape.routeId(), neighborhoodName, new LineRelation.ArrivesAt()));
            }
        }

        // Rule 3: PASSES_THROUGH — any stop in the shape is inside polygon
        for (GtfsStop stop : shape.orderedStops()) {
            if (coversPoint(poly, stop.lon(), stop.lat())) {
                relations.add(new LineNeighborhoodRelation(
                        shape.routeId(), neighborhoodName, new LineRelation.PassesThrough()));
                break; // one matching stop is sufficient — no need to check the rest
            }
        }
    }

    // ── JTS geometry helpers ────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code poly} covers the point at the given
     * longitude and latitude.
     *
     * <p>Uses {@code covers()} (not {@code contains()}) so that points exactly on a
     * polygon boundary are classified as "inside" — deterministic boundary rule.
     *
     * @param poly WGS84 JTS polygon or multi-polygon
     * @param lng  longitude (JTS x-axis)
     * @param lat  latitude  (JTS y-axis)
     */
    private boolean coversPoint(Geometry poly, double lng, double lat) {
        Point point = gf.createPoint(new org.locationtech.jts.geom.Coordinate(lng, lat));
        return poly.covers(point);
    }

    /**
     * Builds transient JTS {@link Geometry} objects for each neighbourhood.
     * Neighbourhoods whose boundary GeoJSON cannot be parsed are logged and skipped.
     */
    private Map<String, Geometry> buildGeometryCache(List<Neighborhood> neighborhoods) {
        Map<String, Geometry> cache = new LinkedHashMap<>();
        for (Neighborhood n : neighborhoods) {
            try {
                cache.put(n.name(), buildGeometry(n.boundaryGeoJson()));
            } catch (Exception e) {
                log.warn("Skipping neighbourhood '{}' — JTS geometry build failed: {}",
                        n.name(), e.getMessage());
            }
        }
        return cache;
    }

    /**
     * Builds a JTS {@link Geometry} from a GeoJSON geometry string.
     * Supports {@code Polygon} and {@code MultiPolygon} types.
     *
     * <p>Package-private for unit testability.
     *
     * @throws BoundaryParseException on malformed JSON or unsupported geometry type
     */
    Geometry buildGeometry(String geoJsonGeometry) {
        JsonNode node;
        try {
            node = objectMapper.readTree(geoJsonGeometry);
        } catch (IOException e) {
            throw new BoundaryParseException(
                    "Cannot parse geometry GeoJSON: " + e.getMessage(), e);
        }
        String type = node.path("type").asText("");
        return switch (type) {
            case "Polygon"      -> buildPolygon(node.path("coordinates"));
            case "MultiPolygon" -> buildMultiPolygon(node.path("coordinates"));
            default -> throw new BoundaryParseException(
                    "Unsupported JTS geometry type: '" + type + "'");
        };
    }

    private Polygon buildPolygon(JsonNode rings) {
        // GeoJSON Polygon: coordinates[0] = exterior ring, [1..n] = holes
        org.locationtech.jts.geom.Coordinate[] shellCoords = buildRingCoordinates(rings.get(0));
        if (rings.size() <= 1) {
            return gf.createPolygon(shellCoords);
        }
        LinearRing shell = gf.createLinearRing(shellCoords);
        LinearRing[] holes = new LinearRing[rings.size() - 1];
        for (int i = 1; i < rings.size(); i++) {
            holes[i - 1] = gf.createLinearRing(buildRingCoordinates(rings.get(i)));
        }
        return gf.createPolygon(shell, holes);
    }

    private MultiPolygon buildMultiPolygon(JsonNode polygonsNode) {
        Polygon[] polygons = new Polygon[polygonsNode.size()];
        for (int i = 0; i < polygonsNode.size(); i++) {
            polygons[i] = buildPolygon(polygonsNode.get(i));
        }
        return gf.createMultiPolygon(polygons);
    }

    /**
     * Converts a GeoJSON ring (array of [lng, lat] pairs) to a JTS
     * {@code Coordinate[]} array.
     *
     * <p>GeoJSON rings are in [longitude, latitude] order; JTS
     * {@code Coordinate(x, y)} maps x→longitude, y→latitude.
     */
    private org.locationtech.jts.geom.Coordinate[] buildRingCoordinates(JsonNode ringNode) {
        org.locationtech.jts.geom.Coordinate[] coords =
                new org.locationtech.jts.geom.Coordinate[ringNode.size()];
        for (int i = 0; i < ringNode.size(); i++) {
            JsonNode pt = ringNode.get(i);
            double lng = pt.get(0).asDouble(); // GeoJSON index 0 = longitude
            double lat = pt.get(1).asDouble(); // GeoJSON index 1 = latitude
            coords[i] = new org.locationtech.jts.geom.Coordinate(lng, lat);
        }
        return coords;
    }
}
