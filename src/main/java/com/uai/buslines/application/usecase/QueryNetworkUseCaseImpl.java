package com.uai.buslines.application.usecase;

import com.uai.buslines.domain.model.*;
import com.uai.buslines.domain.port.in.QueryNetworkUseCase;
import com.uai.buslines.domain.port.out.NetworkQueryRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Application use case — read-only queries over the active GTFS dataset.
 *
 * <p>Implements {@link QueryNetworkUseCase} (driving port) by delegating persistence
 * queries to {@link NetworkQueryRepository} (driven port). Business logic here is
 * minimal: obtain the active version, delegate to the repository, and throw
 * {@link NotFoundException} for missing resources.
 *
 * <p>No framework, JPA, or HTTP types (RULE-JAVA-01).
 * No {@code tenant_id} (ADR-004).
 */
@Component
public class QueryNetworkUseCaseImpl implements QueryNetworkUseCase {

    private final NetworkQueryRepository queryRepo;

    public QueryNetworkUseCaseImpl(NetworkQueryRepository queryRepo) {
        this.queryRepo = queryRepo;
    }

    // ── QueryNetworkUseCase ────────────────────────────────────────────────────

    @Override
    public List<NeighborhoodSummary> listNeighborhoods() {
        long version = queryRepo.activeVersion();
        if (version == 0L) {
            return List.of();
        }
        return queryRepo.listNeighborhoods(version);
    }

    @Override
    public NeighborhoodLines linesByNeighborhood(long neighborhoodId) {
        long version = queryRepo.activeVersion();
        if (version == 0L) {
            throw new NotFoundException("Neighborhood not found: " + neighborhoodId);
        }
        return queryRepo.linesByNeighborhood(neighborhoodId, version)
                .orElseThrow(() -> new NotFoundException(
                        "Neighborhood not found: " + neighborhoodId));
    }

    @Override
    public List<LineSummary> searchLines(String query) {
        long version = queryRepo.activeVersion();
        if (version == 0L) {
            return List.of();
        }
        return queryRepo.searchLines(query, version);
    }

    @Override
    public LineDetail lineDetail(long lineId) {
        long version = queryRepo.activeVersion();
        if (version == 0L) {
            throw new NotFoundException("Line not found: " + lineId);
        }
        return queryRepo.lineDetail(lineId, version)
                .orElseThrow(() -> new NotFoundException("Line not found: " + lineId));
    }
}
