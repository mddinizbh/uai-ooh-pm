package com.uai.buslines.adapter.out.persistence;

import jakarta.persistence.*;

/**
 * JPA entity for the {@code stop} table.
 *
 * <p>WGS84 coordinates stored as plain DOUBLE PRECISION — no PostGIS types (ADR-003).
 * No {@code tenant_id} (ADR-004).
 */
@Entity
@Table(name = "stop")
class StopEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "gtfs_stop_id", nullable = false, length = 255)
    private String gtfsStopId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lon", nullable = false)
    private double lon;

    @Column(name = "dataset_version", nullable = false)
    private Long datasetVersion;

    /** JPA no-arg constructor. */
    protected StopEntity() {}

    StopEntity(String gtfsStopId, String name, double lat, double lon, Long datasetVersion) {
        this.gtfsStopId = gtfsStopId;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.datasetVersion = datasetVersion;
    }

    Long getId() { return id; }
    String getGtfsStopId() { return gtfsStopId; }
    String getName() { return name; }
    double getLat() { return lat; }
    double getLon() { return lon; }
    Long getDatasetVersion() { return datasetVersion; }
}
