package com.uai.buslines;

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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test — validates that {@code V1__create_tables.sql} applies cleanly
 * and the resulting schema matches what Hibernate validates against.
 *
 * <p>Tests run against a real {@code postgres:16} container (same as production)
 * to ensure no PostGIS-only SQL crept in (ADR-003).
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class FlywaySchemaIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private DataSource dataSource;

    @Test
    void allSixTablesExistAfterFlywayMigration() throws Exception {
        String[] expectedTables = {
                "neighborhood",
                "line",
                "line_shape",
                "stop",
                "line_neighborhood",
                "dataset_version"
        };

        try (Connection conn = dataSource.getConnection()) {
            for (String table : expectedTables) {
                try (ResultSet rs = conn.getMetaData()
                        .getTables(null, "public", table, new String[]{"TABLE"})) {
                    assertThat(rs.next())
                            .as("Table '%s' should exist after V1 Flyway migration", table)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void lineNeighborhoodIndexesExist() throws Exception {
        String sql = """
                SELECT indexname
                FROM   pg_indexes
                WHERE  tablename = 'line_neighborhood'
                ORDER  BY indexname
                """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            List<String> indexes = new ArrayList<>();
            while (rs.next()) {
                indexes.add(rs.getString("indexname"));
            }

            assertThat(indexes)
                    .as("idx_line_neighborhood_by_neighborhood must exist")
                    .contains("idx_line_neighborhood_by_neighborhood");
            assertThat(indexes)
                    .as("idx_line_neighborhood_by_line must exist")
                    .contains("idx_line_neighborhood_by_line");
        }
    }

    @Test
    void hibernateValidatePassesNoSchemaDrift() {
        // Hibernate.ddl-auto=validate runs at context startup.
        // If the context loaded (this test is executing), validation passed.
        // We assert the dataSource is healthy as an explicit confirmation.
        assertThat(dataSource).isNotNull();
    }

    @Test
    void noTenantIdColumnAnywhere() throws Exception {
        // ADR-004: no tenant_id column on any table in this schema.
        String[] tables = {"neighborhood", "line", "line_shape", "stop",
                "line_neighborhood", "dataset_version"};

        try (Connection conn = dataSource.getConnection()) {
            for (String table : tables) {
                try (ResultSet rs = conn.getMetaData()
                        .getColumns(null, "public", table, "tenant_id")) {
                    assertThat(rs.next())
                            .as("Table '%s' must NOT have a tenant_id column (ADR-004)", table)
                            .isFalse();
                }
            }
        }
    }
}
