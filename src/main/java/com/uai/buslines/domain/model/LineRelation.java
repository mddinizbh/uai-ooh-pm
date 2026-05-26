package com.uai.buslines.domain.model;

/**
 * Sealed type representing how a bus line relates to a neighbourhood.
 *
 * <p>Instances are produced by the JTS classification step at GTFS import time
 * (ADR-003) and stored in the {@code line_neighborhood.relation} column as:
 * {@code PASSES_THROUGH}, {@code DEPARTS_FROM}, or {@code ARRIVES_AT}.
 *
 * <p>uAI RULE-JAVA-02: no {@code default} branch allowed in switch expressions
 * on this sealed type — the compiler enforces exhaustiveness.
 */
public sealed interface LineRelation
        permits LineRelation.PassesThrough,
                LineRelation.DepartsFrom,
                LineRelation.ArrivesAt {

    /** The line passes through the neighbourhood (neither starts nor ends there). */
    record PassesThrough() implements LineRelation {}

    /** The line departs from / originates in the neighbourhood. */
    record DepartsFrom() implements LineRelation {}

    /** The line arrives at / terminates in the neighbourhood. */
    record ArrivesAt() implements LineRelation {}
}
