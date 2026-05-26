package com.uai.buslines.adapter.out.persistence;

import com.uai.buslines.domain.model.GtfsRoute;

/**
 * Stateless mapper between {@link GtfsRoute} (domain) and {@link LineEntity} (persistence).
 *
 * <p>The GTFS {@code route_id} maps to {@link LineEntity#getGtfsRouteId()} —
 * that field is the stable business key used for FK resolution in the import step.
 */
final class LineMapper {

    private LineMapper() {}

    static LineEntity toEntity(GtfsRoute route, long datasetVersion) {
        return new LineEntity(
                route.shortName(),
                route.longName(),
                route.routeId(),
                datasetVersion);
    }

    static GtfsRoute toDomain(LineEntity entity) {
        return new GtfsRoute(
                entity.getGtfsRouteId(),
                entity.getShortName(),
                entity.getLongName());
    }
}
