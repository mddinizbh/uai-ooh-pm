package com.uai.buslines.adapter.out.persistence;

import com.uai.buslines.domain.model.LineNeighborhoodRelation;
import com.uai.buslines.domain.model.LineRelation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit test — verifies that {@link LineNeighborhoodMapper} correctly converts
 * all three {@link LineRelation} sealed variants to/from their DB string representations,
 * and round-trips a {@link LineNeighborhoodRelation} without data loss.
 */
class LineNeighborhoodMapperTest {

    // ── toDbRelation ──────────────────────────────────────────────────────────

    @Test
    void toDbRelation_passesThrough_encodedCorrectly() {
        assertThat(LineNeighborhoodMapper.toDbRelation(new LineRelation.PassesThrough()))
                .isEqualTo("PASSES_THROUGH");
    }

    @Test
    void toDbRelation_departsFrom_encodedCorrectly() {
        assertThat(LineNeighborhoodMapper.toDbRelation(new LineRelation.DepartsFrom()))
                .isEqualTo("DEPARTS_FROM");
    }

    @Test
    void toDbRelation_arrivesAt_encodedCorrectly() {
        assertThat(LineNeighborhoodMapper.toDbRelation(new LineRelation.ArrivesAt()))
                .isEqualTo("ARRIVES_AT");
    }

    // ── fromDbRelation ────────────────────────────────────────────────────────

    @Test
    void fromDbRelation_roundTripsAllThreeValues() {
        assertThat(LineNeighborhoodMapper.fromDbRelation("PASSES_THROUGH"))
                .isInstanceOf(LineRelation.PassesThrough.class);
        assertThat(LineNeighborhoodMapper.fromDbRelation("DEPARTS_FROM"))
                .isInstanceOf(LineRelation.DepartsFrom.class);
        assertThat(LineNeighborhoodMapper.fromDbRelation("ARRIVES_AT"))
                .isInstanceOf(LineRelation.ArrivesAt.class);
    }

    @Test
    void fromDbRelation_throwsForUnknownValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LineNeighborhoodMapper.fromDbRelation("UNKNOWN_VALUE"))
                .withMessageContaining("UNKNOWN_VALUE");
    }

    // ── toEntity ──────────────────────────────────────────────────────────────

    @Test
    void toEntity_setsAllFieldsCorrectly() {
        LineNeighborhoodRelation rel = new LineNeighborhoodRelation(
                "R001", "Centro", new LineRelation.DepartsFrom());

        LineNeighborhoodEntity entity = LineNeighborhoodMapper.toEntity(rel, 10L, 20L, 5L);

        assertThat(entity.getLineId()).isEqualTo(10L);
        assertThat(entity.getNeighborhoodId()).isEqualTo(20L);
        assertThat(entity.getRelation()).isEqualTo("DEPARTS_FROM");
        assertThat(entity.getDatasetVersion()).isEqualTo(5L);
    }

    // ── Round-trip (toEntity + toDomain) ─────────────────────────────────────

    @Test
    void roundTrip_preservesAllThreeRelationTypes() {
        LineRelation[] allRelations = {
                new LineRelation.PassesThrough(),
                new LineRelation.DepartsFrom(),
                new LineRelation.ArrivesAt()
        };

        for (LineRelation relation : allRelations) {
            LineNeighborhoodRelation original =
                    new LineNeighborhoodRelation("R001", "Centro", relation);

            LineNeighborhoodEntity entity =
                    LineNeighborhoodMapper.toEntity(original, 10L, 20L, 5L);
            LineNeighborhoodRelation restored =
                    LineNeighborhoodMapper.toDomain(entity, "R001", "Centro");

            assertThat(restored)
                    .as("round-trip for relation %s", relation)
                    .isEqualTo(original);
        }
    }

    @Test
    void roundTrip_preservesRouteIdAndNeighborhoodName() {
        LineNeighborhoodRelation original = new LineNeighborhoodRelation(
                "ROUTE-SPECIAL-99", "Savassi", new LineRelation.ArrivesAt());

        LineNeighborhoodEntity entity =
                LineNeighborhoodMapper.toEntity(original, 1L, 2L, 3L);
        LineNeighborhoodRelation restored =
                LineNeighborhoodMapper.toDomain(entity, "ROUTE-SPECIAL-99", "Savassi");

        assertThat(restored.routeId()).isEqualTo("ROUTE-SPECIAL-99");
        assertThat(restored.neighborhoodName()).isEqualTo("Savassi");
    }
}
