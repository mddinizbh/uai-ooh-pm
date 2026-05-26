package com.uai.buslines.domain.model;

/**
 * A neighbourhood with its boundary polygon.
 *
 * <p>The boundary is stored as a GeoJSON {@code Polygon} or {@code MultiPolygon}
 * string (WGS84 / EPSG:4326) — no PostGIS type; persisted as {@code jsonb} in the
 * {@code neighborhood.boundary_geojson} column (ADR-003).
 *
 * <p>Produced by the boundary loader ({@code GeoJsonBoundaryParser}) and enriched
 * into {@link GtfsDataset} by {@code NeighborhoodClassifier} (task_03).
 * Persisted by task_04.
 *
 * <p>No framework dependencies — pure domain value object (RULE-JAVA-01).
 *
 * @param name            official PBH neighbourhood name, e.g. {@code "Savassi"}
 * @param boundaryGeoJson GeoJSON geometry string for the polygon boundary (WGS84)
 */
public record Neighborhood(String name, String boundaryGeoJson) {}
