package com.uai.buslines.domain.port.out;

import com.uai.buslines.domain.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Driven port — read-only queries over the persisted GTFS dataset.
 *
 * <p>Implemented by {@code JpaNetworkQueryRepository} in the persistence adapter.
 * Consumed by {@code QueryNetworkUseCaseImpl} in the application layer.
 *
 * <p>All methods that accept a {@code version} parameter expect the version number
 * of the currently active dataset (obtained via {@link #activeVersion()}).
 *
 * <p>No framework, JPA, or HTTP types (RULE-JAVA-01).
 */
public interface NetworkQueryRepository {

    /**
     * Returns the version number of the currently active dataset, or {@code 0L}
     * if no active dataset exists.
     */
    long activeVersion();

    /**
     * Returns the timestamp of the most recent successful import, or
     * {@code Optional.empty()} if no active dataset exists.
     */
    Optional<Instant> activeImportedAt();

    /**
     * Returns all neighbourhoods tagged with the given dataset version, sorted by
     * name, with per-relation line counts.
     *
     * @param version active dataset version (must be &gt; 0)
     * @return neighbourhood summaries; empty if the version has no neighbourhoods
     */
    List<NeighborhoodSummary> listNeighborhoods(long version);

    /**
     * Returns lines for the given neighbourhood grouped into the three relation
     * buckets, or {@code Optional.empty()} if no neighbourhood with that id exists
     * in the given version.
     *
     * @param neighborhoodId database primary key
     * @param version        active dataset version
     */
    Optional<NeighborhoodLines> linesByNeighborhood(long neighborhoodId, long version);

    /**
     * Searches lines by number ({@code short_name}) or full name ({@code long_name}).
     *
     * <p>The match is case-insensitive and substring-based. If {@code query} is
     * blank, all lines for the version are returned sorted by {@code short_name}.
     *
     * @param query   search term; may be empty
     * @param version active dataset version
     * @return matching line summaries
     */
    List<LineSummary> searchLines(String query, long version);

    /**
     * Returns full detail for the given line, or {@code Optional.empty()} if no
     * line with that id exists in the given version.
     *
     * @param lineId  database primary key of the line
     * @param version active dataset version
     */
    Optional<LineDetail> lineDetail(long lineId, long version);
}
