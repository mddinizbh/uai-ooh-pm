package com.uai.buslines.domain.model;

/**
 * A single directional route shape for a bus line.
 *
 * <p>Geometry is stored and returned as a raw GeoJSON {@code LineString} string
 * (WGS84, {@code [longitude, latitude]} coordinate order) — no PostGIS types (ADR-003).
 *
 * <p>No framework dependencies — pure domain value object (RULE-JAVA-01).
 *
 * @param id               database primary key of the {@code line_shape} row
 * @param direction        GTFS direction_id: {@code 0} = outbound, {@code 1} = inbound
 * @param geometryGeoJson  raw GeoJSON LineString, e.g.
 *                         {@code {"type":"LineString","coordinates":[[lon,lat],...]}}
 */
public record LineShape(
        long id,
        int direction,
        String geometryGeoJson
) {}
