package com.uai.buslines.application.usecase;

import com.uai.buslines.domain.model.ImportResult;
import com.uai.buslines.domain.port.in.ImportNetworkUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for the GTFS import pipeline.
 *
 * <p>Fires the import on the cron expression configured by
 * {@code buslines.import.schedule} (default: 3 AM daily).
 * Override with the {@code IMPORT_SCHEDULE} environment variable.
 *
 * <p>The scheduler is disabled when
 * {@code buslines.import.scheduler.enabled=false} — set this in the test
 * profile ({@code application-test.yml}) to prevent the scheduled trigger from
 * interfering with Testcontainers-based integration tests.
 */
@Component
@ConditionalOnProperty(
        name = "buslines.import.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ImportNetworkScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImportNetworkScheduler.class);

    private final ImportNetworkUseCase useCase;

    public ImportNetworkScheduler(ImportNetworkUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * Runs the import pipeline on the configured schedule.
     * Delegates entirely to {@link ImportNetworkUseCase#triggerImport()};
     * result is logged at INFO level.
     */
    @Scheduled(cron = "${buslines.import.schedule:0 0 3 * * *}")
    public void scheduledImport() {
        log.info("Scheduled GTFS import triggered");
        ImportResult result = useCase.triggerImport();
        log.info("Scheduled import result: {}", result);
    }
}
