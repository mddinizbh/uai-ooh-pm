package com.uai.buslines.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.domain.model.LineDetail;
import com.uai.buslines.domain.model.LineSummary;
import com.uai.buslines.domain.port.in.QueryNetworkUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public REST controller for line search and line detail queries.
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@code GET /api/lines?q=} — search lines by number or name</li>
 *   <li>{@code GET /api/lines/{id}} — full line detail with route geometry and stops</li>
 * </ul>
 *
 * <p>Route geometry is returned as a parsed JSON object (not a string), so the SPA
 * can pass it directly to MapLibre without a second {@code JSON.parse()}.
 *
 * <p>No authentication required (public, single-tenant — ADR-004).
 * Errors handled by {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/lines")
@Tag(name = "Lines", description = "Bus line search and detail endpoints")
public class LineController {

    private final QueryNetworkUseCase useCase;
    private final ObjectMapper objectMapper;

    public LineController(QueryNetworkUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    /**
     * Searches lines by number ({@code short_name}) or name ({@code long_name}).
     * Case-insensitive substring match. An empty query returns all lines.
     *
     * @param q search term; optional, defaults to empty
     * @return 200 with a list of matching line summaries (may be empty)
     */
    @GetMapping
    @Operation(
            summary = "Search lines",
            description = "Case-insensitive substring search against the line number " +
                    "(short_name) and full name (long_name). Empty query returns all lines.")
    @ApiResponse(responseCode = "200", description = "Search results (may be empty)")
    public ResponseEntity<List<LineSummaryResponse>> searchLines(
            @RequestParam(name = "q", required = false, defaultValue = "")
            @Parameter(description = "Search term — line number or partial name") String q) {
        List<LineSummary> results = useCase.searchLines(q);
        List<LineSummaryResponse> response = results.stream()
                .map(LineSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Returns full detail for a line: route shapes (GeoJSON) and ordered stops.
     *
     * @param id line database primary key
     * @return 200 with line detail, or 404 if the line does not exist
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "Line detail",
            description = "Returns the line summary, its directional route shapes as " +
                    "GeoJSON LineString objects, and ordered stops (currently always empty).")
    @ApiResponse(responseCode = "200", description = "Line detail returned")
    @ApiResponse(responseCode = "404", description = "Line not found in active dataset")
    public ResponseEntity<LineDetailResponse> lineDetail(
            @PathVariable @Parameter(description = "Line database id") long id) {
        LineDetail detail = useCase.lineDetail(id);
        return ResponseEntity.ok(LineDetailResponse.from(detail, objectMapper));
    }

    // ── Response DTOs ──────────────────────────────────────────────────────────

    /** DTO for a single directional route shape. Geometry is a parsed JSON object. */
    public record LineShapeResponse(long id, int direction, JsonNode geometry) {}

    /** DTO for a single stop. */
    public record StopResponse(long id, String name, double lat, double lon) {}

    /** DTO for full line detail. */
    public record LineDetailResponse(
            LineSummaryResponse line,
            List<LineShapeResponse> shapes,
            List<StopResponse> stops) {

        static LineDetailResponse from(LineDetail detail, ObjectMapper mapper) {
            List<LineShapeResponse> shapes = detail.shapes().stream()
                    .map(s -> {
                        JsonNode geometry = parseJson(s.geometryGeoJson(), mapper);
                        return new LineShapeResponse(s.id(), s.direction(), geometry);
                    })
                    .toList();

            List<StopResponse> stops = detail.stops().stream()
                    .map(s -> new StopResponse(s.id(), s.name(), s.lat(), s.lon()))
                    .toList();

            return new LineDetailResponse(LineSummaryResponse.from(detail.line()), shapes, stops);
        }

        private static JsonNode parseJson(String json, ObjectMapper mapper) {
            try {
                return mapper.readTree(json);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid GeoJSON in database: " + json, e);
            }
        }
    }
}
