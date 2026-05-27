package com.uai.buslines.application;

import com.uai.buslines.domain.model.BoundaryParseException;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

/**
 * Reprojects boundary coordinates to WGS84 (EPSG:4326) at import time.
 *
 * <p>PBH boundary sources are published in projected CRS (WGS84 / UTM zone 23S,
 * EPSG:32723), but the classifier and the rest of the system operate in WGS84
 * lat/lon. This converts an instance is built once per import from the GeoJSON
 * CRS member and applied to every coordinate (ADR-003 — geospatial work happens
 * at import, not at query time).
 *
 * <p>Supported source CRS: WGS84 (EPSG:4326 / CRS84 — identity, no transform) and
 * WGS84/UTM north &amp; south zones (EPSG:326xx / 327xx). Any other CRS is rejected
 * with a {@link BoundaryParseException} so a wrong projection fails loudly rather
 * than silently producing garbage coordinates.
 */
final class CrsReprojector {

    private static final CRSFactory CRS_FACTORY = new CRSFactory();
    private static final CoordinateTransformFactory TRANSFORM_FACTORY =
            new CoordinateTransformFactory();
    private static final CoordinateReferenceSystem WGS84 =
            CRS_FACTORY.createFromParameters("WGS84", "+proj=longlat +datum=WGS84 +no_defs");

    /** {@code null} when the source is already WGS84 (no transform needed). */
    private final CoordinateTransform transform;

    private CrsReprojector(CoordinateTransform transform) {
        this.transform = transform;
    }

    /** A reprojector that passes coordinates through unchanged (source already WGS84). */
    static CrsReprojector identity() {
        return new CrsReprojector(null);
    }

    /**
     * Builds a reprojector for the given EPSG code.
     *
     * @throws BoundaryParseException if the CRS is not WGS84 or a WGS84/UTM zone
     */
    static CrsReprojector forEpsg(int epsgCode) {
        if (epsgCode == 4326) {
            return identity();
        }
        String params = utmProjParams(epsgCode);
        CoordinateReferenceSystem source = CRS_FACTORY.createFromParameters("EPSG:" + epsgCode, params);
        return new CrsReprojector(TRANSFORM_FACTORY.createTransform(source, WGS84));
    }

    boolean isIdentity() {
        return transform == null;
    }

    /**
     * Transforms a projected (x=easting, y=northing) coordinate to WGS84
     * (lon, lat). Returns the input unchanged when this is an identity reprojector.
     */
    double[] toWgs84(double x, double y) {
        if (transform == null) {
            return new double[] {x, y};
        }
        ProjCoordinate result = new ProjCoordinate();
        transform.transform(new ProjCoordinate(x, y), result);
        return new double[] {result.x, result.y};
    }

    private static String utmProjParams(int epsgCode) {
        if (epsgCode >= 32601 && epsgCode <= 32660) {
            return "+proj=utm +zone=" + (epsgCode - 32600) + " +datum=WGS84 +units=m +no_defs";
        }
        if (epsgCode >= 32701 && epsgCode <= 32760) {
            return "+proj=utm +zone=" + (epsgCode - 32700) + " +south +datum=WGS84 +units=m +no_defs";
        }
        throw new BoundaryParseException(
                "Unsupported boundary CRS EPSG:" + epsgCode
                + ". Only WGS84 (EPSG:4326) and WGS84/UTM zones (EPSG:326xx, 327xx) are supported.");
    }
}
