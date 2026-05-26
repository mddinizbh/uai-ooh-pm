package com.uai.buslines.adapter.out.persistence;

import com.uai.buslines.domain.model.GtfsStop;

/**
 * Stateless mapper between {@link GtfsStop} (domain) and {@link StopEntity} (persistence).
 *
 * <p>Coordinates (lat/lon) are passed through unchanged — validated WGS84
 * double precision values. No PostGIS types (ADR-003).
 */
final class StopMapper {

    private StopMapper() {}

    static StopEntity toEntity(GtfsStop stop, long datasetVersion) {
        return new StopEntity(
                stop.stopId(),
                stop.name(),
                stop.lat(),
                stop.lon(),
                datasetVersion);
    }

    static GtfsStop toDomain(StopEntity entity) {
        return new GtfsStop(
                entity.getGtfsStopId(),
                entity.getName(),
                entity.getLat(),
                entity.getLon());
    }
}
