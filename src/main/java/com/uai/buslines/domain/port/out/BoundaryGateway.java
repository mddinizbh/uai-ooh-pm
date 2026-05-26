package com.uai.buslines.domain.port.out;

import com.uai.buslines.domain.model.BoundaryParseException;
import com.uai.buslines.domain.model.Neighborhood;

import java.util.List;

/**
 * Driven port — loads neighbourhood boundary polygons from an external source.
 *
 * <p>The production implementation ({@code HttpBoundaryGateway} in
 * {@code adapter/out/boundary}) downloads the official PBH GeoJSON boundary file
 * and delegates parsing + CRS validation to {@code GeoJsonBoundaryParser}.
 *
 * <p>Test implementations supply fixture neighbourhoods directly without HTTP.
 */
public interface BoundaryGateway {

    /**
     * Loads all neighbourhood boundaries.
     *
     * @return list of neighbourhoods; never {@code null}, may be empty if the
     *         source contains no valid polygon features
     * @throws BoundaryParseException if the source cannot be read or parsed,
     *         or if CRS is not WGS84 (EPSG:4326)
     */
    List<Neighborhood> loadNeighborhoods();
}
