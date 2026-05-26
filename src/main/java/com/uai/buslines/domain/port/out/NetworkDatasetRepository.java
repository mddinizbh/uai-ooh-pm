package com.uai.buslines.domain.port.out;

import com.uai.buslines.domain.model.GtfsDataset;

/**
 * Driven port — persistence for a complete {@link GtfsDataset}.
 *
 * <p>Implemented by the JPA persistence adapter ({@code JpaNetworkDatasetRepository}).
 * Consumed by the import orchestration use case (task_05).
 *
 * <p>No framework, JPA, or HTTP types (RULE-JAVA-01).
 */
public interface NetworkDatasetRepository {

    /**
     * Returns the version number of the currently active dataset, or {@code 0L}
     * if no active dataset exists (first import has not run yet).
     *
     * <p>{@code BIGSERIAL} starts at 1, so {@code 0L} is a safe sentinel for "none".
     */
    long currentVersion();

    /**
     * Returns the {@code ETag} of the currently active dataset version, or
     * {@code null} if no active dataset exists.
     *
     * <p>Used by the import orchestration use case (task_05) to implement the
     * conditional-GET ETag guard: if the server returns HTTP 304 for this ETag
     * the import is skipped and the existing active dataset remains current.
     */
    String activeEtag();

    /**
     * Persists all entities from {@code dataset} as a new dataset version and
     * atomically activates it, marking the previously active version as archived.
     *
     * <p>The operation is fully transactional: if any step fails, the transaction
     * rolls back and the previously active version remains active and serving.
     *
     * @param dataset fully parsed and classified GTFS dataset (all 7 fields populated)
     * @throws IllegalStateException if the dataset contains line-shapes or
     *                               line-neighbourhood relations referencing route IDs
     *                               or neighbourhood names that are not present in the
     *                               dataset's own routes or neighbourhoods lists
     */
    void replaceWith(GtfsDataset dataset);
}
