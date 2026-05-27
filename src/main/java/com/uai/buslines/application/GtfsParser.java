package com.uai.buslines.application;

import com.uai.buslines.domain.model.Coordinate;
import com.uai.buslines.domain.model.GtfsDataset;
import com.uai.buslines.domain.model.GtfsParseException;
import com.uai.buslines.domain.model.GtfsRoute;
import com.uai.buslines.domain.model.GtfsRouteShape;
import com.uai.buslines.domain.model.GtfsStop;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Stateless parser that converts a raw GTFS ZIP byte array into a {@link GtfsDataset}.
 *
 * <p>Required files: {@code routes.txt}, {@code trips.txt}, {@code stops.txt},
 * {@code stop_times.txt}, {@code shapes.txt}. A {@link GtfsParseException} is thrown if
 * any required file or column is missing, or if any coordinate is outside WGS84
 * (EPSG:4326) bounds.
 *
 * <p>Lives in the application layer — depends only on domain model types, no JPA or HTTP.
 */
@Component
public class GtfsParser {

    /**
     * Small files read fully into memory. {@code stop_times.txt} is intentionally
     * excluded — it can be hundreds of MB, so it is streamed and filtered separately
     * (see {@link #parseStopTimesStreaming}) to keep the import within a small heap.
     */
    private static final Set<String> MATERIALIZED_FILES = Set.of(
            "routes.txt", "trips.txt", "stops.txt", "shapes.txt");

    private static final String STOP_TIMES_FILE = "stop_times.txt";

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Parses a GTFS ZIP and returns the assembled domain aggregate.
     *
     * @param zipContent raw bytes of the GTFS ZIP
     * @param feedEtag   ETag returned by the server (may be {@code null})
     * @return validated, assembled {@link GtfsDataset}
     * @throws GtfsParseException on any structural or coordinate error
     */
    public GtfsDataset parse(byte[] zipContent, String feedEtag) {
        Map<String, List<String>> entries = readMaterializedEntries(zipContent);

        List<GtfsRoute>               routes = parseRoutes(entries.get("routes.txt"));
        Map<String, TripInfo>         trips  = parseTrips(entries.get("trips.txt"));
        List<GtfsStop>                stops  = parseStops(entries.get("stops.txt"));
        Map<String, List<Coordinate>> shapes = parseShapes(entries.get("shapes.txt"));

        // Only the first trip per (route, direction) contributes a route shape, so we
        // only need stop_times for that small set of trips. Computing it up front lets
        // us stream stop_times and discard the ~99% of rows we never use.
        Map<String, Map<Integer, TripInfo>> routeDirFirstTrip = groupFirstTripPerDirection(trips);
        Set<String> neededTripIds = new HashSet<>();
        for (Map<Integer, TripInfo> dirTrips : routeDirFirstTrip.values()) {
            for (TripInfo trip : dirTrips.values()) {
                neededTripIds.add(trip.tripId());
            }
        }

        Map<String, List<String>> tripStopIds = parseStopTimesStreaming(zipContent, neededTripIds);

        Map<String, GtfsStop> stopById = new LinkedHashMap<>();
        for (GtfsStop s : stops) {
            stopById.put(s.stopId(), s);
        }

        List<GtfsRouteShape> routeShapes =
                buildRouteShapes(routes, routeDirFirstTrip, tripStopIds, shapes, stopById);

        return new GtfsDataset(feedEtag, Instant.now(), routes, stops, routeShapes);
    }

    // ── ZIP reading ─────────────────────────────────────────────────────────────

    private static Map<String, List<String>> readMaterializedEntries(byte[] zipContent) {
        Map<String, List<String>> contents = new LinkedHashMap<>();
        boolean stopTimesPresent = false;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (MATERIALIZED_FILES.contains(name)) {
                    contents.put(name, readLines(zis));
                } else if (STOP_TIMES_FILE.equals(name)) {
                    // Presence only — content is streamed later, never held in memory.
                    stopTimesPresent = true;
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new GtfsParseException("Failed to read GTFS ZIP: " + e.getMessage(), e);
        }

        for (String required : MATERIALIZED_FILES) {
            if (!contents.containsKey(required)) {
                throw new GtfsParseException("Required GTFS file missing from ZIP: " + required);
            }
        }
        if (!stopTimesPresent) {
            throw new GtfsParseException("Required GTFS file missing from ZIP: " + STOP_TIMES_FILE);
        }

        return contents;
    }

    private static List<String> readLines(ZipInputStream zis) throws IOException {
        byte[] bytes = zis.readAllBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);
        // Strip UTF-8 BOM if present
        if (content.startsWith("﻿")) {
            content = content.substring(1);
        }
        // Normalise CRLF → LF and filter blank lines
        return Arrays.stream(content.split("\n"))
                .map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());
    }

    // ── routes.txt ──────────────────────────────────────────────────────────────

    private static List<GtfsRoute> parseRoutes(List<String> lines) {
        requireNonEmpty(lines, "routes.txt");
        Map<String, Integer> header = parseHeader(lines.get(0), "routes.txt",
                "route_id", "route_short_name", "route_long_name");

        List<GtfsRoute> routes = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> fields = parseCsvLine(lines.get(i));
            routes.add(new GtfsRoute(
                    field(fields, header, "route_id",         "routes.txt", i),
                    field(fields, header, "route_short_name", "routes.txt", i),
                    field(fields, header, "route_long_name",  "routes.txt", i)
            ));
        }

        if (routes.isEmpty()) {
            throw new GtfsParseException("routes.txt contains no route records");
        }
        return routes;
    }

    // ── trips.txt ───────────────────────────────────────────────────────────────

    /** Internal holder for trip metadata (not exposed outside this class). */
    private record TripInfo(String tripId, String routeId, int directionId, String shapeId) {}

    private static Map<String, TripInfo> parseTrips(List<String> lines) {
        requireNonEmpty(lines, "trips.txt");
        Map<String, Integer> header = parseHeader(lines.get(0), "trips.txt",
                "route_id", "trip_id");

        // direction_id and shape_id are optional columns in the GTFS spec
        int dirIdx   = header.getOrDefault("direction_id", -1);
        int shapeIdx = header.getOrDefault("shape_id",     -1);

        Map<String, TripInfo> trips = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> fields = parseCsvLine(lines.get(i));
            String routeId  = field(fields, header, "route_id", "trips.txt", i);
            String tripId   = field(fields, header, "trip_id",  "trips.txt", i);
            int    direction = optionalInt(fields, dirIdx, 0, "direction_id", "trips.txt", i);
            String shapeId  = optionalString(fields, shapeIdx);
            trips.put(tripId, new TripInfo(tripId, routeId, direction, shapeId));
        }
        return trips;
    }

    // ── stops.txt ───────────────────────────────────────────────────────────────

    private static List<GtfsStop> parseStops(List<String> lines) {
        requireNonEmpty(lines, "stops.txt");
        Map<String, Integer> header = parseHeader(lines.get(0), "stops.txt",
                "stop_id", "stop_name", "stop_lat", "stop_lon");

        List<GtfsStop> stops = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> fields = parseCsvLine(lines.get(i));
            String stopId = field(fields, header, "stop_id",   "stops.txt", i);
            String name   = field(fields, header, "stop_name", "stops.txt", i);
            double lat    = parseCoord(field(fields, header, "stop_lat", "stops.txt", i),
                    "stop_lat", "stops.txt", i);
            double lon    = parseCoord(field(fields, header, "stop_lon", "stops.txt", i),
                    "stop_lon", "stops.txt", i);
            validateWgs84(lat, lon, "stops.txt", i);
            stops.add(new GtfsStop(stopId, name, lat, lon));
        }
        return stops;
    }

    // ── stop_times.txt ──────────────────────────────────────────────────────────

    /**
     * Streams {@code stop_times.txt} from the ZIP and returns
     * {@code trip_id → ordered list of stop_ids} (ordered by stop_sequence),
     * keeping only the trips in {@code neededTripIds}.
     *
     * <p>The file is read line-by-line straight from the ZIP entry stream and never
     * materialised in full — essential because {@code stop_times.txt} can exceed
     * 500 MB. Filtering to the handful of representative trips keeps the resulting
     * map tiny.
     */
    private static Map<String, List<String>> parseStopTimesStreaming(
            byte[] zipContent, Set<String> neededTripIds) {

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (STOP_TIMES_FILE.equals(entry.getName())) {
                    return streamStopTimes(zis, neededTripIds);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new GtfsParseException("Failed to read " + STOP_TIMES_FILE + ": " + e.getMessage(), e);
        }
        throw new GtfsParseException("Required GTFS file missing from ZIP: " + STOP_TIMES_FILE);
    }

    private static Map<String, List<String>> streamStopTimes(
            ZipInputStream zis, Set<String> neededTripIds) throws IOException {

        // Reader over the current ZIP entry; readLine() returns null at entry end.
        // Not closed here on purpose — closing it would close the shared ZipInputStream.
        BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));

        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new GtfsParseException(STOP_TIMES_FILE + " is empty");
        }
        if (headerLine.startsWith("﻿")) {
            headerLine = headerLine.substring(1); // strip UTF-8 BOM
        }
        Map<String, Integer> header = parseHeader(headerLine, STOP_TIMES_FILE,
                "trip_id", "stop_id", "stop_sequence");

        // TreeMap keeps stop_sequence ascending; only needed trips are retained.
        Map<String, TreeMap<Integer, String>> raw = new HashMap<>();
        String line;
        int lineNum = 1;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isBlank()) {
                continue;
            }
            List<String> fields = parseCsvLine(line);
            String tripId = field(fields, header, "trip_id", STOP_TIMES_FILE, lineNum);
            if (!neededTripIds.contains(tripId)) {
                continue;
            }
            String stopId = field(fields, header, "stop_id", STOP_TIMES_FILE, lineNum);
            int seq = parseIntField(
                    field(fields, header, "stop_sequence", STOP_TIMES_FILE, lineNum),
                    "stop_sequence", STOP_TIMES_FILE, lineNum);
            raw.computeIfAbsent(tripId, k -> new TreeMap<>()).put(seq, stopId);
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        raw.forEach((tripId, seqMap) -> result.put(tripId, new ArrayList<>(seqMap.values())));
        return result;
    }

    // ── shapes.txt ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code shape_id → ordered list of Coordinates} (ordered by shape_pt_sequence).
     */
    private static Map<String, List<Coordinate>> parseShapes(List<String> lines) {
        requireNonEmpty(lines, "shapes.txt");
        Map<String, Integer> header = parseHeader(lines.get(0), "shapes.txt",
                "shape_id", "shape_pt_lat", "shape_pt_lon", "shape_pt_sequence");

        Map<String, TreeMap<Integer, Coordinate>> raw = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> fields = parseCsvLine(lines.get(i));
            String shapeId = field(fields, header, "shape_id",           "shapes.txt", i);
            double lat     = parseCoord(field(fields, header, "shape_pt_lat", "shapes.txt", i),
                    "shape_pt_lat", "shapes.txt", i);
            double lon     = parseCoord(field(fields, header, "shape_pt_lon", "shapes.txt", i),
                    "shape_pt_lon", "shapes.txt", i);
            int seq        = parseIntField(
                    field(fields, header, "shape_pt_sequence", "shapes.txt", i),
                    "shape_pt_sequence", "shapes.txt", i);
            validateWgs84(lat, lon, "shapes.txt", i);
            raw.computeIfAbsent(shapeId, k -> new TreeMap<>()).put(seq, new Coordinate(lat, lon));
        }

        Map<String, List<Coordinate>> result = new LinkedHashMap<>();
        raw.forEach((shapeId, seqMap) -> result.put(shapeId, new ArrayList<>(seqMap.values())));
        return result;
    }

    // ── Route-shape assembly ────────────────────────────────────────────────────

    /** Groups trips: routeId → directionId → first {@link TripInfo} seen (insertion order). */
    private static Map<String, Map<Integer, TripInfo>> groupFirstTripPerDirection(
            Map<String, TripInfo> trips) {
        Map<String, Map<Integer, TripInfo>> routeDirFirstTrip = new LinkedHashMap<>();
        for (TripInfo trip : trips.values()) {
            routeDirFirstTrip
                    .computeIfAbsent(trip.routeId(), k -> new LinkedHashMap<>())
                    .putIfAbsent(trip.directionId(), trip);   // first one wins per direction
        }
        return routeDirFirstTrip;
    }

    private static List<GtfsRouteShape> buildRouteShapes(
            List<GtfsRoute> routes,
            Map<String, Map<Integer, TripInfo>> routeDirFirstTrip,
            Map<String, List<String>> tripStopIds,
            Map<String, List<Coordinate>> shapes,
            Map<String, GtfsStop> stopById) {

        List<GtfsRouteShape> result = new ArrayList<>();
        for (GtfsRoute route : routes) {
            Map<Integer, TripInfo> dirTrips = routeDirFirstTrip.get(route.routeId());
            if (dirTrips == null) {
                continue; // no trips for this route — skip silently
            }
            for (Map.Entry<Integer, TripInfo> entry : dirTrips.entrySet()) {
                int      direction = entry.getKey();
                TripInfo trip      = entry.getValue();

                List<Coordinate> coords = trip.shapeId() != null
                        ? shapes.getOrDefault(trip.shapeId(), List.of())
                        : List.of();

                List<GtfsStop> orderedStops = new ArrayList<>();
                List<String>   stopIds      = tripStopIds.get(trip.tripId());
                if (stopIds != null) {
                    for (String stopId : stopIds) {
                        GtfsStop stop = stopById.get(stopId);
                        if (stop != null) {
                            orderedStops.add(stop);
                        }
                    }
                }

                result.add(new GtfsRouteShape(route.routeId(), direction, coords, orderedStops));
            }
        }
        return result;
    }

    // ── CSV helpers ─────────────────────────────────────────────────────────────

    /**
     * Parses a single CSV line, respecting RFC 4180 double-quoted fields.
     * Package-private for testability.
     */
    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++; // skip escaped double-quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    /**
     * Parses the header row and validates that all required columns are present.
     *
     * @return column-name → zero-based index map
     * @throws GtfsParseException if any required column is absent
     */
    private static Map<String, Integer> parseHeader(
            String headerLine, String filename, String... required) {
        List<String> headers = parseCsvLine(headerLine);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            map.put(headers.get(i).trim(), i);
        }
        for (String req : required) {
            if (!map.containsKey(req)) {
                throw new GtfsParseException(
                        "Required column '" + req + "' is missing from " + filename);
            }
        }
        return map;
    }

    /** Returns the (trimmed) field value for a required column. */
    private static String field(
            List<String> fields, Map<String, Integer> header,
            String column, String filename, int lineNum) {
        int idx = header.get(column);
        if (idx >= fields.size()) {
            throw new GtfsParseException(
                    "Row " + lineNum + " in " + filename + " is missing required column '"
                            + column + "' (row has " + fields.size()
                            + " fields, column expected at index " + idx + ")");
        }
        return fields.get(idx).trim();
    }

    /**
     * Returns the integer value at an optional column index.
     * Falls back to {@code defaultValue} when the column is absent or the row is too short.
     */
    private static int optionalInt(
            List<String> fields, int columnIdx, int defaultValue,
            String columnName, String filename, int lineNum) {
        if (columnIdx < 0 || columnIdx >= fields.size()) {
            return defaultValue;
        }
        String raw = fields.get(columnIdx).trim();
        if (raw.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new GtfsParseException(
                    "Invalid integer value '" + raw + "' in optional column '"
                            + columnName + "' at " + filename + " line " + lineNum);
        }
    }

    /**
     * Returns the string value at an optional column index; {@code null} when absent or blank.
     */
    private static String optionalString(List<String> fields, int columnIdx) {
        if (columnIdx < 0 || columnIdx >= fields.size()) return null;
        String raw = fields.get(columnIdx).trim();
        return raw.isEmpty() ? null : raw;
    }

    private static double parseCoord(String value, String column, String filename, int lineNum) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new GtfsParseException(
                    "Invalid coordinate value '" + value + "' in column '" + column
                            + "' at " + filename + " line " + lineNum);
        }
    }

    private static int parseIntField(String value, String column, String filename, int lineNum) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new GtfsParseException(
                    "Invalid integer value '" + value + "' in column '" + column
                            + "' at " + filename + " line " + lineNum);
        }
    }

    private static void validateWgs84(double lat, double lon, String filename, int lineNum) {
        if (lat < -90.0 || lat > 90.0) {
            throw new GtfsParseException(
                    "Coordinate out of WGS84 range in " + filename + " line " + lineNum
                            + ": latitude " + lat + " is outside [-90, 90]");
        }
        if (lon < -180.0 || lon > 180.0) {
            throw new GtfsParseException(
                    "Coordinate out of WGS84 range in " + filename + " line " + lineNum
                            + ": longitude " + lon + " is outside [-180, 180]");
        }
    }

    private static void requireNonEmpty(List<String> lines, String filename) {
        if (lines == null || lines.isEmpty()) {
            throw new GtfsParseException(filename + " is empty");
        }
    }
}
