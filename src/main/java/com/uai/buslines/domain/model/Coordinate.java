package com.uai.buslines.domain.model;

/**
 * An immutable WGS84 (EPSG:4326) coordinate pair.
 *
 * <p>Used for bus-stop locations and route-shape points.
 * Validation (lat ∈ [-90,90], lon ∈ [-180,180]) is enforced by the caller
 * ({@link com.uai.buslines.application.GtfsParser}) before constructing an instance.
 * No framework dependencies — pure domain value object (RULE-JAVA-01).
 */
public record Coordinate(double lat, double lon) {}
