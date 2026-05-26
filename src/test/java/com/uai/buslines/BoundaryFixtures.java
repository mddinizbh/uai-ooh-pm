package com.uai.buslines;

import com.uai.buslines.domain.model.Neighborhood;

import java.util.List;

/**
 * Test helper — provides in-memory neighbourhood boundary fixtures for unit and
 * integration tests.
 *
 * <p>The three polygons are sized to contain exactly one of the stops from
 * {@link GtfsFixtures} and no others, enabling deterministic classification
 * assertions in {@code NeighborhoodClassifierTest} and
 * {@code NeighborhoodClassifierIT}.
 *
 * <h3>Stop coordinates (from {@link GtfsFixtures}):</h3>
 * <ul>
 *   <li>ST001 — lat={@code -19.9167}, lon={@code -43.9345} → inside "Centro"</li>
 *   <li>ST002 — lat={@code -19.8800}, lon={@code -43.9100} → inside "Bairro A"</li>
 *   <li>ST003 — lat={@code -19.9500}, lon={@code -44.0000} → inside "Bairro B"</li>
 * </ul>
 *
 * <h3>Shape point endpoints (from {@link GtfsFixtures}):</h3>
 * <ul>
 *   <li>S1 first: {@code (-19.9167, -43.9345)} = Centro (DEPARTS_FROM for R001)</li>
 *   <li>S1 last:  {@code (-19.8800, -43.9100)} = Bairro A (ARRIVES_AT for R001)</li>
 *   <li>S2 first: {@code (-19.8800, -43.9100)} = Bairro A (DEPARTS_FROM for R001 dir1)</li>
 *   <li>S2 last:  {@code (-19.9167, -43.9345)} = Centro (ARRIVES_AT for R001 dir1)</li>
 *   <li>S3 first: {@code (-19.9167, -43.9345)} = Centro (DEPARTS_FROM for R002)</li>
 *   <li>S3 last:  {@code (-19.9500, -44.0000)} = Bairro B (ARRIVES_AT for R002)</li>
 * </ul>
 *
 * <h3>Expected relations (10 total):</h3>
 * <ol>
 *   <li>(R001, Centro,   DepartsFrom)</li>
 *   <li>(R001, Bairro A, ArrivesAt)</li>
 *   <li>(R001, Centro,   PassesThrough)</li>
 *   <li>(R001, Bairro A, PassesThrough)</li>
 *   <li>(R001, Centro,   ArrivesAt)</li>
 *   <li>(R001, Bairro A, DepartsFrom)</li>
 *   <li>(R002, Centro,   DepartsFrom)</li>
 *   <li>(R002, Centro,   PassesThrough)</li>
 *   <li>(R002, Bairro B, ArrivesAt)</li>
 *   <li>(R002, Bairro B, PassesThrough)</li>
 * </ol>
 */
public final class BoundaryFixtures {

    public static final String CENTRO_NAME   = "Centro";
    public static final String BAIRRO_A_NAME = "Bairro A";
    public static final String BAIRRO_B_NAME = "Bairro B";

    private BoundaryFixtures() {}

    // ── GeoJSON strings ─────────────────────────────────────────────────────────

    /**
     * GeoJSON Polygon containing ST001 (lat=-19.9167, lon=-43.9345).
     * Box: lon ∈ [-43.9445, -43.9245], lat ∈ [-19.9267, -19.9067].
     * GeoJSON coordinates are in [longitude, latitude] order.
     */
    public static final String CENTRO_GEOJSON =
            "{\"type\":\"Polygon\",\"coordinates\":[[" +
            "[-43.9445,-19.9267]," +
            "[-43.9245,-19.9267]," +
            "[-43.9245,-19.9067]," +
            "[-43.9445,-19.9067]," +
            "[-43.9445,-19.9267]" +
            "]]}";

    /**
     * GeoJSON Polygon containing ST002 (lat=-19.8800, lon=-43.9100).
     * Box: lon ∈ [-43.9200, -43.9000], lat ∈ [-19.8900, -19.8700].
     */
    public static final String BAIRRO_A_GEOJSON =
            "{\"type\":\"Polygon\",\"coordinates\":[[" +
            "[-43.9200,-19.8900]," +
            "[-43.9000,-19.8900]," +
            "[-43.9000,-19.8700]," +
            "[-43.9200,-19.8700]," +
            "[-43.9200,-19.8900]" +
            "]]}";

    /**
     * GeoJSON Polygon containing ST003 (lat=-19.9500, lon=-44.0000).
     * Box: lon ∈ [-44.0100, -43.9900], lat ∈ [-19.9600, -19.9400].
     */
    public static final String BAIRRO_B_GEOJSON =
            "{\"type\":\"Polygon\",\"coordinates\":[[" +
            "[-44.0100,-19.9600]," +
            "[-43.9900,-19.9600]," +
            "[-43.9900,-19.9400]," +
            "[-44.0100,-19.9400]," +
            "[-44.0100,-19.9600]" +
            "]]}";

    // ── FeatureCollection (for GeoJsonBoundaryParser tests) ─────────────────────

    /**
     * A minimal GeoJSON FeatureCollection with the three fixture neighbourhoods
     * (using the common PBH property name {@code "NOME"}).
     */
    public static final String FEATURE_COLLECTION_JSON =
            "{\n" +
            "  \"type\": \"FeatureCollection\",\n" +
            "  \"features\": [\n" +
            "    {\n" +
            "      \"type\": \"Feature\",\n" +
            "      \"properties\": {\"NOME\": \"Centro\"},\n" +
            "      \"geometry\": " + CENTRO_GEOJSON + "\n" +
            "    },\n" +
            "    {\n" +
            "      \"type\": \"Feature\",\n" +
            "      \"properties\": {\"NOME\": \"Bairro A\"},\n" +
            "      \"geometry\": " + BAIRRO_A_GEOJSON + "\n" +
            "    },\n" +
            "    {\n" +
            "      \"type\": \"Feature\",\n" +
            "      \"properties\": {\"NOME\": \"Bairro B\"},\n" +
            "      \"geometry\": " + BAIRRO_B_GEOJSON + "\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    // ── Neighborhood objects ─────────────────────────────────────────────────────

    /** Returns the three fixture {@link Neighborhood} objects. */
    public static List<Neighborhood> threeNeighborhoods() {
        return List.of(
                new Neighborhood(CENTRO_NAME,   CENTRO_GEOJSON),
                new Neighborhood(BAIRRO_A_NAME, BAIRRO_A_GEOJSON),
                new Neighborhood(BAIRRO_B_NAME, BAIRRO_B_GEOJSON)
        );
    }

    /**
     * A small square polygon near the centre of BH coordinates, useful for
     * testing "no relation" cases (does not contain any fixture stop).
     * Box: lon ∈ [-43.8500, -43.8400], lat ∈ [-19.8600, -19.8500].
     */
    public static final String EMPTY_POLYGON_GEOJSON =
            "{\"type\":\"Polygon\",\"coordinates\":[[" +
            "[-43.8500,-19.8600]," +
            "[-43.8400,-19.8600]," +
            "[-43.8400,-19.8500]," +
            "[-43.8500,-19.8500]," +
            "[-43.8500,-19.8600]" +
            "]]}";

    /**
     * A unit-square polygon centred at (lng=0, lat=0) for pure geometric tests.
     * Exterior ring in GeoJSON [lng, lat] order.
     * Covers the point (lng=0.5, lat=0.5) and also boundary points like (lng=0.5, lat=0.0).
     */
    public static final String UNIT_SQUARE_GEOJSON =
            "{\"type\":\"Polygon\",\"coordinates\":[[" +
            "[0.0,0.0]," +
            "[1.0,0.0]," +
            "[1.0,1.0]," +
            "[0.0,1.0]," +
            "[0.0,0.0]" +
            "]]}";
}
