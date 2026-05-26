package com.uai.buslines.domain.model;

/**
 * Lightweight bus-line summary used in neighbourhood groupings and search results.
 *
 * <p>No framework dependencies — pure domain value object (RULE-JAVA-01).
 *
 * @param id        database primary key of the {@code line} row
 * @param shortName human-facing line number, e.g. {@code "9400"}
 * @param longName  full route description, e.g. {@code "Bairro A - Centro"}
 */
public record LineSummary(
        long id,
        String shortName,
        String longName
) {}
