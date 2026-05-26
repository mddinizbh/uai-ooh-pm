package com.uai.buslines.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link LineNeighborhoodEntity}.
 */
interface LineNeighborhoodJpaRepository
        extends JpaRepository<LineNeighborhoodEntity, LineNeighborhoodId> {

    /** Returns all line-neighbourhood rows tagged with the given dataset version. */
    List<LineNeighborhoodEntity> findByDatasetVersion(long datasetVersion);

    /**
     * Returns all line-neighbourhood rows for the given neighbourhood and version.
     * Used to build the grouped-lines response for a single neighbourhood.
     */
    List<LineNeighborhoodEntity> findByNeighborhoodIdAndDatasetVersion(
            long neighborhoodId, long datasetVersion);
}
