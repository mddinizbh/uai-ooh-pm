package com.uai.buslines.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.BoundaryFixtures;
import com.uai.buslines.GtfsFixtures;
import com.uai.buslines.domain.model.GtfsDataset;
import com.uai.buslines.domain.model.LineNeighborhoodRelation;
import com.uai.buslines.domain.model.LineRelation;
import com.uai.buslines.domain.model.Neighborhood;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test — classifies the complete fixture GTFS network against
 * the three fixture neighbourhood polygons and asserts the expected 10-relation set.
 *
 * <p>No Spring context or database needed: this test exercises the pure
 * application-layer pipeline ({@link GtfsParser} → {@link NeighborhoodClassifier})
 * with in-memory fixtures. Named {@code *IT} to run under Failsafe.
 *
 * <h3>Expected 10 relations (hand-verified)</h3>
 * <pre>
 * Route shapes in the fixture:
 *   R001 dir0 (S1): first=(-19.9167,-43.9345)=Centro, last=(-19.8800,-43.9100)=BairroA
 *                   stops: ST001(Centro), ST002(BairroA)
 *   R001 dir1 (S2): first=(-19.8800,-43.9100)=BairroA, last=(-19.9167,-43.9345)=Centro
 *                   stops: ST002(BairroA), ST001(Centro)
 *   R002 dir0 (S3): first=(-19.9167,-43.9345)=Centro, last=(-19.9500,-44.0000)=BairroB
 *                   stops: ST001(Centro), ST003(BairroB)
 *
 * Resulting relations (after dedup across directions):
 *   1.  (R001, Centro,   DepartsFrom)    — S1 first point in Centro
 *   2.  (R001, Bairro A, ArrivesAt)      — S1 last  point in Bairro A
 *   3.  (R001, Centro,   PassesThrough)  — ST001 in Centro (S1 or S2, deduped)
 *   4.  (R001, Bairro A, PassesThrough)  — ST002 in Bairro A (S1 or S2, deduped)
 *   5.  (R001, Bairro A, DepartsFrom)    — S2 first point in Bairro A
 *   6.  (R001, Centro,   ArrivesAt)      — S2 last  point in Centro
 *   7.  (R002, Centro,   DepartsFrom)    — S3 first point in Centro
 *   8.  (R002, Bairro B, ArrivesAt)      — S3 last  point in Bairro B
 *   9.  (R002, Centro,   PassesThrough)  — ST001 in Centro
 *   10. (R002, Bairro B, PassesThrough)  — ST003 in Bairro B
 * </pre>
 */
class NeighborhoodClassifierIT {

    private GtfsParser parser;
    private NeighborhoodClassifier classifier;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        parser     = new GtfsParser();
        classifier = new NeighborhoodClassifier(objectMapper);
    }

    // ── Main integration assertion ───────────────────────────────────────────────

    @Test
    void fixtureNetworkProducesExpectedTenRelations() {
        GtfsDataset parsed = parser.parse(GtfsFixtures.minimalGtfsZip(), "\"v1\"");
        List<Neighborhood> neighborhoods = BoundaryFixtures.threeNeighborhoods();

        GtfsDataset classified = classifier.classify(parsed, neighborhoods);

        assertThat(classified.lineRelations()).hasSize(10);

        // -- Centro relations -------------------------------------------------
        assertContainsRelation(classified.lineRelations(),
                "R001", BoundaryFixtures.CENTRO_NAME, new LineRelation.DepartsFrom());
        assertContainsRelation(classified.lineRelations(),
                "R001", BoundaryFixtures.CENTRO_NAME, new LineRelation.ArrivesAt());
        assertContainsRelation(classified.lineRelations(),
                "R001", BoundaryFixtures.CENTRO_NAME, new LineRelation.PassesThrough());
        assertContainsRelation(classified.lineRelations(),
                "R002", BoundaryFixtures.CENTRO_NAME, new LineRelation.DepartsFrom());
        assertContainsRelation(classified.lineRelations(),
                "R002", BoundaryFixtures.CENTRO_NAME, new LineRelation.PassesThrough());

        // -- Bairro A relations ------------------------------------------------
        assertContainsRelation(classified.lineRelations(),
                "R001", BoundaryFixtures.BAIRRO_A_NAME, new LineRelation.DepartsFrom());
        assertContainsRelation(classified.lineRelations(),
                "R001", BoundaryFixtures.BAIRRO_A_NAME, new LineRelation.ArrivesAt());
        assertContainsRelation(classified.lineRelations(),
                "R001", BoundaryFixtures.BAIRRO_A_NAME, new LineRelation.PassesThrough());

        // -- Bairro B relations ------------------------------------------------
        assertContainsRelation(classified.lineRelations(),
                "R002", BoundaryFixtures.BAIRRO_B_NAME, new LineRelation.ArrivesAt());
        assertContainsRelation(classified.lineRelations(),
                "R002", BoundaryFixtures.BAIRRO_B_NAME, new LineRelation.PassesThrough());
    }

    @Test
    void fixtureNetworkNeighborhoodsArePreservedInResult() {
        GtfsDataset parsed = parser.parse(GtfsFixtures.minimalGtfsZip(), null);
        List<Neighborhood> neighborhoods = BoundaryFixtures.threeNeighborhoods();

        GtfsDataset classified = classifier.classify(parsed, neighborhoods);

        assertThat(classified.neighborhoods()).hasSize(3);
        assertThat(classified.neighborhoods())
                .extracting(Neighborhood::name)
                .containsExactlyInAnyOrder(
                        BoundaryFixtures.CENTRO_NAME,
                        BoundaryFixtures.BAIRRO_A_NAME,
                        BoundaryFixtures.BAIRRO_B_NAME);
    }

    @Test
    void fixtureNetworkOriginalGtfsDataIsPreserved() {
        GtfsDataset parsed = parser.parse(GtfsFixtures.minimalGtfsZip(), "\"v2\"");
        List<Neighborhood> neighborhoods = BoundaryFixtures.threeNeighborhoods();

        GtfsDataset classified = classifier.classify(parsed, neighborhoods);

        // Original GTFS data must be unchanged
        assertThat(classified.feedEtag()).isEqualTo("\"v2\"");
        assertThat(classified.routes()).hasSize(2);
        assertThat(classified.stops()).hasSize(3);
        assertThat(classified.routeShapes()).hasSize(3);
    }

    @Test
    void r002HasNoRelationsToBairroA() {
        GtfsDataset parsed = parser.parse(GtfsFixtures.minimalGtfsZip(), null);
        List<Neighborhood> neighborhoods = BoundaryFixtures.threeNeighborhoods();

        GtfsDataset classified = classifier.classify(parsed, neighborhoods);

        long r002BairroARelations = classified.lineRelations().stream()
                .filter(r -> "R002".equals(r.routeId())
                          && BoundaryFixtures.BAIRRO_A_NAME.equals(r.neighborhoodName()))
                .count();

        assertThat(r002BairroARelations).isZero();
    }

    // ── Helper ───────────────────────────────────────────────────────────────────

    private static void assertContainsRelation(
            List<LineNeighborhoodRelation> relations,
            String routeId, String neighborhoodName, LineRelation relation) {
        assertThat(relations)
                .as("Expected relation (%s, %s, %s) to be present",
                        routeId, neighborhoodName, relation)
                .contains(new LineNeighborhoodRelation(routeId, neighborhoodName, relation));
    }
}
