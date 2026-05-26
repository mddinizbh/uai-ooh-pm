package com.uai.buslines.domain.model;

/**
 * The result of a single import orchestration run.
 *
 * <p>uAI RULE-JAVA-02: sealed type — switch expressions must cover all permits
 * without a {@code default} branch; the compiler enforces exhaustiveness.
 */
public sealed interface ImportResult
        permits ImportResult.Completed, ImportResult.Skipped,
                ImportResult.AlreadyRunning, ImportResult.Failed {

    /**
     * Import pipeline ran to completion and the active dataset was advanced.
     *
     * @param version          the new active {@code dataset_version.version}
     * @param routeCount       number of routes persisted
     * @param neighborhoodCount number of neighbourhoods persisted
     */
    record Completed(long version, int routeCount, int neighborhoodCount) implements ImportResult {}

    /**
     * GTFS feed ETag was unchanged — the import was intentionally skipped.
     * The existing active dataset remains current.
     *
     * @param reason human-readable explanation (e.g. "Feed unchanged (ETag: …)")
     */
    record Skipped(String reason) implements ImportResult {}

    /**
     * Another import run was already in progress when this trigger arrived.
     * The concurrent trigger was dropped (single-flight guard).
     */
    record AlreadyRunning() implements ImportResult {}

    /**
     * The import failed with an unexpected exception.
     * The previously active dataset was preserved — no partial data was committed.
     *
     * @param reason the exception message
     */
    record Failed(String reason) implements ImportResult {}
}
