package com.uai.buslines.domain.model;

import java.util.List;

/**
 * The geometric path and ordered stops for a single bus-route direction.
 *
 * <p>Assembled from GTFS {@code shapes.txt}, {@code trips.txt}, and
 * {@code stop_times.txt} by {@link com.uai.buslines.application.GtfsParser}.
 * Each distinct {@code (routeId, direction)} pair produces one instance.
 *
 * <p>Maps to the {@code line_shape} table and the ordered stops for task_04.
 * No framework dependencies — pure domain value object (RULE-JAVA-01).
 *
 * @param routeId      GTFS {@code route_id}
 * @param direction    0 = outbound, 1 = inbound (GTFS {@code direction_id})
 * @param shapePoints  ordered path coordinates from {@code shapes.txt} (WGS84)
 * @param orderedStops stops in travel order from {@code stop_times.txt}
 */
public record GtfsRouteShape(
        String routeId,
        int direction,
        List<Coordinate> shapePoints,
        List<GtfsStop> orderedStops
) {}
