package com.uai.buslines.adapter.out.persistence;

import jakarta.persistence.*;

/**
 * JPA entity for the {@code line} table.
 *
 * <p>Maps to {@link com.uai.buslines.domain.model.GtfsRoute}.
 * No {@code tenant_id} (ADR-004).
 */
@Entity
@Table(name = "line")
class LineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "short_name", nullable = false, length = 50)
    private String shortName;

    @Column(name = "long_name", nullable = false, length = 500)
    private String longName;

    @Column(name = "gtfs_route_id", nullable = false, length = 255)
    private String gtfsRouteId;

    @Column(name = "dataset_version", nullable = false)
    private Long datasetVersion;

    /** JPA no-arg constructor. */
    protected LineEntity() {}

    LineEntity(String shortName, String longName, String gtfsRouteId, Long datasetVersion) {
        this.shortName = shortName;
        this.longName = longName;
        this.gtfsRouteId = gtfsRouteId;
        this.datasetVersion = datasetVersion;
    }

    Long getId() { return id; }
    String getShortName() { return shortName; }
    String getLongName() { return longName; }
    String getGtfsRouteId() { return gtfsRouteId; }
    Long getDatasetVersion() { return datasetVersion; }
}
