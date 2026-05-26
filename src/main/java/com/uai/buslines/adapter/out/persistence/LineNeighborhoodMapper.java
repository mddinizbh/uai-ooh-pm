package com.uai.buslines.adapter.out.persistence;

import com.uai.buslines.domain.model.LineNeighborhoodRelation;
import com.uai.buslines.domain.model.LineRelation;

/**
 * Mapper between {@link LineNeighborhoodRelation} (domain) and
 * {@link LineNeighborhoodEntity} (persistence).
 *
 * <p>The domain model uses {@code routeId} (GTFS string key) and
 * {@code neighborhoodName} (string), while the entity uses DB PKs
 * ({@code lineId}, {@code neighborhoodId}). The caller is responsible for
 * resolving these mappings before calling {@link #toEntity}.
 *
 * <p>{@link LineRelation} is a sealed type (uAI RULE-JAVA-02). Switch expressions
 * over it must be exhaustive — no {@code default} branch.
 */
final class LineNeighborhoodMapper {

    private LineNeighborhoodMapper() {}

    // ── Domain → Entity ────────────────────────────────────────────────────────

    static LineNeighborhoodEntity toEntity(
            LineNeighborhoodRelation relation,
            long lineId,
            long neighborhoodId,
            long datasetVersion) {
        return new LineNeighborhoodEntity(
                lineId,
                neighborhoodId,
                toDbRelation(relation.relation()),
                datasetVersion);
    }

    // ── Entity → Domain ────────────────────────────────────────────────────────

    static LineNeighborhoodRelation toDomain(
            LineNeighborhoodEntity entity,
            String routeId,
            String neighborhoodName) {
        return new LineNeighborhoodRelation(
                routeId,
                neighborhoodName,
                fromDbRelation(entity.getRelation()));
    }

    // ── Relation conversions ───────────────────────────────────────────────────

    /**
     * Converts a {@link LineRelation} sealed instance to its DB column value.
     * uAI RULE-JAVA-02: no {@code default} branch — exhaustiveness enforced by compiler.
     */
    static String toDbRelation(LineRelation relation) {
        return switch (relation) {
            case LineRelation.PassesThrough ignored -> "PASSES_THROUGH";
            case LineRelation.DepartsFrom ignored   -> "DEPARTS_FROM";
            case LineRelation.ArrivesAt ignored     -> "ARRIVES_AT";
        };
    }

    /**
     * Converts a DB column value to a {@link LineRelation} sealed instance.
     * Switching on a {@code String}, not a sealed type, so {@code default} is required.
     */
    static LineRelation fromDbRelation(String relation) {
        return switch (relation) {
            case "PASSES_THROUGH" -> new LineRelation.PassesThrough();
            case "DEPARTS_FROM"   -> new LineRelation.DepartsFrom();
            case "ARRIVES_AT"     -> new LineRelation.ArrivesAt();
            default -> throw new IllegalArgumentException(
                    "Unknown line_neighborhood.relation value: '" + relation + "'");
        };
    }
}
