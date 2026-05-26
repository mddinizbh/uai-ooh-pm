package com.uai.buslines.application.usecase;

import com.uai.buslines.application.GtfsParser;
import com.uai.buslines.application.NeighborhoodClassifier;
import com.uai.buslines.domain.model.GtfsDataset;
import com.uai.buslines.domain.model.GtfsFeedResult;
import com.uai.buslines.domain.model.ImportResult;
import com.uai.buslines.domain.model.Neighborhood;
import com.uai.buslines.domain.port.in.ImportNetworkUseCase;
import com.uai.buslines.domain.port.out.BoundaryGateway;
import com.uai.buslines.domain.port.out.GtfsFeedGateway;
import com.uai.buslines.domain.port.out.NetworkDatasetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Application use case that orchestrates the full GTFS import pipeline.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Read the ETag of the currently active dataset.</li>
 *   <li>Attempt a conditional GET via {@link GtfsFeedGateway#download(String)}.</li>
 *   <li>If the server responds with 304 (Not Modified), return {@link ImportResult.Skipped}
 *       immediately — the active dataset is unchanged.</li>
 *   <li>Parse the ZIP into a {@link GtfsDataset} via {@link GtfsParser}.</li>
 *   <li>Load neighbourhood boundaries via {@link BoundaryGateway}.</li>
 *   <li>Classify route↔neighbourhood relations via {@link NeighborhoodClassifier}.</li>
 *   <li>Atomically swap the dataset via {@link NetworkDatasetRepository#replaceWith(GtfsDataset)}.</li>
 * </ol>
 *
 * <h3>Single-flight guard</h3>
 * An {@link AtomicBoolean} prevents overlapping imports. If {@code triggerImport} is called
 * while another run is in progress, it returns {@link ImportResult.AlreadyRunning} immediately.
 *
 * <h3>Keep-last-good failure handling</h3>
 * Any exception thrown before {@code replaceWith} is called is caught; the method returns
 * {@link ImportResult.Failed} and the previously active dataset remains active.
 * {@code replaceWith} is transactional — a failure inside it also rolls back completely.
 */
@Component
public class ImportNetworkUseCaseImpl implements ImportNetworkUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportNetworkUseCaseImpl.class);

    /** Single-flight guard — {@code true} while an import is running. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final GtfsFeedGateway feedGateway;
    private final GtfsParser parser;
    private final BoundaryGateway boundaryGateway;
    private final NeighborhoodClassifier classifier;
    private final NetworkDatasetRepository repository;

    public ImportNetworkUseCaseImpl(GtfsFeedGateway feedGateway,
                                    GtfsParser parser,
                                    BoundaryGateway boundaryGateway,
                                    NeighborhoodClassifier classifier,
                                    NetworkDatasetRepository repository) {
        this.feedGateway = feedGateway;
        this.parser = parser;
        this.boundaryGateway = boundaryGateway;
        this.classifier = classifier;
        this.repository = repository;
    }

    // ── ImportNetworkUseCase ───────────────────────────────────────────────────

    @Override
    public ImportResult triggerImport() {
        if (!running.compareAndSet(false, true)) {
            log.info("Import already in progress — concurrent trigger skipped");
            return new ImportResult.AlreadyRunning();
        }
        try {
            return runPipeline();
        } finally {
            running.set(false);
        }
    }

    // ── Pipeline ───────────────────────────────────────────────────────────────

    private ImportResult runPipeline() {
        String lastEtag = repository.activeEtag();
        log.info("Import started — lastKnownEtag={}", lastEtag);

        try {
            GtfsFeedResult feedResult = feedGateway.download(lastEtag);

            return switch (feedResult) {
                case GtfsFeedResult.NotModified() -> {
                    log.info("GTFS feed unchanged (ETag: {}) — import skipped", lastEtag);
                    yield new ImportResult.Skipped("Feed unchanged (ETag: " + lastEtag + ")");
                }
                case GtfsFeedResult.Downloaded(byte[] content, String etag) -> {
                    log.info("GTFS feed downloaded (ETag: {}) — parsing and classifying", etag);
                    GtfsDataset parsed = parser.parse(content, etag);
                    List<Neighborhood> neighborhoods = boundaryGateway.loadNeighborhoods();
                    GtfsDataset classified = classifier.classify(parsed, neighborhoods);
                    repository.replaceWith(classified);
                    long version = repository.currentVersion();
                    log.info("Import complete: version={}, routes={}, neighborhoods={}, relations={}",
                            version,
                            classified.routes().size(),
                            classified.neighborhoods().size(),
                            classified.lineRelations().size());
                    yield new ImportResult.Completed(
                            version,
                            classified.routes().size(),
                            classified.neighborhoods().size());
                }
            };

        } catch (Exception e) {
            log.error("Import failed — keeping last good dataset active: {}", e.getMessage(), e);
            return new ImportResult.Failed(e.getMessage());
        }
    }
}
