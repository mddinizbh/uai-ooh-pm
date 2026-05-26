package com.uai.buslines.adapter.out.persistence;

import jakarta.persistence.*;

/**
 * JPA entity for the {@code line_neighborhood} table.
 *
 * <p>Precomputed classification linking a bus line to a neighbourhood.
 * Composite PK: {@code (line_id, neighborhood_id, relation)}.
 * Populated at GTFS import time by the JTS classification step (ADR-003).
 * No {@code tenant_id} (ADR-004).
 *
 * <p>Uses {@code @IdClass} with {@link LineNeighborhoodId} for the composite PK.
 */
@Entity
@Table(name = "line_neighborhood")
@IdClass(LineNeighborhoodId.class)
class LineNeighborhoodEntity {

    @Id
    @Column(name = "line_id")
    private Long lineId;

    @Id
    @Column(name = "neighborhood_id")
    private Long neighborhoodId;

    @Id
    @Column(name = "relation", length = 50)
    private String relation;

    @Column(name = "dataset_version", nullable = false)
    private Long datasetVersion;

    /** JPA no-arg constructor. */
    protected LineNeighborhoodEntity() {}

    LineNeighborhoodEntity(Long lineId, Long neighborhoodId, String relation, Long datasetVersion) {
        this.lineId = lineId;
        this.neighborhoodId = neighborhoodId;
        this.relation = relation;
        this.datasetVersion = datasetVersion;
    }

    Long getLineId() { return lineId; }
    Long getNeighborhoodId() { return neighborhoodId; }
    String getRelation() { return relation; }
    Long getDatasetVersion() { return datasetVersion; }
}
