package com.uai.buslines.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DatasetVersionEntity}.
 *
 * <p>Provides the custom queries needed for the atomic dataset-version swap:
 * <ul>
 *   <li>Finding the current ACTIVE version</li>
 *   <li>Bulk-archiving all other ACTIVE versions after a new one is activated</li>
 * </ul>
 */
interface DatasetVersionJpaRepository extends JpaRepository<DatasetVersionEntity, Long> {

    /**
     * Returns the dataset version with the given status having the highest version number.
     * Using {@code findFirst…OrderByVersionDesc} is safe even if the ACTIVE-at-most-one
     * invariant is temporarily violated.
     */
    Optional<DatasetVersionEntity> findFirstByStatusOrderByVersionDesc(String status);

    /**
     * Updates the status of all versions that currently have {@code currentStatus} to
     * {@code newStatus}, excluding the version identified by {@code excludeVersion}.
     *
     * <p>Used in {@code replaceWith} to archive all previously ACTIVE versions after
     * the new version has been activated.
     */
    @Modifying
    @Query("UPDATE DatasetVersionEntity v SET v.status = :newStatus " +
           "WHERE v.status = :currentStatus AND v.version <> :excludeVersion")
    void updateStatusExcluding(
            @Param("currentStatus") String currentStatus,
            @Param("newStatus") String newStatus,
            @Param("excludeVersion") long excludeVersion);
}
