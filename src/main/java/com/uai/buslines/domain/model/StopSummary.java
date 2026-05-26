package com.uai.buslines.domain.model;

/**
 * Lightweight stop summary used in line detail responses.
 *
 * <p>No framework dependencies — pure domain value object (RULE-JAVA-01).
 *
 * @param id    database primary key of the {@code stop} row
 * @param name  human-readable stop name
 * @param lat   WGS84 latitude
 * @param lon   WGS84 longitude
 */
public record StopSummary(
        long id,
        String name,
        double lat,
        double lon
) {}
