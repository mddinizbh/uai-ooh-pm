package com.uai.buslines.adapter.in.web;

import com.uai.buslines.BoundaryFixtures;
import com.uai.buslines.GtfsFixtures;
import com.uai.buslines.domain.model.GtfsFeedResult;
import com.uai.buslines.domain.model.GtfsDownloadException;
import com.uai.buslines.domain.port.out.BoundaryGateway;
import com.uai.buslines.domain.port.out.GtfsFeedGateway;
import com.uai.buslines.domain.port.out.NetworkDatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the import trigger endpoint and the full import pipeline.
 *
 * <h3>Scenarios covered</h3>
 * <ol>
 *   <li>{@code POST /api/internal/import} with valid {@code X-UAI-Internal-Key} → 202.</li>
 *   <li>{@code POST /api/internal/import} with missing key → 401.</li>
 *   <li>{@code POST /api/internal/import} with wrong key → 401.</li>
 *   <li>End-to-end: trigger import → active version advances.</li>
 *   <li>Forced download failure → prior active version is preserved.</li>
 * </ol>
 *
 * <h3>Async handling</h3>
 * {@link SyncTaskExecutor} is provided via an inner {@code @TestConfiguration} with
 * {@code @Primary}, overriding the production {@code importTaskExecutor} bean.
 * This causes the import to run synchronously in the HTTP request thread, so
 * {@code MockMvc.perform()} returns only after the import completes — enabling
 * deterministic DB assertions without polling or sleeping.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "buslines.internal.api-key=test-import-key-123",
        // Allow the inner @TestConfiguration to override the production importTaskExecutor bean
        // with a SyncTaskExecutor so imports run synchronously in the HTTP request thread.
        "spring.main.allow-bean-definition-overriding=true"
})
class ImportIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    // ── Test doubles ──────────────────────────────────────────────────────────

    /** Override production TaskExecutor with a synchronous one for deterministic tests. */
    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean("importTaskExecutor")
        @Primary
        public TaskExecutor importTaskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @MockBean GtfsFeedGateway gtfsFeedGateway;
    @MockBean BoundaryGateway boundaryGateway;

    // ── Injected beans ────────────────────────────────────────────────────────

    @Autowired MockMvc mockMvc;
    @Autowired NetworkDatasetRepository repository;
    @Autowired DataSource dataSource;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void truncateTables() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    TRUNCATE TABLE
                        line_neighborhood,
                        line_shape,
                        stop,
                        line,
                        neighborhood,
                        dataset_version
                    RESTART IDENTITY
                    """);
        }
    }

    // ── API key protection ─────────────────────────────────────────────────────

    @Test
    void postImport_validKey_returns202() throws Exception {
        // Minimal mock: feed returns NotModified so no DB writes are needed
        when(gtfsFeedGateway.download(any())).thenReturn(new GtfsFeedResult.NotModified());

        mockMvc.perform(post("/api/internal/import")
                        .header("X-UAI-Internal-Key", "test-import-key-123"))
                .andExpect(status().isAccepted());
    }

    @Test
    void postImport_missingKey_returns401() throws Exception {
        mockMvc.perform(post("/api/internal/import"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postImport_wrongKey_returns401() throws Exception {
        mockMvc.perform(post("/api/internal/import")
                        .header("X-UAI-Internal-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    // ── End-to-end import ──────────────────────────────────────────────────────

    @Test
    void postImport_validFeed_activeVersionAdvances() throws Exception {
        when(gtfsFeedGateway.download(null))
                .thenReturn(new GtfsFeedResult.Downloaded(GtfsFixtures.minimalGtfsZip(), "\"etag-v1\""));
        when(boundaryGateway.loadNeighborhoods())
                .thenReturn(BoundaryFixtures.threeNeighborhoods());

        long versionBefore = repository.currentVersion();
        assertThat(versionBefore).isEqualTo(0L); // no active dataset yet

        mockMvc.perform(post("/api/internal/import")
                        .header("X-UAI-Internal-Key", "test-import-key-123"))
                .andExpect(status().isAccepted());

        // SyncTaskExecutor: import ran synchronously — version must have advanced
        long versionAfter = repository.currentVersion();
        assertThat(versionAfter).isGreaterThan(versionBefore);
    }

    @Test
    void postImport_downloadFailure_preservesPreviousActiveVersion() throws Exception {
        // Step 1: establish a good active dataset
        when(gtfsFeedGateway.download(null))
                .thenReturn(new GtfsFeedResult.Downloaded(GtfsFixtures.minimalGtfsZip(), "\"etag-v1\""));
        when(boundaryGateway.loadNeighborhoods())
                .thenReturn(BoundaryFixtures.threeNeighborhoods());

        mockMvc.perform(post("/api/internal/import")
                        .header("X-UAI-Internal-Key", "test-import-key-123"))
                .andExpect(status().isAccepted());

        long activeVersionAfterFirstImport = repository.currentVersion();
        assertThat(activeVersionAfterFirstImport).isPositive();

        // Step 2: second trigger fails on download — active version must be preserved
        when(gtfsFeedGateway.download("\"etag-v1\""))
                .thenThrow(new GtfsDownloadException("Upstream unavailable", 503));

        mockMvc.perform(post("/api/internal/import")
                        .header("X-UAI-Internal-Key", "test-import-key-123"))
                .andExpect(status().isAccepted()); // 202 is still returned (async endpoint)

        // Active version must not have changed after the failure
        assertThat(repository.currentVersion()).isEqualTo(activeVersionAfterFirstImport);
    }

    @Test
    void postImport_unchangedEtag_skipsAndPreservesActiveVersion() throws Exception {
        // Establish an active version
        when(gtfsFeedGateway.download(null))
                .thenReturn(new GtfsFeedResult.Downloaded(GtfsFixtures.minimalGtfsZip(), "\"etag-v1\""));
        when(boundaryGateway.loadNeighborhoods())
                .thenReturn(BoundaryFixtures.threeNeighborhoods());

        mockMvc.perform(post("/api/internal/import")
                        .header("X-UAI-Internal-Key", "test-import-key-123"))
                .andExpect(status().isAccepted());

        long activeVersion = repository.currentVersion();
        assertThat(activeVersion).isPositive();

        // Second trigger: feed reports no change
        when(gtfsFeedGateway.download("\"etag-v1\""))
                .thenReturn(new GtfsFeedResult.NotModified());

        mockMvc.perform(post("/api/internal/import")
                        .header("X-UAI-Internal-Key", "test-import-key-123"))
                .andExpect(status().isAccepted());

        // Version must remain unchanged
        assertThat(repository.currentVersion()).isEqualTo(activeVersion);
    }
}
