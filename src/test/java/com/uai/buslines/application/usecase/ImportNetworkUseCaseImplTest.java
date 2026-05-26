package com.uai.buslines.application.usecase;

import com.uai.buslines.BoundaryFixtures;
import com.uai.buslines.GtfsFixtures;
import com.uai.buslines.application.GtfsParser;
import com.uai.buslines.application.NeighborhoodClassifier;
import com.uai.buslines.domain.model.GtfsDataset;
import com.uai.buslines.domain.model.GtfsFeedResult;
import com.uai.buslines.domain.model.GtfsParseException;
import com.uai.buslines.domain.model.ImportResult;
import com.uai.buslines.domain.port.out.BoundaryGateway;
import com.uai.buslines.domain.port.out.GtfsFeedGateway;
import com.uai.buslines.domain.port.out.NetworkDatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImportNetworkUseCaseImpl}.
 *
 * <p>All external ports (gateway, parser, classifier, repository) are mocked —
 * no Spring context, no DB, no HTTP. Tests run in milliseconds.
 *
 * <h3>Scenarios covered</h3>
 * <ol>
 *   <li>Unchanged ETag → {@link ImportResult.Skipped}; {@code replaceWith} never called.</li>
 *   <li>Parse failure → {@link ImportResult.Failed}; {@code replaceWith} never called.</li>
 *   <li>Download failure → {@link ImportResult.Failed}; {@code replaceWith} never called.</li>
 *   <li>Single-flight guard → second concurrent trigger returns {@link ImportResult.AlreadyRunning}.</li>
 *   <li>Full success → pipeline completes; returns {@link ImportResult.Completed}.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ImportNetworkUseCaseImplTest {

    @Mock GtfsFeedGateway        feedGateway;
    @Mock GtfsParser             parser;
    @Mock BoundaryGateway        boundaryGateway;
    @Mock NeighborhoodClassifier classifier;
    @Mock NetworkDatasetRepository repository;

    ImportNetworkUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ImportNetworkUseCaseImpl(feedGateway, parser, boundaryGateway, classifier, repository);
        when(repository.activeEtag()).thenReturn(null);
    }

    // ── ETag unchanged ─────────────────────────────────────────────────────────

    @Test
    void triggerImport_unchangedEtag_skipsWithoutCallingReplaceWith() {
        when(feedGateway.download(null)).thenReturn(new GtfsFeedResult.NotModified());

        ImportResult result = useCase.triggerImport();

        assertThat(result).isInstanceOf(ImportResult.Skipped.class);
        // replaceWith must NOT be called when the feed hasn't changed
        verify(repository, never()).replaceWith(any());
        // parsing and classification should not happen either
        verify(parser, never()).parse(any(), any());
        verify(boundaryGateway, never()).loadNeighborhoods();
    }

    @Test
    void triggerImport_unchangedEtag_usesActiveEtagInDownloadRequest() {
        when(repository.activeEtag()).thenReturn("\"etag-v1\"");
        when(feedGateway.download("\"etag-v1\"")).thenReturn(new GtfsFeedResult.NotModified());

        ImportResult result = useCase.triggerImport();

        assertThat(result).isInstanceOf(ImportResult.Skipped.class);
        // verify the active etag was forwarded to the gateway
        verify(feedGateway).download("\"etag-v1\"");
    }

    // ── Failure handling ───────────────────────────────────────────────────────

    @Test
    void triggerImport_parseFailure_doesNotCallReplaceWithAndReturnsFailure() {
        byte[] zip = GtfsFixtures.minimalGtfsZip();
        when(feedGateway.download(null))
                .thenReturn(new GtfsFeedResult.Downloaded(zip, "\"etag-v1\""));
        when(parser.parse(zip, "\"etag-v1\""))
                .thenThrow(new GtfsParseException("Bad GTFS file: missing routes.txt"));

        ImportResult result = useCase.triggerImport();

        assertThat(result).isInstanceOf(ImportResult.Failed.class);
        assertThat(((ImportResult.Failed) result).reason()).contains("Bad GTFS file");
        // replaceWith must NEVER be called after a parse failure
        verify(repository, never()).replaceWith(any());
    }

    @Test
    void triggerImport_classifyFailure_doesNotCallReplaceWithAndReturnsFailure() {
        byte[] zip = GtfsFixtures.minimalGtfsZip();
        GtfsDataset parsed = new GtfsDataset("\"etag-v1\"", Instant.now(), List.of(), List.of(), List.of());
        when(feedGateway.download(null))
                .thenReturn(new GtfsFeedResult.Downloaded(zip, "\"etag-v1\""));
        when(parser.parse(zip, "\"etag-v1\"")).thenReturn(parsed);
        when(boundaryGateway.loadNeighborhoods()).thenReturn(BoundaryFixtures.threeNeighborhoods());
        when(classifier.classify(eq(parsed), any()))
                .thenThrow(new RuntimeException("JTS geometry error"));

        ImportResult result = useCase.triggerImport();

        assertThat(result).isInstanceOf(ImportResult.Failed.class);
        assertThat(((ImportResult.Failed) result).reason()).contains("JTS geometry error");
        verify(repository, never()).replaceWith(any());
    }

    @Test
    void triggerImport_downloadFailure_doesNotCallReplaceWithAndReturnsFailure() {
        when(feedGateway.download(null))
                .thenThrow(new RuntimeException("Network timeout"));

        ImportResult result = useCase.triggerImport();

        assertThat(result).isInstanceOf(ImportResult.Failed.class);
        assertThat(((ImportResult.Failed) result).reason()).contains("Network timeout");
        verify(repository, never()).replaceWith(any());
    }

    // ── Single-flight guard ────────────────────────────────────────────────────

    @Test
    void triggerImport_concurrentTrigger_secondCallReturnsAlreadyRunning() throws Exception {
        // Block the first download until we release it — simulates a long-running import
        CountDownLatch importStarted = new CountDownLatch(1);
        CountDownLatch releaseImport = new CountDownLatch(1);

        when(feedGateway.download(null)).thenAnswer(inv -> {
            importStarted.countDown();   // signal that the import thread has the lock
            releaseImport.await();       // wait until the test releases it
            return new GtfsFeedResult.NotModified();
        });

        // Start the first import in a background thread
        CompletableFuture<ImportResult> firstFuture =
                CompletableFuture.supplyAsync(() -> useCase.triggerImport());

        // Wait until the first import is holding the AtomicBoolean lock
        importStarted.await();

        // Second trigger while first is running must see AlreadyRunning
        ImportResult secondResult = useCase.triggerImport();
        assertThat(secondResult).isInstanceOf(ImportResult.AlreadyRunning.class);

        // Release the first import and confirm it finishes cleanly
        releaseImport.countDown();
        ImportResult firstResult = firstFuture.get();
        assertThat(firstResult).isInstanceOf(ImportResult.Skipped.class);
    }

    @Test
    void triggerImport_afterFailure_lockIsReleasedAndNextCallCanRun() {
        // First call fails
        when(feedGateway.download(null))
                .thenThrow(new RuntimeException("Transient error"))
                .thenReturn(new GtfsFeedResult.NotModified()); // second call succeeds

        ImportResult first = useCase.triggerImport();
        assertThat(first).isInstanceOf(ImportResult.Failed.class);

        // AtomicBoolean must have been reset in the finally block
        ImportResult second = useCase.triggerImport();
        assertThat(second).isInstanceOf(ImportResult.Skipped.class);
    }

    // ── Successful import ──────────────────────────────────────────────────────

    @Test
    void triggerImport_success_callsFullPipelineAndReturnsCompleted() {
        byte[] zip = GtfsFixtures.minimalGtfsZip();
        GtfsDataset parsed     = new GtfsDataset("\"etag-v1\"", Instant.now(), List.of(), List.of(), List.of());
        GtfsDataset classified = new GtfsDataset(
                "\"etag-v1\"", Instant.now(), List.of(), List.of(), List.of(),
                BoundaryFixtures.threeNeighborhoods(), List.of());

        when(feedGateway.download(null))
                .thenReturn(new GtfsFeedResult.Downloaded(zip, "\"etag-v1\""));
        when(parser.parse(zip, "\"etag-v1\"")).thenReturn(parsed);
        when(boundaryGateway.loadNeighborhoods()).thenReturn(BoundaryFixtures.threeNeighborhoods());
        when(classifier.classify(eq(parsed), any())).thenReturn(classified);
        when(repository.currentVersion()).thenReturn(1L);

        ImportResult result = useCase.triggerImport();

        assertThat(result).isInstanceOf(ImportResult.Completed.class);
        ImportResult.Completed completed = (ImportResult.Completed) result;
        assertThat(completed.version()).isEqualTo(1L);
        assertThat(completed.neighborhoodCount()).isEqualTo(3);

        verify(repository).replaceWith(classified);
        verify(parser).parse(zip, "\"etag-v1\"");
        verify(boundaryGateway).loadNeighborhoods();
        verify(classifier).classify(eq(parsed), any());
    }
}
