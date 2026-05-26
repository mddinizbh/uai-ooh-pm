package com.uai.buslines.domain.model;

/**
 * A classified line-neighbourhood relationship produced at GTFS import time.
 *
 * <p>Maps to a row in the {@code line_neighborhood} table (task_04). Multiple
 * relations with different {@link LineRelation} types may exist for the same
 * (routeId, neighbourhoodName) pair — e.g. a bidirectional route may both depart
 * from and arrive at the same neighbourhood.
 *
 * <p>No framework dependencies — pure domain value object (RULE-JAVA-01).
 *
 * @param routeId           GTFS {@code route_id} (links to {@link GtfsRoute})
 * @param neighborhoodName  canonical neighbourhood name (links to {@link Neighborhood})
 * @param relation          the type of spatial relationship (one of the three sealed subtypes)
 */
public record LineNeighborhoodRelation(
        String routeId,
        String neighborhoodName,
        LineRelation relation
) {}
