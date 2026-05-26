package com.uai.buslines.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link StopEntity}.
 */
interface StopJpaRepository extends JpaRepository<StopEntity, Long> {

    /** Returns all stops tagged with the given dataset version. */
    List<StopEntity> findByDatasetVersion(long datasetVersion);
}
