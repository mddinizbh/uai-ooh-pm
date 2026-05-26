package com.uai.buslines.application;

import com.uai.buslines.GtfsFixtures;
import com.uai.buslines.domain.model.Coordinate;
import com.uai.buslines.domain.model.GtfsDataset;
import com.uai.buslines.domain.model.GtfsRouteShape;
import com.uai.buslines.domain.model.GtfsStop;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test — end-to-end parse of a canned GTFS ZIP fixture.
 *
 * <p>No Spring context, no database — just verifies that {@link GtfsParser}
 * produces a fully populated {@link GtfsDataset} from the standard test fixture.
 *
 * <p>Named {@code *IT} so Failsafe picks it up in the {@code integration-test} phase
 * and its coverage is included in the JaCoCo report.
 */
class GtfsParserIT {

    @Test
    void endToEndParseProducesPopulatedDataset() {
        GtfsParser parser = new GtfsParser();
        byte[] zip = GtfsFixtures.minimalGtfsZip();

        GtfsDataset dataset = parser.parse(zip, "\"it-etag\"");

        // ── Routes ────────────────────────────────────────────────────────────
        assertThat(dataset.routes()).hasSize(2);
        assertThat(dataset.routes())
                .extracting(r -> r.routeId())
                .containsExactlyInAnyOrder("R001", "R002");

        assertThat(dataset.routes())
                .extracting(r -> r.shortName())
                .containsExactlyInAnyOrder("9400", "2010");

        // ── Stops ─────────────────────────────────────────────────────────────
        assertThat(dataset.stops()).hasSize(3);
        assertThat(dataset.stops())
                .extracting(GtfsStop::stopId)
                .containsExactlyInAnyOrder("ST001", "ST002", "ST003");

        // All stop coordinates must be valid WGS84
        for (GtfsStop stop : dataset.stops()) {
            assertThat(stop.lat()).isBetween(-90.0, 90.0);
            assertThat(stop.lon()).isBetween(-180.0, 180.0);
        }

        // ── Route shapes ──────────────────────────────────────────────────────
        // R001 dir0, R001 dir1, R002 dir0
        assertThat(dataset.routeShapes()).hasSize(3);

        // R001 direction 0 (S1: 3 points, stops ST001→ST002)
        Optional<GtfsRouteShape> r001Dir0 = dataset.routeShapes().stream()
                .filter(s -> "R001".equals(s.routeId()) && s.direction() == 0)
                .findFirst();
        assertThat(r001Dir0).isPresent();
        GtfsRouteShape shape0 = r001Dir0.get();
        assertThat(shape0.shapePoints()).hasSize(3);
        assertThat(shape0.orderedStops()).hasSize(2);
        assertThat(shape0.orderedStops().get(0).stopId()).isEqualTo("ST001");
        assertThat(shape0.orderedStops().get(1).stopId()).isEqualTo("ST002");

        // Shape points are valid WGS84
        for (Coordinate pt : shape0.shapePoints()) {
            assertThat(pt.lat()).isBetween(-90.0, 90.0);
            assertThat(pt.lon()).isBetween(-180.0, 180.0);
        }

        // R001 direction 1 (S2: 3 points, stops ST002→ST001)
        Optional<GtfsRouteShape> r001Dir1 = dataset.routeShapes().stream()
                .filter(s -> "R001".equals(s.routeId()) && s.direction() == 1)
                .findFirst();
        assertThat(r001Dir1).isPresent();
        assertThat(r001Dir1.get().shapePoints()).hasSize(3);
        assertThat(r001Dir1.get().orderedStops().get(0).stopId()).isEqualTo("ST002");

        // R002 direction 0 (S3: 2 points, stops ST001→ST003)
        Optional<GtfsRouteShape> r002Dir0 = dataset.routeShapes().stream()
                .filter(s -> "R002".equals(s.routeId()) && s.direction() == 0)
                .findFirst();
        assertThat(r002Dir0).isPresent();
        assertThat(r002Dir0.get().shapePoints()).hasSize(2);
        assertThat(r002Dir0.get().orderedStops()).hasSize(2);

        // ── Metadata ──────────────────────────────────────────────────────────
        assertThat(dataset.feedEtag()).isEqualTo("\"it-etag\"");
        assertThat(dataset.fetchedAt()).isNotNull();
    }
}
