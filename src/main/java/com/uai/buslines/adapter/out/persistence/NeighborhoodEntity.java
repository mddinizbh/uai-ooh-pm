package com.uai.buslines.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code neighborhood} table.
 *
 * <p>Geometry stored as GeoJSON {@code jsonb} — no PostGIS types (ADR-003).
 * No {@code tenant_id} (ADR-004).
 */
@Entity
@Table(name = "neighborhood")
class NeighborhoodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "boundary_geojson", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String boundaryGeoJson;

    @Column(name = "dataset_version", nullable = false)
    private Long datasetVersion;

    /** JPA no-arg constructor. */
    protected NeighborhoodEntity() {}

    NeighborhoodEntity(String name, String boundaryGeoJson, Long datasetVersion) {
        this.name = name;
        this.boundaryGeoJson = boundaryGeoJson;
        this.datasetVersion = datasetVersion;
    }

    Long getId() { return id; }
    String getName() { return name; }
    String getBoundaryGeoJson() { return boundaryGeoJson; }
    Long getDatasetVersion() { return datasetVersion; }
}
