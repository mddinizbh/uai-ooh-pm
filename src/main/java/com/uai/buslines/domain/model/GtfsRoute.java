package com.uai.buslines.domain.model;

/**
 * A bus route parsed from GTFS {@code routes.txt}.
 *
 * <p>Maps to the {@code line} table (task_04).
 * No framework dependencies — pure domain value object (RULE-JAVA-01).
 *
 * @param routeId   GTFS {@code route_id}
 * @param shortName human-facing line number, e.g. {@code "9400"} (GTFS {@code route_short_name})
 * @param longName  full route description (GTFS {@code route_long_name})
 */
public record GtfsRoute(
        String routeId,
        String shortName,
        String longName
) {}
