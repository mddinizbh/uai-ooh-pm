package com.uai.buslines.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.BoundaryFixtures;
import com.uai.buslines.GtfsFixtures;
import com.uai.buslines.application.GtfsParser;
import com.uai.buslines.application.NeighborhoodClassifier;
import com.uai.buslines.domain.model.GtfsDataset;
import com.uai.buslines.domain.port.out.NetworkDatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the public Read API ({@code GET /api/neighborhoods},
 * {@code /api/neighborhoods/{id}/lines}, {@code /api/lines?q=},
 * {@code /api/lines/{id}}, {@code /api/meta}, {@code /api/docs}).
 *
 * <h3>Data setup</h3>
 * A single {@link GtfsDataset} built from {@link GtfsFixtures} and classified
 * against {@link BoundaryFixtures} is persisted before each test class via a
 * static container; tables are truncated and re-seeded in {@code @BeforeEach}
 * to keep tests independent.
 *
 * <h3>Expected fixture data</h3>
 * <ul>
 *   <li>2 lines: R001 = "9400" (Bairro A - Centro), R002 = "2010" (Centro - Bairro B)</li>
 *   <li>3 neighbourhoods: Centro, Bairro A, Bairro B</li>
 *   <li>10 line_neighborhood relations (see {@link BoundaryFixtures})</li>
 * </ul>
 *
 * <h3>Expected neighbourhood counts</h3>
 * <ul>
 *   <li>Centro:   passesThrough=2, departsFrom=2, arrivesAt=1</li>
 *   <li>Bairro A: passesThrough=1, departsFrom=1, arrivesAt=1</li>
 *   <li>Bairro B: passesThrough=1, departsFrom=0, arrivesAt=1</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class QueryNetworkIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
    @Autowired NetworkDatasetRepository networkDatasetRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired DataSource dataSource;

    @BeforeEach
    void resetAndSeed() throws Exception {
        truncateAllTables();
        seedFixtureDataset();
    }

    // ── GET /api/neighborhoods ─────────────────────────────────────────────────

    @Test
    void getNeighborhoods_returns200WithThreeNeighborhoods() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/neighborhoods"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(3);

        // Sorted by name: Bairro A, Bairro B, Centro
        assertThat(body.get(0).get("name").asText()).isEqualTo("Bairro A");
        assertThat(body.get(1).get("name").asText()).isEqualTo("Bairro B");
        assertThat(body.get(2).get("name").asText()).isEqualTo("Centro");
    }

    @Test
    void getNeighborhoods_centroHasCorrectCounts() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/neighborhoods"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode centro = findByName(body, "Centro");

        assertThat(centro).isNotNull();
        assertThat(centro.get("passesThroughCount").asInt()).isEqualTo(2);
        assertThat(centro.get("departsFromCount").asInt()).isEqualTo(2);
        assertThat(centro.get("arrivesAtCount").asInt()).isEqualTo(1);
    }

    @Test
    void getNeighborhoods_barroBHasCorrectCounts() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/neighborhoods"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode barroB = findByName(body, "Bairro B");

        assertThat(barroB).isNotNull();
        assertThat(barroB.get("passesThroughCount").asInt()).isEqualTo(1);
        assertThat(barroB.get("departsFromCount").asInt()).isEqualTo(0);
        assertThat(barroB.get("arrivesAtCount").asInt()).isEqualTo(1);
    }

    // ── GET /api/neighborhoods/{id}/lines ─────────────────────────────────────

    @Test
    void getNeighborhoodLines_centro_returnsThreeGroupedSections() throws Exception {
        long centroId = findNeighborhoodId("Centro");

        MvcResult result = mockMvc.perform(get("/api/neighborhoods/{id}/lines", centroId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.get("neighborhoodName").asText()).isEqualTo("Centro");
        assertThat(body.get("boundaryGeoJson").isObject()).isTrue();
        assertThat(body.get("boundaryGeoJson").get("type").asText()).isIn("Polygon", "MultiPolygon");
        assertThat(body.get("passesThrough").isArray()).isTrue();
        assertThat(body.get("departsFrom").isArray()).isTrue();
        assertThat(body.get("arrivesAt").isArray()).isTrue();

        // Centro: passesThrough has 2 lines (R001 and R002)
        assertThat(body.get("passesThrough").size()).isEqualTo(2);
        // Centro: departsFrom has 2 lines (R001 and R002)
        assertThat(body.get("departsFrom").size()).isEqualTo(2);
        // Centro: arrivesAt has 1 line (R001)
        assertThat(body.get("arrivesAt").size()).isEqualTo(1);
    }

    @Test
    void getNeighborhoodLines_unknownId_returns404WithStructuredError() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/neighborhoods/999999/lines"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("status")).isTrue();
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.has("message")).isTrue();
        // No stack trace
        assertThat(body.has("trace")).isFalse();
        assertThat(body.has("stackTrace")).isFalse();
    }

    // ── GET /api/lines?q= ─────────────────────────────────────────────────────

    @Test
    void searchLines_byNumber_returnsMatchingLine() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/lines").param("q", "9400"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).get("shortName").asText()).isEqualTo("9400");
    }

    @Test
    void searchLines_byName_caseInsensitive() throws Exception {
        // R001 long_name is "Bairro A - Centro" — "centro" should match
        MvcResult result = mockMvc.perform(get("/api/lines").param("q", "centro"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        // At least R001 matches ("Bairro A - Centro") and R002 ("Centro - Bairro B")
        assertThat(body.size()).isGreaterThanOrEqualTo(1);
        // Verify shortName is present for each result
        for (JsonNode line : body) {
            assertThat(line.has("shortName")).isTrue();
            assertThat(line.has("longName")).isTrue();
        }
    }

    @Test
    void searchLines_emptyQuery_returnsAllLines() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/lines"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(2);
    }

    @Test
    void searchLines_noMatch_returnsEmptyArray() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/lines").param("q", "ZZZNOTEXIST"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(0);
    }

    // ── GET /api/lines/{id} ───────────────────────────────────────────────────

    @Test
    void getLineDetail_knownLine_returns200WithGeoJson() throws Exception {
        long lineId = findLineId("9400");

        MvcResult result = mockMvc.perform(get("/api/lines/{id}", lineId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.get("line").get("shortName").asText()).isEqualTo("9400");
        assertThat(body.get("shapes").isArray()).isTrue();
        assertThat(body.get("shapes").size()).isGreaterThan(0);

        // Verify GeoJSON structure in first shape
        JsonNode firstGeometry = body.get("shapes").get(0).get("geometry");
        assertThat(firstGeometry.get("type").asText()).isEqualTo("LineString");
        assertThat(firstGeometry.get("coordinates").isArray()).isTrue();

        // Stops are empty (follow-up task)
        assertThat(body.get("stops").isArray()).isTrue();
    }

    @Test
    void getLineDetail_unknownId_returns404WithStructuredError() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/lines/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.has("message")).isTrue();
        assertThat(body.has("trace")).isFalse();
        assertThat(body.has("stackTrace")).isFalse();
    }

    // ── GET /api/meta ─────────────────────────────────────────────────────────

    @Test
    void getMeta_includesAttributionAndTimestamp() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("attribution")).isTrue();
        assertThat(body.get("attribution").asText()).contains("CC-BY");
        assertThat(body.has("lastImportedAt")).isTrue();
        // After seeding, lastImportedAt should be non-null
        assertThat(body.get("lastImportedAt").isNull()).isFalse();
    }

    // ── GET /api/docs (OpenAPI) ────────────────────────────────────────────────

    @Test
    void openApiDocs_returns200() throws Exception {
        mockMvc.perform(get("/api/docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void truncateAllTables() throws Exception {
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

    private void seedFixtureDataset() {
        GtfsParser parser = new GtfsParser();
        NeighborhoodClassifier classifier = new NeighborhoodClassifier(objectMapper);
        GtfsDataset parsed = parser.parse(GtfsFixtures.minimalGtfsZip(), "\"etag-seed\"");
        GtfsDataset classified = classifier.classify(parsed, BoundaryFixtures.threeNeighborhoods());
        networkDatasetRepository.replaceWith(classified);
    }

    /** Finds a neighbourhood id from the /api/neighborhoods response by name. */
    private long findNeighborhoodId(String name) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/neighborhoods"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode n : body) {
            if (name.equals(n.get("name").asText())) {
                return n.get("id").asLong();
            }
        }
        throw new IllegalStateException("Neighbourhood '" + name + "' not found in /api/neighborhoods");
    }

    /** Finds a line id from the /api/lines response by shortName. */
    private long findLineId(String shortName) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/lines").param("q", shortName))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode l : body) {
            if (shortName.equals(l.get("shortName").asText())) {
                return l.get("id").asLong();
            }
        }
        throw new IllegalStateException("Line '" + shortName + "' not found via /api/lines?q=" + shortName);
    }

    /** Finds a neighbourhood node by name from an array JsonNode. */
    private static JsonNode findByName(JsonNode array, String name) {
        for (JsonNode n : array) {
            if (name.equals(n.get("name").asText())) {
                return n;
            }
        }
        return null;
    }
}
