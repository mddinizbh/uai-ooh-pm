package com.uai.buslines.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link LineEntity}.
 */
interface LineJpaRepository extends JpaRepository<LineEntity, Long> {

    /** Returns all lines tagged with the given dataset version. */
    List<LineEntity> findByDatasetVersion(long datasetVersion);

    /** Returns all lines for the given version, sorted by short_name. */
    List<LineEntity> findByDatasetVersionOrderByShortNameAsc(long datasetVersion);

    /** Returns the line with the given id and version, or empty if not found. */
    Optional<LineEntity> findByIdAndDatasetVersion(long id, long datasetVersion);

    /** Returns lines matching the given ids and version. */
    List<LineEntity> findByIdInAndDatasetVersion(Collection<Long> ids, long datasetVersion);

    /**
     * Searches lines by number ({@code short_name}) or full name ({@code long_name}).
     * Case-insensitive substring match. Results are sorted by {@code short_name}.
     */
    @Query("SELECT l FROM LineEntity l " +
           "WHERE l.datasetVersion = :version " +
           "AND (LOWER(l.shortName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "  OR LOWER(l.longName)  LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY l.shortName")
    List<LineEntity> searchByNameOrNumber(@Param("query") String query,
                                          @Param("version") long version);
}
