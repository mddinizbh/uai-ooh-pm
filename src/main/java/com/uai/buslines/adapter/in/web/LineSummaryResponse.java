package com.uai.buslines.adapter.in.web;

import com.uai.buslines.domain.model.LineSummary;

/**
 * Web DTO for a bus-line summary.
 *
 * <p>Used in both search results ({@link LineController}) and neighbourhood line
 * groupings ({@link NeighborhoodController}).
 *
 * <p>Kept in a top-level file so both controllers can reference it without
 * duplication (RULE-JAVA-01 — DTOs separate from domain models).
 */
public record LineSummaryResponse(
        long id,
        String shortName,
        String longName) {

    static LineSummaryResponse from(LineSummary s) {
        return new LineSummaryResponse(s.id(), s.shortName(), s.longName());
    }
}
