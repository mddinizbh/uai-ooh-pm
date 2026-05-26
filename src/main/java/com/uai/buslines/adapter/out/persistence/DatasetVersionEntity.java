package com.uai.buslines.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for the {@code dataset_version} table.
 *
 * <p>Tracks every import batch. Status lifecycle:
 * <ol>
 *   <li>{@code IMPORTING} — new rows are being inserted into all tables</li>
 *   <li>{@code ACTIVE} — swap complete; this version is being served</li>
 *   <li>{@code ARCHIVED} — superseded; rows still present for debug/rollback</li>
 *   <li>{@code FAILED} — import failed; never activated (reserved for task_05)</li>
 * </ol>
 *
 * <p>No {@code tenant_id} (ADR-004). No PostGIS types (ADR-003).
 */
@Entity
@Table(name = "dataset_version")
class DatasetVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version")
    private Long version;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "gtfs_source_etag", length = 255)
    private String gtfsSourceEtag;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    /** JPA no-arg constructor. */
    protected DatasetVersionEntity() {}

    DatasetVersionEntity(Instant importedAt, String gtfsSourceEtag, String status) {
        this.importedAt = importedAt;
        this.gtfsSourceEtag = gtfsSourceEtag;
        this.status = status;
    }

    Long getVersion() { return version; }
    Instant getImportedAt() { return importedAt; }
    String getGtfsSourceEtag() { return gtfsSourceEtag; }
    String getStatus() { return status; }
    void setStatus(String status) { this.status = status; }
}
