package com.uai.buslines.adapter.in.web;

import com.uai.buslines.domain.port.out.NetworkQueryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Public REST controller for dataset metadata.
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@code GET /api/meta} — data source attribution (CC-BY) and last import
 *   timestamp</li>
 * </ul>
 *
 * <p>No authentication required (public, single-tenant — ADR-004).
 */
@RestController
@RequestMapping("/api/meta")
@Tag(name = "Meta", description = "Dataset metadata and attribution")
public class MetaController {

    static final String ATTRIBUTION =
            "Dados abertos PBH/BHTRANS — GTFS e limites de bairros — Licença CC-BY 4.0. " +
            "Fonte: dados.pbh.gov.br e geoportal.pbh.gov.br";

    private final NetworkQueryRepository queryRepo;

    public MetaController(NetworkQueryRepository queryRepo) {
        this.queryRepo = queryRepo;
    }

    /**
     * Returns dataset metadata: source attribution and the timestamp of the last
     * successful import.
     *
     * @return 200 with attribution and last-import timestamp (may be {@code null}
     *         if no import has run yet)
     */
    @GetMapping
    @Operation(
            summary = "Dataset metadata",
            description = "Returns CC-BY attribution and the timestamp of the last " +
                    "successful GTFS import. lastImportedAt is null if no import has run.")
    @ApiResponse(responseCode = "200", description = "Metadata returned")
    public ResponseEntity<MetaResponse> meta() {
        Instant lastImportedAt = queryRepo.activeImportedAt().orElse(null);
        return ResponseEntity.ok(new MetaResponse(lastImportedAt, ATTRIBUTION));
    }

    /** DTO for dataset metadata. */
    public record MetaResponse(Instant lastImportedAt, String attribution) {}
}
