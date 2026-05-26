package com.uai.buslines.domain.model;

/**
 * A bus stop parsed from GTFS {@code stops.txt}.
 *
 * <p>Coordinates are guaranteed WGS84 (EPSG:4326) — validated by
 * {@link com.uai.buslines.application.GtfsParser} before construction.
 * Maps to the {@code stop} table (task_04).
 * No framework dependencies — pure domain value object (RULE-JAVA-01).
 *
 * @param stopId GTFS {@code stop_id}
 * @param name   {@code stop_name}
 * @param lat    WGS84 latitude  (−90 … +90)
 * @param lon    WGS84 longitude (−180 … +180)
 */
public record GtfsStop(
        String stopId,
        String name,
        double lat,
        double lon
) {}
