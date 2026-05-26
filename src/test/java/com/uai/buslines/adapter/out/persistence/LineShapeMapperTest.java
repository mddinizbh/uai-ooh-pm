package com.uai.buslines.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.domain.model.Coordinate;
import com.uai.buslines.domain.model.GtfsRouteShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit test — verifies that {@link LineShapeMapper} correctly serializes/deserializes
 * route geometry to/from GeoJSON LineString and round-trips a
 * {@link GtfsRouteShape} through the entity representation.
 */
class LineShapeMapperTest {

    private LineShapeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new LineShapeMapper(new ObjectMapper());
    }

    // ── GeoJSON coordinate order ───────────────────────────────────────────────

    @Test
    void toLineStringGeoJson_coordinateOrderIsLonLat() {
        // GeoJSON requires [longitude, latitude]; Coordinate is (lat, lon)
        List<Coordinate> points = List.of(
                new Coordinate(-19.9167, -43.9345)); // lat=-19.9167, lon=-43.9345

        String geoJson = LineShapeMapper.toLineStringGeoJson(points);

        // GeoJSON [lon, lat] = [-43.9345, -19.9167]
        assertThat(geoJson).contains("{\"type\":\"LineString\"");
        assertThat(geoJson).contains("-43.9345");  // lon first
        assertThat(geoJson).contains("-19.9167");  // lat second

        // Parse back and verify round-trip preserves order
        List<Coordinate> parsed = mapper.parseLineStringGeoJson(geoJson);
        assertThat(parsed.get(0).lat()).isCloseTo(-19.9167, within(1e-9));
        assertThat(parsed.get(0).lon()).isCloseTo(-43.9345, within(1e-9));
    }

    @Test
    void toLineStringGeoJson_multiplePoints() {
        List<Coordinate> points = List.of(
                new Coordinate(-19.9167, -43.9345),
                new Coordinate(-19.8950, -43.9200),
                new Coordinate(-19.8800, -43.9100));

        String geoJson = LineShapeMapper.toLineStringGeoJson(points);
        List<Coordinate> parsed = mapper.parseLineStringGeoJson(geoJson);

        assertThat(parsed).hasSize(3);
        for (int i = 0; i < points.size(); i++) {
            assertThat(parsed.get(i).lat()).as("lat[%d]", i)
                    .isCloseTo(points.get(i).lat(), within(1e-9));
            assertThat(parsed.get(i).lon()).as("lon[%d]", i)
                    .isCloseTo(points.get(i).lon(), within(1e-9));
        }
    }

    @Test
    void toLineStringGeoJson_singlePoint_producesValidJson() {
        List<Coordinate> points = List.of(new Coordinate(0.0, 0.0));

        String geoJson = LineShapeMapper.toLineStringGeoJson(points);

        assertThat(geoJson).isEqualTo("{\"type\":\"LineString\",\"coordinates\":[[0.0,0.0]]}");
    }

    // ── toEntity round-trip ────────────────────────────────────────────────────

    @Test
    void toEntity_setsLineIdDirectionAndDatasetVersion() {
        List<Coordinate> points = List.of(
                new Coordinate(-19.9167, -43.9345),
                new Coordinate(-19.8800, -43.9100));
        GtfsRouteShape shape = new GtfsRouteShape("R001", 0, points, List.of());

        LineShapeEntity entity = mapper.toEntity(shape, 10L, 3L);

        assertThat(entity.getLineId()).isEqualTo(10L);
        assertThat(entity.getDirection()).isEqualTo((short) 0);
        assertThat(entity.getDatasetVersion()).isEqualTo(3L);
        assertThat(entity.getId()).isNull();
    }

    @Test
    void toEntity_direction1_encodedCorrectly() {
        GtfsRouteShape shape = new GtfsRouteShape("R001", 1,
                List.of(new Coordinate(0.0, 0.0)), List.of());

        LineShapeEntity entity = mapper.toEntity(shape, 5L, 1L);

        assertThat(entity.getDirection()).isEqualTo((short) 1);
    }

    // ── toDomain round-trip ────────────────────────────────────────────────────

    @Test
    void roundTrip_preservesShapePointsAndDirection() {
        List<Coordinate> points = List.of(
                new Coordinate(-19.9167, -43.9345),
                new Coordinate(-19.8950, -43.9200),
                new Coordinate(-19.8800, -43.9100));
        GtfsRouteShape original = new GtfsRouteShape("R001", 0, points, List.of());

        LineShapeEntity entity = mapper.toEntity(original, 10L, 3L);
        GtfsRouteShape restored = mapper.toDomain(entity, "R001");

        assertThat(restored.routeId()).isEqualTo("R001");
        assertThat(restored.direction()).isEqualTo(0);
        assertThat(restored.shapePoints()).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(restored.shapePoints().get(i).lat())
                    .isCloseTo(points.get(i).lat(), within(1e-9));
            assertThat(restored.shapePoints().get(i).lon())
                    .isCloseTo(points.get(i).lon(), within(1e-9));
        }
    }

    @Test
    void toDomain_orderedStops_isEmptyBecauseNotStoredInSchema() {
        // orderedStops are not persisted in line_shape (no column in V1 schema)
        GtfsRouteShape original = new GtfsRouteShape("R001", 0,
                List.of(new Coordinate(0.0, 0.0)), List.of());

        LineShapeEntity entity = mapper.toEntity(original, 1L, 1L);
        GtfsRouteShape restored = mapper.toDomain(entity, "R001");

        assertThat(restored.orderedStops()).isEmpty();
    }
}
