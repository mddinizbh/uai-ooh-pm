package com.uai.buslines.domain.model;

import java.util.List;

/**
 * Full detail for a bus line: summary info, route shapes, and ordered stops.
 *
 * <p>Shapes carry the GeoJSON geometry for each direction (0 = outbound, 1 = inbound).
 * Stops are empty in the current implementation — per-line stop ordering requires a
 * migration that adds a {@code line_stop} junction table (follow-up task).
 *
 * <p>No framework dependencies — pure domain value object (RULE-JAVA-01).
 *
 * @param line    summary info (id, shortName, longName)
 * @param shapes  route geometries, one per direction; may be empty if shapes were not
 *                available in the GTFS feed
 * @param stops   ordered stops along the route; currently always empty
 *                (stop-per-line persistence is a follow-up task)
 */
public record LineDetail(
        LineSummary line,
        List<LineShape> shapes,
        List<StopSummary> stops
) {}
