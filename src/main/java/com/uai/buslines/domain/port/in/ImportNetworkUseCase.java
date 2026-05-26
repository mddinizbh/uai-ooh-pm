package com.uai.buslines.domain.port.in;

import com.uai.buslines.domain.model.ImportResult;

/**
 * Driving port — GTFS import orchestration.
 *
 * <p>Implemented by {@code application.usecase.ImportNetworkUseCaseImpl}.
 * Called by:
 * <ul>
 *   <li>The scheduled trigger ({@code ImportNetworkScheduler}) — on a configurable cron.</li>
 *   <li>The internal admin endpoint ({@code InternalImportController}) — on demand, async 202.</li>
 * </ul>
 *
 * <p>Contract:
 * <ul>
 *   <li>Exactly one import runs at a time (single-flight guard).</li>
 *   <li>If the GTFS feed ETag is unchanged, the import is skipped without touching the DB.</li>
 *   <li>On any failure the active dataset is preserved — {@code replaceWith} is never called
 *       after a partial failure.</li>
 * </ul>
 */
public interface ImportNetworkUseCase {

    /**
     * Runs the full import pipeline:
     * download → parse → classify → {@code replaceWith}.
     *
     * @return a sealed {@link ImportResult} describing the outcome
     */
    ImportResult triggerImport();
}
