package com.uai.buslines;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Test helper — builds in-memory GTFS ZIP fixtures for unit and integration tests.
 *
 * <p>The default fixture contains:
 * <ul>
 *   <li>2 routes (R001 = two directions, R002 = one direction)</li>
 *   <li>3 stops in Belo Horizonte WGS84 coordinates</li>
 *   <li>3 trips → 3 shape sequences</li>
 * </ul>
 */
public final class GtfsFixtures {

    private GtfsFixtures() {}

    // ── Default fixture content ─────────────────────────────────────────────────

    static final String ROUTES_CSV =
            "route_id,route_short_name,route_long_name,route_type\n" +
            "R001,9400,Bairro A - Centro,3\n" +
            "R002,2010,Centro - Bairro B,3\n";

    static final String TRIPS_CSV =
            "route_id,service_id,trip_id,direction_id,shape_id\n" +
            "R001,WD,T001,0,S1\n" +
            "R001,WD,T002,1,S2\n" +
            "R002,WD,T003,0,S3\n";

    static final String STOPS_CSV =
            "stop_id,stop_name,stop_lat,stop_lon\n" +
            "ST001,Ponto Centro,-19.9167,-43.9345\n" +
            "ST002,Ponto Bairro A,-19.8800,-43.9100\n" +
            "ST003,Ponto Bairro B,-19.9500,-44.0000\n";

    static final String STOP_TIMES_CSV =
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n" +
            "T001,08:00:00,08:00:00,ST001,1\n" +
            "T001,08:10:00,08:10:00,ST002,2\n" +
            "T002,09:00:00,09:00:00,ST002,1\n" +
            "T002,09:10:00,09:10:00,ST001,2\n" +
            "T003,10:00:00,10:00:00,ST001,1\n" +
            "T003,10:20:00,10:20:00,ST003,2\n";

    static final String SHAPES_CSV =
            "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\n" +
            "S1,-19.9167,-43.9345,1\n" +
            "S1,-19.8950,-43.9200,2\n" +
            "S1,-19.8800,-43.9100,3\n" +
            "S2,-19.8800,-43.9100,1\n" +
            "S2,-19.8950,-43.9200,2\n" +
            "S2,-19.9167,-43.9345,3\n" +
            "S3,-19.9167,-43.9345,1\n" +
            "S3,-19.9500,-44.0000,2\n";

    // ── Factory methods ─────────────────────────────────────────────────────────

    /** Builds the standard minimal GTFS ZIP with all required files. */
    public static byte[] minimalGtfsZip() {
        return buildZip(defaultFiles());
    }

    /**
     * Builds a GTFS ZIP with {@code overrideFile} replaced by {@code content}.
     * Useful for injecting a single bad file without changing the rest.
     */
    public static byte[] zipWithOverride(String overrideFile, String content) {
        Map<String, String> files = defaultFiles();
        files.put(overrideFile, content);
        return buildZip(files);
    }

    /**
     * Builds a GTFS ZIP with {@code excludeFile} removed.
     * Useful for testing missing-file error paths.
     */
    public static byte[] zipWithoutFile(String excludeFile) {
        Map<String, String> files = defaultFiles();
        files.remove(excludeFile);
        return buildZip(files);
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private static Map<String, String> defaultFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("routes.txt",     ROUTES_CSV);
        files.put("trips.txt",      TRIPS_CSV);
        files.put("stops.txt",      STOPS_CSV);
        files.put("stop_times.txt", STOP_TIMES_CSV);
        files.put("shapes.txt",     SHAPES_CSV);
        return files;
    }

    static byte[] buildZip(Map<String, String> files) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }
}
