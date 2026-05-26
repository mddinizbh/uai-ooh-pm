package com.uai.buslines.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link NeighborhoodEntity}.
 */
interface NeighborhoodJpaRepository extends JpaRepository<NeighborhoodEntity, Long> {

    /** Returns all neighbourhoods tagged with the given dataset version. */
    List<NeighborhoodEntity> findByDatasetVersion(long datasetVersion);

    /** Returns all neighbourhoods for the given version, sorted alphabetically by name. */
    List<NeighborhoodEntity> findByDatasetVersionOrderByNameAsc(long datasetVersion);

    /** Returns the neighbourhood with the given id and version, or empty if not found. */
    Optional<NeighborhoodEntity> findByIdAndDatasetVersion(long id, long datasetVersion);
}
