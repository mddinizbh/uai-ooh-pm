package com.uai.buslines.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LineRelation}.
 *
 * <p>Pure domain type — no Spring context needed.
 * Verifies the three permitted subtypes, equality contract, and toString.
 */
class LineRelationTest {

    // ── Type identity ─────────────────────────────────────────────────────────

    @Test
    void passesThroughIsALineRelation() {
        LineRelation rel = new LineRelation.PassesThrough();
        assertThat(rel).isInstanceOf(LineRelation.PassesThrough.class);
    }

    @Test
    void departsFromIsALineRelation() {
        LineRelation rel = new LineRelation.DepartsFrom();
        assertThat(rel).isInstanceOf(LineRelation.DepartsFrom.class);
    }

    @Test
    void arrivesAtIsALineRelation() {
        LineRelation rel = new LineRelation.ArrivesAt();
        assertThat(rel).isInstanceOf(LineRelation.ArrivesAt.class);
    }

    // ── Equality (record semantics) ───────────────────────────────────────────

    @Test
    void sameTypesAreEqual() {
        assertThat(new LineRelation.PassesThrough()).isEqualTo(new LineRelation.PassesThrough());
        assertThat(new LineRelation.DepartsFrom()).isEqualTo(new LineRelation.DepartsFrom());
        assertThat(new LineRelation.ArrivesAt()).isEqualTo(new LineRelation.ArrivesAt());
    }

    @Test
    void differentTypesAreNotEqual() {
        assertThat(new LineRelation.PassesThrough()).isNotEqualTo(new LineRelation.DepartsFrom());
        assertThat(new LineRelation.DepartsFrom()).isNotEqualTo(new LineRelation.ArrivesAt());
        assertThat(new LineRelation.ArrivesAt()).isNotEqualTo(new LineRelation.PassesThrough());
    }

    // ── HashCode ─────────────────────────────────────────────────────────────

    @Test
    void sameTypesHaveEqualHashCodes() {
        assertThat(new LineRelation.PassesThrough().hashCode())
                .isEqualTo(new LineRelation.PassesThrough().hashCode());
        assertThat(new LineRelation.DepartsFrom().hashCode())
                .isEqualTo(new LineRelation.DepartsFrom().hashCode());
        assertThat(new LineRelation.ArrivesAt().hashCode())
                .isEqualTo(new LineRelation.ArrivesAt().hashCode());
    }

    // ── ToString ─────────────────────────────────────────────────────────────

    @Test
    void toStringContainsTypeName() {
        assertThat(new LineRelation.PassesThrough().toString()).contains("PassesThrough");
        assertThat(new LineRelation.DepartsFrom().toString()).contains("DepartsFrom");
        assertThat(new LineRelation.ArrivesAt().toString()).contains("ArrivesAt");
    }

    // ── Exhaustive switch (RULE-JAVA-02 — no default branch) ─────────────────

    @Test
    void switchExpressionIsExhaustiveWithoutDefault() {
        LineRelation rel = new LineRelation.PassesThrough();

        // This switch must compile without a default branch — proving the sealed
        // type is complete and the compiler enforces exhaustiveness (RULE-JAVA-02).
        String label = switch (rel) {
            case LineRelation.PassesThrough pt -> "passes-through";
            case LineRelation.DepartsFrom df   -> "departs-from";
            case LineRelation.ArrivesAt aa     -> "arrives-at";
        };

        assertThat(label).isEqualTo("passes-through");
    }
}
