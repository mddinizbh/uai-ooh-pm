package com.uai.buslines.application;

import com.uai.buslines.GtfsFixtures;
import com.uai.buslines.domain.model.GtfsDataset;
import com.uai.buslines.domain.model.GtfsParseException;
import com.uai.buslines.domain.model.GtfsRouteShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GtfsParser}.
 *
 * <p>No Spring context needed — GtfsParser is a plain object.
 * Uses in-memory ZIP fixtures from {@link GtfsFixtures}.
 */
class GtfsParserTest {

    private GtfsParser parser;

    @BeforeEach
    void setUp() {
        parser = new GtfsParser();
    }

    // ── 2.1 Minimal valid parse ─────────────────────────────────────────────────

    @Test
    void parsesMinimalGtfsFixtureIntoExpectedCounts() {
        GtfsDataset dataset = parser.parse(GtfsFixtures.minimalGtfsZip(), "\"v1\"");

        assertThat(dataset.routes()).hasSize(2);
        assertThat(dataset.stops()).hasSize(3);
        // R001 dir0, R001 dir1, R002 dir0 = 3 shapes
        assertThat(dataset.routeShapes()).hasSize(3);
        assertThat(dataset.feedEtag()).isEqualTo("\"v1\"");
        assertThat(dataset.fetchedAt()).isNotNull();
    }

    @Test
    void nullEtagIsPreservedInDataset() {
        GtfsDataset dataset = parser.parse(GtfsFixtures.minimalGtfsZip(), null);
        assertThat(dataset.feedEtag()).isNull();
    }

    // ── 2.2 Two directions yield two route LineStrings ──────────────────────────

    @Test
    void lineWithTwoDirectionsYieldsTwoRouteShapes() {
        GtfsDataset dataset = parser.parse(GtfsFixtures.minimalGtfsZip(), null);

        List<GtfsRouteShape> r001Shapes = dataset.routeShapes().stream()
                .filter(s -> "R001".equals(s.routeId()))
                .toList();

        assertThat(r001Shapes).hasSize(2);

        Set<Integer> directions = r001Shapes.stream()
                .map(GtfsRouteShape::direction)
                .collect(Collectors.toSet());
        assertThat(directions).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void lineWithOneDirectionYieldsOneRouteShape() {
        GtfsDataset dataset = parser.parse(GtfsFixtures.minimalGtfsZip(), null);

        List<GtfsRouteShape> r002Shapes = dataset.routeShapes().stream()
                .filter(s -> "R002".equals(s.routeId()))
                .toList();

        assertThat(r002Shapes).hasSize(1);
        assertThat(r002Shapes.get(0).direction()).isZero();
    }

    // ── 2.3 Route shapes have correct stop ordering ─────────────────────────────

    @Test
    void directionZeroShapeHasCorrectStopOrder() {
        GtfsDataset dataset = parser.parse(GtfsFixtures.minimalGtfsZip(), null);

        GtfsRouteShape r001Dir0 = dataset.routeShapes().stream()
                .filter(s -> "R001".equals(s.routeId()) && s.direction() == 0)
                .findFirst()
                .orElseThrow();

        // T001: ST001 (seq=1) → ST002 (seq=2)
        assertThat(r001Dir0.orderedStops()).hasSize(2);
        assertThat(r001Dir0.orderedStops().get(0).stopId()).isEqualTo("ST001");
        assertThat(r001Dir0.orderedStops().get(1).stopId()).isEqualTo("ST002");
    }

    @Test
    void shapePointsAreOrderedBySequence() {
        GtfsDataset dataset = parser.parse(GtfsFixtures.minimalGtfsZip(), null);

        GtfsRouteShape r001Dir0 = dataset.routeShapes().stream()
                .filter(s -> "R001".equals(s.routeId()) && s.direction() == 0)
                .findFirst()
                .orElseThrow();

        // S1 has 3 points; first point lat is -19.9167 (Centro)
        assertThat(r001Dir0.shapePoints()).hasSize(3);
        assertThat(r001Dir0.shapePoints().get(0).lat()).isEqualTo(-19.9167);
    }

    // ── 2.4 Missing required column ─────────────────────────────────────────────

    @Test
    void missingRouteIdColumnThrowsDescriptiveError() {
        String badRoutes = "route_short_name,route_long_name,route_type\n9400,Test Line,3\n";
        byte[] zip = GtfsFixtures.zipWithOverride("routes.txt", badRoutes);

        assertThatThrownBy(() -> parser.parse(zip, null))
                .isInstanceOf(GtfsParseException.class)
                .hasMessageContaining("route_id")
                .hasMessageContaining("routes.txt");
    }

    @Test
    void missingStopIdColumnThrowsDescriptiveError() {
        String badStops = "stop_name,stop_lat,stop_lon\nCentro,-19.9,-43.9\n";
        byte[] zip = GtfsFixtures.zipWithOverride("stops.txt", badStops);

        assertThatThrownBy(() -> parser.parse(zip, null))
                .isInstanceOf(GtfsParseException.class)
                .hasMessageContaining("stop_id")
                .hasMessageContaining("stops.txt");
    }

    @Test
    void missingRequiredFileThrowsDescriptiveError() {
        byte[] zip = GtfsFixtures.zipWithoutFile("shapes.txt");

        assertThatThrownBy(() -> parser.parse(zip, null))
                .isInstanceOf(GtfsParseException.class)
                .hasMessageContaining("shapes.txt");
    }

    // ── 2.5 WGS84 coordinate validation ─────────────────────────────────────────

    @Test
    void latitudeAbove90FailsCrsValidation() {
        String badStops = "stop_id,stop_name,stop_lat,stop_lon\nST1,Test Stop,91.0,-43.0\n";
        byte[] zip = GtfsFixtures.zipWithOverride("stops.txt", badStops);

        assertThatThrownBy(() -> parser.parse(zip, null))
                .isInstanceOf(GtfsParseException.class)
                .hasMessageContaining("91.0")
                .hasMessageContaining("WGS84");
    }

    @Test
    void latitudeBelowMinus90FailsCrsValidation() {
        String badStops = "stop_id,stop_name,stop_lat,stop_lon\nST1,Test Stop,-91.0,-43.0\n";
        byte[] zip = GtfsFixtures.zipWithOverride("stops.txt", badStops);

        assertThatThrownBy(() -> parser.parse(zip, null))
                .isInstanceOf(GtfsParseException.class)
                .hasMessageContaining("-91.0");
    }

    @Test
    void longitudeAbove180FailsCrsValidation() {
        String badStops = "stop_id,stop_name,stop_lat,stop_lon\nST1,Test Stop,-19.9,181.0\n";
        byte[] zip = GtfsFixtures.zipWithOverride("stops.txt", badStops);

        assertThatThrownBy(() -> parser.parse(zip, null))
                .isInstanceOf(GtfsParseException.class)
                .hasMessageContaining("181.0")
                .hasMessageContaining("WGS84");
    }

    @Test
    void shapeCoordinateOutsideWgs84FailsValidation() {
        String badShapes =
                "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\n" +
                "S_BAD,200.0,-43.0,1\n";
        byte[] zip = GtfsFixtures.zipWithOverride("shapes.txt", badShapes);

        assertThatThrownBy(() -> parser.parse(zip, null))
                .isInstanceOf(GtfsParseException.class)
                .hasMessageContaining("200.0");
    }

    // ── 2.6 CSV edge cases ──────────────────────────────────────────────────────

    @Test
    void parseCsvLineHandlesQuotedFieldsWithCommas() {
        List<String> fields = GtfsParser.parseCsvLine("\"Centro, BH\",3,route");
        assertThat(fields).containsExactly("Centro, BH", "3", "route");
    }

    @Test
    void parseCsvLineHandlesEscapedQuotes() {
        List<String> fields = GtfsParser.parseCsvLine("\"He said \"\"hello\"\"\",123");
        assertThat(fields).containsExactly("He said \"hello\"", "123");
    }

    // ── 2.7 Empty / corrupted input ─────────────────────────────────────────────

    @Test
    void corruptedZipThrowsParseException() {
        // A byte sequence that is not a valid ZIP causes ZipInputStream to yield no
        // entries; the parser detects the missing required files and throws.
        byte[] notAZip = "not a zip file".getBytes();

        assertThatThrownBy(() -> parser.parse(notAZip, null))
                .isInstanceOf(GtfsParseException.class);
    }
}
