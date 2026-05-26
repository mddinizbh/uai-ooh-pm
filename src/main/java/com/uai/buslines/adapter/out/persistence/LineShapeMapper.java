package com.uai.buslines.adapter.out.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.domain.model.Coordinate;
import com.uai.buslines.domain.model.GtfsRouteShape;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapper between {@link GtfsRouteShape} (domain) and {@link LineShapeEntity} (persistence).
 *
 * <p>Route geometry is serialized to/from a GeoJSON LineString stored in the
 * {@code geometry_geojson jsonb} column (ADR-003).
 *
 * <h3>Coordinate order convention</h3>
 * GeoJSON uses {@code [longitude, latitude]} order.
 * The domain {@link Coordinate} is {@code (lat, lon)}.
 * This class handles the transposition explicitly.
 *
 * <h3>orderedStops is not persisted</h3>
 * {@link GtfsRouteShape#orderedStops()} has no corresponding column in {@code line_shape}.
 * {@link #toDomain(LineShapeEntity, String)} returns an empty list for {@code orderedStops}.
 * Task_06 addresses stop-per-line retrieval separately.
 */
class LineShapeMapper {

    private final ObjectMapper objectMapper;

    LineShapeMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Entity → Domain ────────────────────────────────────────────────────────

    /**
     * Converts a {@link LineShapeEntity} to a {@link GtfsRouteShape}.
     *
     * @param entity   the persistence entity
     * @param routeId  the GTFS route_id (not stored in line_shape — must be resolved by caller)
     */
    GtfsRouteShape toDomain(LineShapeEntity entity, String routeId) {
        List<Coordinate> points = parseLineStringGeoJson(entity.getGeometryGeoJson());
        return new GtfsRouteShape(routeId, entity.getDirection(), points, List.of());
    }

    // ── Domain → Entity ────────────────────────────────────────────────────────

    /**
     * Converts a {@link GtfsRouteShape} to a {@link LineShapeEntity}.
     *
     * @param shape          the domain shape
     * @param lineId         DB primary key of the parent {@code line} row
     * @param datasetVersion the version to tag the row with
     */
    LineShapeEntity toEntity(GtfsRouteShape shape, long lineId, long datasetVersion) {
        String geoJson = toLineStringGeoJson(shape.shapePoints());
        return new LineShapeEntity(lineId, (short) shape.direction(), geoJson, datasetVersion);
    }

    // ── GeoJSON helpers (package-private for unit tests) ──────────────────────

    /**
     * Serializes a list of WGS84 coordinates to a GeoJSON LineString string.
     * Output: {@code {"type":"LineString","coordinates":[[lon,lat],...]}}
     */
    static String toLineStringGeoJson(List<Coordinate> points) {
        StringBuilder sb = new StringBuilder("{\"type\":\"LineString\",\"coordinates\":[");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) sb.append(',');
            Coordinate c = points.get(i);
            // GeoJSON: [longitude, latitude] — Coordinate is (lat, lon)
            sb.append('[').append(c.lon()).append(',').append(c.lat()).append(']');
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Parses a GeoJSON LineString string into a list of domain {@link Coordinate}s.
     * Expects: {@code {"type":"LineString","coordinates":[[lon,lat],...]}}
     */
    List<Coordinate> parseLineStringGeoJson(String geoJson) {
        try {
            JsonNode root = objectMapper.readTree(geoJson);
            JsonNode coords = root.get("coordinates");
            List<Coordinate> points = new ArrayList<>(coords.size());
            for (JsonNode coord : coords) {
                double lon = coord.get(0).asDouble(); // GeoJSON index 0 = longitude
                double lat = coord.get(1).asDouble(); // GeoJSON index 1 = latitude
                points.add(new Coordinate(lat, lon));
            }
            return List.copyOf(points);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse GeoJSON LineString: " + geoJson, e);
        }
    }
}
