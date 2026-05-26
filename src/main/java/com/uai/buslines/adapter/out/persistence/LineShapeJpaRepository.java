package com.uai.buslines.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link LineShapeEntity}.
 */
interface LineShapeJpaRepository extends JpaRepository<LineShapeEntity, Long> {

    /** Returns all line shapes tagged with the given dataset version. */
    List<LineShapeEntity> findByDatasetVersion(long datasetVersion);

    /** Returns all shapes for the given line and version, ordered by direction. */
    List<LineShapeEntity> findByLineIdAndDatasetVersionOrderByDirectionAsc(
            long lineId, long datasetVersion);
}
