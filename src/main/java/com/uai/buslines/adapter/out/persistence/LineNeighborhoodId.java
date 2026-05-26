package com.uai.buslines.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link LineNeighborhoodEntity}.
 *
 * <p>Represents the compound PK {@code (line_id, neighborhood_id, relation)}
 * from the {@code line_neighborhood} table. Used with {@code @IdClass}.
 */
class LineNeighborhoodId implements Serializable {

    private Long lineId;
    private Long neighborhoodId;
    private String relation;

    /** JPA no-arg constructor. */
    protected LineNeighborhoodId() {}

    LineNeighborhoodId(Long lineId, Long neighborhoodId, String relation) {
        this.lineId = lineId;
        this.neighborhoodId = neighborhoodId;
        this.relation = relation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LineNeighborhoodId other)) return false;
        return Objects.equals(lineId, other.lineId)
                && Objects.equals(neighborhoodId, other.neighborhoodId)
                && Objects.equals(relation, other.relation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineId, neighborhoodId, relation);
    }
}
