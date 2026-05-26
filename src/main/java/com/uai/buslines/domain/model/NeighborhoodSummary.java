package com.uai.buslines.domain.model;

/**
 * Lightweight neighbourhood summary used by the list/autocomplete endpoint.
 *
 * <p>Includes per-relation line counts so the SPA can display badge numbers
 * before the user navigates into a neighbourhood.
 *
 * <p>No framework dependencies — pure domain value object (RULE-JAVA-01).
 *
 * @param id                  database primary key
 * @param name                official PBH neighbourhood name
 * @param passesThroughCount  number of lines that pass through this neighbourhood
 * @param departsFromCount    number of lines that depart from this neighbourhood
 * @param arrivesAtCount      number of lines that arrive at this neighbourhood
 */
public record NeighborhoodSummary(
        long id,
        String name,
        int passesThroughCount,
        int departsFromCount,
        int arrivesAtCount
) {}
