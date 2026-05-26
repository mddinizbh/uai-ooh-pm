package com.uai.buslines.adapter.out.persistence;

import com.uai.buslines.domain.model.Neighborhood;

/**
 * Stateless mapper between {@link Neighborhood} (domain) and
 * {@link NeighborhoodEntity} (persistence).
 *
 * <p>The {@code boundaryGeoJson} string is passed through unchanged —
 * it is already a valid GeoJSON geometry string in the domain model (ADR-003).
 */
final class NeighborhoodMapper {

    private NeighborhoodMapper() {}

    static NeighborhoodEntity toEntity(Neighborhood neighborhood, long datasetVersion) {
        return new NeighborhoodEntity(
                neighborhood.name(),
                neighborhood.boundaryGeoJson(),
                datasetVersion);
    }

    static Neighborhood toDomain(NeighborhoodEntity entity) {
        return new Neighborhood(entity.getName(), entity.getBoundaryGeoJson());
    }
}
