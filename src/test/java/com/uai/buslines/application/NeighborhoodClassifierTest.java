package com.uai.buslines.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.BoundaryFixtures;
import com.uai.buslines.domain.model.Coordinate;
import com.uai.buslines.domain.model.GtfsDataset;
import com.uai.buslines.domain.model.GtfsRoute;
import com.uai.buslines.domain.model.GtfsRouteShape;
import com.uai.buslines.domain.model.GtfsStop;
import com.uai.buslines.domain.model.LineNeighborhoodRelation;
import com.uai.buslines.domain.model.LineRelation;
import com.uai.buslines.domain.model.Neighborhood;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NeighborhoodClassifier}.
 *
 * <p>No Spring context — plain JUnit 5 with fixture {@link Neighborhood} objects.
 * Tests verify the three classification rules and boundary/edge behaviour.
 *
 * <h3>Coordinate convention reminder</h3>
 * Domain {@link Coordinate}(lat, lon) — JTS {@code Coordinate(x=lon, y=lat)}.
 * GeoJSON fixture polygons in {@link BoundaryFixtures} use [lon, lat] order.
 */
class NeighborhoodClassifierTest {

    private NeighborhoodClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new NeighborhoodClassifier(new ObjectMapper());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Creates a minimal dataset with a single route and one shape. */
    private GtfsDataset datasetWith(GtfsRouteShape shape) {
        GtfsRoute route = new GtfsRoute(shape.routeId(), "9999", "Test Route");
        return new GtfsDataset(null, Instant.now(),
                List.of(route), List.of(), List.of(shape));
    }

    /** A stop at a point inside the given neighbourhood box. */
    private GtfsStop stopAt(double lat, double lon) {
        return new GtfsStop("S1", "Test Stop", lat, lon);
    }

    /** A shape whose ONLY stop is at (lat, lon). Shape points are unused. */
    private GtfsRouteShape shapeWithStop(String routeId, double lat, double lon) {
        GtfsStop stop = stopAt(lat, lon);
        return new GtfsRouteShape(routeId, 0, List.of(), List.of(stop));
    }

    /** A shape with two shape points (first, last) and no stops. */
    private GtfsRouteShape shapeWithEndpoints(String routeId,
                                               double firstLat, double firstLon,
                                               double lastLat, double lastLon) {
        Coordinate first = new Coordinate(firstLat, firstLon);
        Coordinate last  = new Coordinate(lastLat,  lastLon);
        return new GtfsRouteShape(routeId, 0, List.of(first, last), List.of());
    }

    // ── 3.3a PASSES_THROUGH ──────────────────────────────────────────────────────

    @Test
    void stopInsidePolygonProducesPassesThrough() {
        // ST001 inside "Centro" polygon
        GtfsRouteShape shape = shapeWithStop("R001", -19.9167, -43.9345);
        Neighborhood centro = new Neighborhood(BoundaryFixtures.CENTRO_NAME,
                BoundaryFixtures.CENTRO_GEOJSON);

        GtfsDataset result = classifier.classify(datasetWith(shape), List.of(centro));

        assertThat(result.lineRelations())
                .containsExactly(new LineNeighborhoodRelation(
                        "R001", BoundaryFixtures.CENTRO_NAME, new LineRelation.PassesThrough()));
    }

    @Test
    void stopOutsideAllPolygonsProducesNoRelation() {
        // Stop outside all polygons (far from fixtures)
        GtfsRouteShape shape = shapeWithStop("R001", -20.0000, -45.0000);
        List<Neighborhood> neighborhoods = BoundaryFixtures.threeNeighborhoods();

        GtfsDataset result = classifier.classify(datasetWith(shape), neighborhoods);

        assertThat(result.lineRelations()).isEmpty();
    }

    // ── 3.3b DEPARTS_FROM ────────────────────────────────────────────────────────

    @Test
    void firstShapePointInsidePolygonProducesDepartsFrom() {
        // First point inside "Centro", last outside
        GtfsRouteShape shape = shapeWithEndpoints(
                "R001",
                -19.9167, -43.9345,   // inside Centro
                -20.0000, -45.0000    // outside all
        );
        Neighborhood centro = new Neighborhood(BoundaryFixtures.CENTRO_NAME,
                BoundaryFixtures.CENTRO_GEOJSON);

        GtfsDataset result = classifier.classify(datasetWith(shape), List.of(centro));

        assertThat(result.lineRelations())
                .containsExactly(new LineNeighborhoodRelation(
                        "R001", BoundaryFixtures.CENTRO_NAME, new LineRelation.DepartsFrom()));
    }

    // ── 3.3c ARRIVES_AT ──────────────────────────────────────────────────────────

    @Test
    void lastShapePointInsidePolygonProducesArrivesAt() {
        // First point outside, last inside "Centro"
        GtfsRouteShape shape = shapeWithEndpoints(
                "R001",
                -20.0000, -45.0000,   // outside all
                -19.9167, -43.9345    // inside Centro
        );
        Neighborhood centro = new Neighborhood(BoundaryFixtures.CENTRO_NAME,
                BoundaryFixtures.CENTRO_GEOJSON);

        GtfsDataset result = classifier.classify(datasetWith(shape), List.of(centro));

        assertThat(result.lineRelations())
                .containsExactly(new LineNeighborhoodRelation(
                        "R001", BoundaryFixtures.CENTRO_NAME, new LineRelation.ArrivesAt()));
    }

    // ── 3.3d No relation ─────────────────────────────────────────────────────────

    @Test
    void lineWithNoStopOrEndpointInPolygonProducesNoRelation() {
        // Shape with a point and stop both outside "Centro"
        GtfsStop stop = new GtfsStop("S99", "Far Stop", -20.0000, -45.0000);
        GtfsRouteShape shape = new GtfsRouteShape("R001", 0,
                List.of(new Coordinate(-20.0000, -45.0000),
                        new Coordinate(-20.1000, -45.1000)),
                List.of(stop));
        Neighborhood centro = new Neighborhood(BoundaryFixtures.CENTRO_NAME,
                BoundaryFixtures.CENTRO_GEOJSON);

        GtfsDataset result = classifier.classify(datasetWith(shape), List.of(centro));

        assertThat(result.lineRelations()).isEmpty();
    }

    // ── 3.3e Boundary edge — deterministic rule ──────────────────────────────────

    /**
     * A point exactly on the polygon boundary resolves to "inside" using
     * {@code covers()} semantics. This is the documented boundary rule.
     *
     * <p>The unit-square polygon covers [0,1]×[0,1] in (lng, lat) space.
     * The stop at (lat=0.0, lon=0.5) lies on the bottom edge.
     */
    @Test
    void stopExactlyOnPolygonBoundaryClassifiedAsInside() {
        // Stop at lat=0.0, lon=0.5 — on the bottom edge of the unit square [0,1]×[0,1]
        GtfsRouteShape shape = shapeWithStop("R001", 0.0, 0.5);
        Neighborhood square = new Neighborhood("Square",
                BoundaryFixtures.UNIT_SQUARE_GEOJSON);

        GtfsDataset result = classifier.classify(datasetWith(shape), List.of(square));

        assertThat(result.lineRelations())
                .containsExactly(new LineNeighborhoodRelation(
                        "R001", "Square", new LineRelation.PassesThrough()));
    }

    @Test
    void firstPointExactlyOnPolygonBoundaryProducesDepartsFrom() {
        // First point at (lat=0.0, lon=0.0) — corner of the unit square
        GtfsRouteShape shape = shapeWithEndpoints("R001",
                0.0, 0.0,   // on boundary (corner)
                5.0, 5.0    // outside
        );
        Neighborhood square = new Neighborhood("Square",
                BoundaryFixtures.UNIT_SQUARE_GEOJSON);

        GtfsDataset result = classifier.classify(datasetWith(shape), List.of(square));

        assertThat(result.lineRelations())
                .containsExactly(new LineNeighborhoodRelation(
                        "R001", "Square", new LineRelation.DepartsFrom()));
    }

    // ── 3.3f Deduplication across multiple shapes ────────────────────────────────

    @Test
    void duplicateRelationsFromMultipleShapesAreDeduped() {
        // Two shapes for R001, both with a stop inside Centro → only one PASSES_THROUGH
        GtfsStop stop = stopAt(-19.9167, -43.9345); // inside Centro
        GtfsRouteShape shape0 = new GtfsRouteShape("R001", 0, List.of(), List.of(stop));
        GtfsRouteShape shape1 = new GtfsRouteShape("R001", 1, List.of(), List.of(stop));

        GtfsRoute route = new GtfsRoute("R001", "9400", "Test");
        GtfsDataset dataset = new GtfsDataset(null, Instant.now(),
                List.of(route), List.of(stop), List.of(shape0, shape1));
        Neighborhood centro = new Neighborhood(BoundaryFixtures.CENTRO_NAME,
                BoundaryFixtures.CENTRO_GEOJSON);

        GtfsDataset result = classifier.classify(dataset, List.of(centro));

        // Only one PASSES_THROUGH for (R001, Centro) — deduped
        assertThat(result.lineRelations()).hasSize(1);
        assertThat(result.lineRelations().get(0))
                .isEqualTo(new LineNeighborhoodRelation(
                        "R001", BoundaryFixtures.CENTRO_NAME, new LineRelation.PassesThrough()));
    }

    // ── 3.3g Output completeness ─────────────────────────────────────────────────

    @Test
    void classifiedDatasetContainsOriginalFieldsUnchanged() {
        GtfsStop stop = stopAt(-19.9167, -43.9345);
        GtfsRoute route = new GtfsRoute("R001", "9400", "Test");
        GtfsRouteShape shape = new GtfsRouteShape("R001", 0, List.of(), List.of(stop));
        GtfsDataset original = new GtfsDataset("etag123", Instant.now(),
                List.of(route), List.of(stop), List.of(shape));
        Neighborhood centro = new Neighborhood(BoundaryFixtures.CENTRO_NAME,
                BoundaryFixtures.CENTRO_GEOJSON);

        GtfsDataset result = classifier.classify(original, List.of(centro));

        assertThat(result.feedEtag()).isEqualTo("etag123");
        assertThat(result.routes()).isEqualTo(original.routes());
        assertThat(result.stops()).isEqualTo(original.stops());
        assertThat(result.routeShapes()).isEqualTo(original.routeShapes());
        assertThat(result.neighborhoods()).hasSize(1);
        assertThat(result.neighborhoods().get(0).name())
                .isEqualTo(BoundaryFixtures.CENTRO_NAME);
    }

    @Test
    void emptyDatasetWithNeighborhoodsProducesNoRelations() {
        GtfsDataset empty = new GtfsDataset(null, Instant.now(),
                List.of(), List.of(), List.of());
        List<Neighborhood> neighborhoods = BoundaryFixtures.threeNeighborhoods();

        GtfsDataset result = classifier.classify(empty, neighborhoods);

        assertThat(result.lineRelations()).isEmpty();
        assertThat(result.neighborhoods()).hasSize(3);
    }

    @Test
    void classifyWithEmptyNeighborhoodListProducesNoRelations() {
        GtfsStop stop = stopAt(-19.9167, -43.9345);
        GtfsRouteShape shape = shapeWithStop("R001", -19.9167, -43.9345);
        GtfsDataset dataset = datasetWith(shape);

        GtfsDataset result = classifier.classify(dataset, List.of());

        assertThat(result.lineRelations()).isEmpty();
        assertThat(result.neighborhoods()).isEmpty();
    }

    // ── 3.3h buildGeometry — package-private tested directly ────────────────────

    @Test
    void buildGeometryFromPolygonGeoJson() {
        org.locationtech.jts.geom.Geometry geom =
                classifier.buildGeometry(BoundaryFixtures.CENTRO_GEOJSON);

        assertThat(geom).isNotNull();
        assertThat(geom.getGeometryType()).isEqualTo("Polygon");
    }

    @Test
    void buildGeometryFromMultiPolygonGeoJson() {
        // A valid MultiPolygon with a single ring (same shape as the Centro polygon)
        String validMulti = "{\"type\":\"MultiPolygon\",\"coordinates\":[[[" +
                "[-43.9445,-19.9267]," +
                "[-43.9245,-19.9267]," +
                "[-43.9245,-19.9067]," +
                "[-43.9445,-19.9067]," +
                "[-43.9445,-19.9267]" +
                "]]]}";

        org.locationtech.jts.geom.Geometry geom = classifier.buildGeometry(validMulti);

        assertThat(geom).isNotNull();
        assertThat(geom.getGeometryType()).isEqualTo("MultiPolygon");
    }
}
