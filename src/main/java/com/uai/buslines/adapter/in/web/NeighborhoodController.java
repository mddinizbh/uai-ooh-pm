package com.uai.buslines.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.domain.model.NeighborhoodLines;
import com.uai.buslines.domain.model.NeighborhoodSummary;
import com.uai.buslines.domain.port.in.QueryNetworkUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public REST controller for neighbourhood queries.
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@code GET /api/neighborhoods} — list all neighbourhoods with per-relation counts</li>
 *   <li>{@code GET /api/neighborhoods/{id}/lines} — lines grouped by relation for one neighbourhood</li>
 * </ul>
 *
 * <p>No authentication required (public, single-tenant — ADR-004).
 * Errors handled by {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/neighborhoods")
@Tag(name = "Neighbourhoods", description = "Neighbourhood list and line-grouping endpoints")
public class NeighborhoodController {

    private final QueryNetworkUseCase useCase;
    private final ObjectMapper objectMapper;

    public NeighborhoodController(QueryNetworkUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns all neighbourhoods in the active dataset, sorted by name, with
     * per-relation line counts.
     *
     * @return 200 with a list of neighbourhood summaries (may be empty)
     */
    @GetMapping
    @Operation(
            summary = "List all neighbourhoods",
            description = "Returns all neighbourhoods sorted by name with line counts per " +
                    "relation type (passes-through, departs-from, arrives-at).")
    @ApiResponse(responseCode = "200", description = "List returned (may be empty if no import has run)")
    public ResponseEntity<List<NeighborhoodResponse>> listNeighborhoods() {
        List<NeighborhoodSummary> summaries = useCase.listNeighborhoods();
        List<NeighborhoodResponse> response = summaries.stream()
                .map(NeighborhoodResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Returns lines for a neighbourhood grouped into the three relation buckets.
     *
     * @param id neighbourhood database primary key
     * @return 200 with grouped lines, or 404 if the neighbourhood does not exist
     */
    @GetMapping("/{id}/lines")
    @Operation(
            summary = "Lines by neighbourhood",
            description = "Returns lines serving the neighbourhood grouped into " +
                    "passesThrough / departsFrom / arrivesAt. A line may appear in " +
                    "more than one bucket.")
    @ApiResponse(responseCode = "200", description = "Lines returned")
    @ApiResponse(responseCode = "404", description = "Neighbourhood not found in active dataset")
    public ResponseEntity<NeighborhoodLinesResponse> linesByNeighborhood(
            @PathVariable @Parameter(description = "Neighbourhood database id") long id) {
        NeighborhoodLines lines = useCase.linesByNeighborhood(id);
        return ResponseEntity.ok(NeighborhoodLinesResponse.from(lines, objectMapper));
    }

    // ── Response DTOs (inner, package-visible for tests) ──────────────────────

    /** DTO for a neighbourhood summary with per-relation line counts. */
    public record NeighborhoodResponse(
            long id,
            String name,
            int passesThroughCount,
            int departsFromCount,
            int arrivesAtCount) {

        static NeighborhoodResponse from(NeighborhoodSummary s) {
            return new NeighborhoodResponse(
                    s.id(), s.name(),
                    s.passesThroughCount(), s.departsFromCount(), s.arrivesAtCount());
        }
    }

    /** DTO for the grouped-lines response of a single neighbourhood. */
    public record NeighborhoodLinesResponse(
            long neighborhoodId,
            String neighborhoodName,
            JsonNode boundaryGeoJson,
            List<LineSummaryResponse> passesThrough,
            List<LineSummaryResponse> departsFrom,
            List<LineSummaryResponse> arrivesAt) {

        static NeighborhoodLinesResponse from(NeighborhoodLines nl, ObjectMapper mapper) {
            JsonNode boundary = parseJson(nl.boundaryGeoJson(), mapper);
            return new NeighborhoodLinesResponse(
                    nl.neighborhoodId(),
                    nl.neighborhoodName(),
                    boundary,
                    nl.passesThrough().stream().map(LineSummaryResponse::from).toList(),
                    nl.departsFrom().stream().map(LineSummaryResponse::from).toList(),
                    nl.arrivesAt().stream().map(LineSummaryResponse::from).toList());
        }

        private static JsonNode parseJson(String json, ObjectMapper mapper) {
            try {
                return mapper.readTree(json);
            } catch (Exception e) {
                throw new IllegalStateException("Invalid GeoJSON boundary in database: " + json, e);
            }
        }
    }
}
