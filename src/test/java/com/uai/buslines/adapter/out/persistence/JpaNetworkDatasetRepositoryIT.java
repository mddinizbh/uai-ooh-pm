package com.uai.buslines.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.BoundaryFixtures;
import com.uai.buslines.GtfsFixtures;
import com.uai.buslines.application.GtfsParser;
import com.uai.buslines.application.NeighborhoodClassifier;
import com.uai.buslines.domain.model.*;
import com.uai.buslines.domain.port.out.NetworkDatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test — verifies the transactional dataset-swap behaviour of
 * {@link JpaNetworkDatasetRepository} against a real {@code postgres:16} container.
 *
 * <h3>Test scenarios</h3>
 * <ol>
 *   <li>{@code currentVersion()} returns {@code 0} before any import.</li>
 *   <li>{@code replaceWith} persists all entities and activates the new version.</li>
 *   <li>A second {@code replaceWith} archives the first version and activates the new one.</li>
 *   <li>An exception mid-{@code replaceWith} rolls back; the previous active version
 *       remains active and unchanged.</li>
 *   <li>After a successful swap, rows are tagged by version; filtering by
 *       {@code currentVersion()} yields only active-version rows.</li>
 * </ol>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class JpaNetworkDatasetRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /** SUT injected via the public port interface. */
    @Autowired
    NetworkDatasetRepository repository;

    /** Internal repos — same package, accessible for white-box verification. */
    @Autowired DatasetVersionJpaRepository versionRepo;
    @Autowired LineJpaRepository           lineRepo;
    @Autowired NeighborhoodJpaRepository   neighborhoodRepo;
    @Autowired StopJpaRepository           stopRepo;
    @Autowired LineShapeJpaRepository      lineShapeRepo;
    @Autowired LineNeighborhoodJpaRepository lineNeighborhoodRepo;

    @Autowired DataSource dataSource;

    // ── Fixture helpers ────────────────────────────────────────────────────────

    private static GtfsDataset buildClassifiedDataset(String etag) {
        GtfsParser parser = new GtfsParser();
        NeighborhoodClassifier classifier = new NeighborhoodClassifier(new ObjectMapper());
        GtfsDataset parsed = parser.parse(GtfsFixtures.minimalGtfsZip(), etag);
        return classifier.classify(parsed, BoundaryFixtures.threeNeighborhoods());
    }

    @BeforeEach
    void truncateAllTables() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Truncate in FK-dependency order (or all at once — Postgres handles intra-list FKs)
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

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    void currentVersion_returnsZeroWhenNoDatasetExists() {
        assertThat(repository.currentVersion()).isEqualTo(0L);
    }

    @Test
    void replaceWith_persistsAllEntitiesAndActivatesVersion() {
        GtfsDataset dataset = buildClassifiedDataset("\"etag-v1\"");

        repository.replaceWith(dataset);

        long version = repository.currentVersion();
        assertThat(version).isPositive();

        // All entity counts match fixture content
        assertThat(lineRepo.findAll())            .as("2 routes in fixture").hasSize(2);
        assertThat(neighborhoodRepo.findAll())    .as("3 neighbourhoods in fixture").hasSize(3);
        assertThat(stopRepo.findAll())            .as("3 stops in fixture").hasSize(3);
        assertThat(lineShapeRepo.findAll())       .as("3 shapes in fixture").hasSize(3);
        assertThat(lineNeighborhoodRepo.findAll()).as("10 relations in fixture").hasSize(10);

        // All rows tagged with the active version
        assertThat(lineRepo.findAll())
                .allMatch(l -> l.getDatasetVersion() == version);
        assertThat(neighborhoodRepo.findAll())
                .allMatch(n -> n.getDatasetVersion() == version);

        // dataset_version row is ACTIVE and has the correct etag
        DatasetVersionEntity v = versionRepo.findById(version).orElseThrow();
        assertThat(v.getStatus()).isEqualTo("ACTIVE");
        assertThat(v.getGtfsSourceEtag()).isEqualTo("\"etag-v1\"");
        assertThat(v.getImportedAt()).isNotNull();
    }

    @Test
    void replaceWith_secondSwap_archivesFirstVersionAndActivatesSecond() {
        GtfsDataset first  = buildClassifiedDataset("\"etag-v1\"");
        GtfsDataset second = buildClassifiedDataset("\"etag-v2\"");

        repository.replaceWith(first);
        long firstVersion = repository.currentVersion();

        repository.replaceWith(second);
        long secondVersion = repository.currentVersion();

        assertThat(secondVersion).isGreaterThan(firstVersion);

        DatasetVersionEntity v1 = versionRepo.findById(firstVersion).orElseThrow();
        assertThat(v1.getStatus()).isEqualTo("ARCHIVED");

        DatasetVersionEntity v2 = versionRepo.findById(secondVersion).orElseThrow();
        assertThat(v2.getStatus()).isEqualTo("ACTIVE");
        assertThat(v2.getGtfsSourceEtag()).isEqualTo("\"etag-v2\"");
    }

    @Test
    void replaceWith_exceptionMidSwap_rollsBackAndPreservesPreviousActiveVersion() {
        // Establish a valid active dataset
        GtfsDataset initial = buildClassifiedDataset("\"etag-initial\"");
        repository.replaceWith(initial);
        long previousActiveVersion = repository.currentVersion();
        assertThat(previousActiveVersion).isPositive();

        // Build a broken dataset: lineRelation references a routeId not in routes
        GtfsDataset brokenDataset = new GtfsDataset(
                "\"etag-broken\"",
                Instant.now(),
                List.of(new GtfsRoute("R001", "9400", "Bairro A - Centro")),
                List.of(),
                List.of(),
                List.of(new Neighborhood("Centro", BoundaryFixtures.CENTRO_GEOJSON)),
                List.of(new LineNeighborhoodRelation(
                        "NONEXISTENT_ROUTE_ID",   // not in routes → triggers IllegalStateException
                        "Centro",
                        new LineRelation.PassesThrough())));

        // replaceWith must throw and roll back
        assertThatThrownBy(() -> repository.replaceWith(brokenDataset))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NONEXISTENT_ROUTE_ID");

        // Previous active version must still be active
        assertThat(repository.currentVersion()).isEqualTo(previousActiveVersion);

        // Exactly one ACTIVE version in the DB
        long activeCount = versionRepo.findAll().stream()
                .filter(v -> "ACTIVE".equals(v.getStatus()))
                .count();
        assertThat(activeCount).isEqualTo(1);

        // The failed version's rows must NOT exist (rolled back)
        // Check: total line count is still from the initial import only (2 routes)
        assertThat(lineRepo.findAll()).hasSize(2);
    }

    @Test
    void replaceWith_afterSuccessfulSwap_rowsAreTaggedByVersion_noLeakage() {
        GtfsDataset first  = buildClassifiedDataset("\"etag-v1\"");
        GtfsDataset second = buildClassifiedDataset("\"etag-v2\"");

        repository.replaceWith(first);
        long firstVersion = repository.currentVersion();

        repository.replaceWith(second);
        long activeVersion = repository.currentVersion();

        // Both version sets of rows exist in the DB (2 rows per fixture route × 2 versions)
        assertThat(lineRepo.findAll()).hasSize(4); // 2 routes × 2 versions

        // Filtering by active version returns only the latest rows
        List<LineEntity> activeLines = lineRepo.findByDatasetVersion(activeVersion);
        assertThat(activeLines).hasSize(2);
        assertThat(activeLines).noneMatch(l -> l.getDatasetVersion() == firstVersion);

        // Archived version rows are still present and tagged with the old version
        List<LineEntity> archivedLines = lineRepo.findByDatasetVersion(firstVersion);
        assertThat(archivedLines).hasSize(2);
        assertThat(archivedLines).noneMatch(l -> l.getDatasetVersion() == activeVersion);
    }
}
