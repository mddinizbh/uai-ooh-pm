package com.uai.buslines.domain.model;

import java.util.List;

/**
 * Lines serving a neighbourhood, grouped into the three spatial-relation buckets.
 *
 * <p>A single line may appear in more than one bucket — for example, a bidirectional
 * route may both depart from and arrive at the same neighbourhood.
 *
 * <p>No framework dependencies — pure domain value object (RULE-JAVA-01).
 *
 * @param neighborhoodId    database primary key of the neighbourhood
 * @param neighborhoodName  official PBH neighbourhood name
 * @param boundaryGeoJson   GeoJSON geometry string for the neighbourhood boundary (for map highlight)
 * @param passesThrough     lines that pass through this neighbourhood (neither start nor end)
 * @param departsFrom       lines that originate / depart from this neighbourhood
 * @param arrivesAt         lines that terminate / arrive at this neighbourhood
 */
public record NeighborhoodLines(
        long neighborhoodId,
        String neighborhoodName,
        String boundaryGeoJson,
        List<LineSummary> passesThrough,
        List<LineSummary> departsFrom,
        List<LineSummary> arrivesAt
) {}
