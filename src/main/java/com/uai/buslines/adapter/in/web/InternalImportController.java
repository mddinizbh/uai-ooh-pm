package com.uai.buslines.adapter.in.web;

import com.uai.buslines.domain.port.in.ImportNetworkUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal admin endpoint — triggers a GTFS reimport on demand.
 *
 * <p>Protected by {@link InternalApiKeyFilter}: callers must supply the
 * {@code X-UAI-Internal-Key} header.
 *
 * <p>The import runs asynchronously on the {@code importTaskExecutor} thread pool,
 * so the endpoint returns 202 immediately without blocking on completion.
 * Actual import progress is reported via structured logs.
 */
@RestController
@RequestMapping("/api/internal")
@Tag(name = "Internal", description = "Admin endpoints (protected by X-UAI-Internal-Key)")
public class InternalImportController {

    private final ImportNetworkUseCase useCase;
    private final TaskExecutor importTaskExecutor;

    public InternalImportController(ImportNetworkUseCase useCase,
                                    TaskExecutor importTaskExecutor) {
        this.useCase = useCase;
        this.importTaskExecutor = importTaskExecutor;
    }

    /**
     * Triggers an asynchronous GTFS reimport.
     *
     * <p>Returns 202 immediately. The import pipeline runs in the background;
     * use structured logs or the {@code dataset_version} table to track progress.
     * If an import is already running, the new trigger is silently dropped (single-flight).
     *
     * @return 202 Accepted
     */
    @PostMapping("/import")
    @Operation(
            summary = "Trigger GTFS reimport",
            description = "Starts an asynchronous GTFS import. Returns 202 immediately. " +
                    "Requires X-UAI-Internal-Key header.")
    @ApiResponse(responseCode = "202", description = "Import accepted and running asynchronously")
    @ApiResponse(responseCode = "401", description = "Missing or invalid X-UAI-Internal-Key")
    public ResponseEntity<Void> triggerImport() {
        importTaskExecutor.execute(() -> useCase.triggerImport());
        return ResponseEntity.accepted().build();
    }
}
