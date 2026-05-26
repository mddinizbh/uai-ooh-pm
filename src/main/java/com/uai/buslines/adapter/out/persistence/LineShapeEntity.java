package com.uai.buslines.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code line_shape} table.
 *
 * <p>Route geometry stored as GeoJSON LineString {@code jsonb} — no PostGIS (ADR-003).
 * {@code direction} is 0 (outbound) or 1 (inbound), mapped to SMALLINT.
 * No {@code tenant_id} (ADR-004).
 */
@Entity
@Table(name = "line_shape")
class LineShapeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    /**
     * GTFS direction_id: 0 = outbound, 1 = inbound.
     * Schema column is SMALLINT — mapped to {@link Short} for correct Hibernate type validation.
     */
    @Column(name = "direction", nullable = false, columnDefinition = "SMALLINT")
    private Short direction;

    /**
     * GeoJSON LineString for the route path (WGS84 coordinates, [lon, lat] order).
     * Stored as {@code jsonb} — no PostGIS geometry type (ADR-003).
     */
    @Column(name = "geometry_geojson", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String geometryGeoJson;

    @Column(name = "dataset_version", nullable = false)
    private Long datasetVersion;

    /** JPA no-arg constructor. */
    protected LineShapeEntity() {}

    LineShapeEntity(Long lineId, Short direction, String geometryGeoJson, Long datasetVersion) {
        this.lineId = lineId;
        this.direction = direction;
        this.geometryGeoJson = geometryGeoJson;
        this.datasetVersion = datasetVersion;
    }

    Long getId() { return id; }
    Long getLineId() { return lineId; }
    Short getDirection() { return direction; }
    String getGeometryGeoJson() { return geometryGeoJson; }
    Long getDatasetVersion() { return datasetVersion; }
}
