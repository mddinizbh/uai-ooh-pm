package com.uai.buslines.adapter.out.persistence;

import com.uai.buslines.domain.model.*;
import com.uai.buslines.domain.port.out.NetworkQueryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JPA implementation of {@link NetworkQueryRepository}.
 *
 * <p>All reads target the currently active dataset version; callers obtain the
 * active version via {@link #activeVersion()} and pass it to the other methods.
 *
 * <p>No {@code tenant_id} anywhere (ADR-004). No PostGIS types (ADR-003).
 * Geometry is returned as raw GeoJSON strings stored in {@code jsonb} columns.
 *
 * <h3>orderedStops is not persisted</h3>
 * {@code line_shape} has no stop-sequence column. {@link #lineDetail} returns an
 * empty stops list — a follow-up migration must add a {@code line_stop} junction
 * table before ordered stops can be served.
 */
@Component
@Transactional(readOnly = true)
class JpaNetworkQueryRepository implements NetworkQueryRepository {

    private static final String STATUS_ACTIVE = "ACTIVE";

    // Relation string constants — must match the CHECK constraint in the DDL
    private static final String PASSES_THROUGH = "PASSES_THROUGH";
    private static final String DEPARTS_FROM   = "DEPARTS_FROM";
    private static final String ARRIVES_AT     = "ARRIVES_AT";

    private final DatasetVersionJpaRepository  versionRepo;
    private final NeighborhoodJpaRepository    neighborhoodRepo;
    private final LineJpaRepository            lineRepo;
    private final LineShapeJpaRepository       lineShapeRepo;
    private final LineNeighborhoodJpaRepository lineNeighborhoodRepo;

    JpaNetworkQueryRepository(
            DatasetVersionJpaRepository versionRepo,
            NeighborhoodJpaRepository neighborhoodRepo,
            LineJpaRepository lineRepo,
            LineShapeJpaRepository lineShapeRepo,
            LineNeighborhoodJpaRepository lineNeighborhoodRepo) {
        this.versionRepo          = versionRepo;
        this.neighborhoodRepo     = neighborhoodRepo;
        this.lineRepo             = lineRepo;
        this.lineShapeRepo        = lineShapeRepo;
        this.lineNeighborhoodRepo = lineNeighborhoodRepo;
    }

    // ── NetworkQueryRepository ─────────────────────────────────────────────────

    @Override
    public long activeVersion() {
        return versionRepo.findFirstByStatusOrderByVersionDesc(STATUS_ACTIVE)
                .map(DatasetVersionEntity::getVersion)
                .orElse(0L);
    }

    @Override
    public Optional<Instant> activeImportedAt() {
        return versionRepo.findFirstByStatusOrderByVersionDesc(STATUS_ACTIVE)
                .map(DatasetVersionEntity::getImportedAt);
    }

    @Override
    public List<NeighborhoodSummary> listNeighborhoods(long version) {
        List<NeighborhoodEntity> neighborhoods =
                neighborhoodRepo.findByDatasetVersionOrderByNameAsc(version);
        if (neighborhoods.isEmpty()) {
            return List.of();
        }

        // Load all line_neighborhood rows for this version to compute counts in one pass
        List<LineNeighborhoodEntity> allRelations =
                lineNeighborhoodRepo.findByDatasetVersion(version);

        // countsByNeighborhood: neighborhoodId → relation → count
        Map<Long, Map<String, Long>> countsByNeighborhood = allRelations.stream()
                .collect(Collectors.groupingBy(
                        LineNeighborhoodEntity::getNeighborhoodId,
                        Collectors.groupingBy(
                                LineNeighborhoodEntity::getRelation,
                                Collectors.counting())));

        return neighborhoods.stream()
                .map(n -> {
                    Map<String, Long> counts =
                            countsByNeighborhood.getOrDefault(n.getId(), Map.of());
                    return new NeighborhoodSummary(
                            n.getId(),
                            n.getName(),
                            counts.getOrDefault(PASSES_THROUGH, 0L).intValue(),
                            counts.getOrDefault(DEPARTS_FROM,   0L).intValue(),
                            counts.getOrDefault(ARRIVES_AT,     0L).intValue());
                })
                .toList();
    }

    @Override
    public Optional<NeighborhoodLines> linesByNeighborhood(long neighborhoodId, long version) {
        Optional<NeighborhoodEntity> neighborhoodOpt =
                neighborhoodRepo.findByIdAndDatasetVersion(neighborhoodId, version);
        if (neighborhoodOpt.isEmpty()) {
            return Optional.empty();
        }
        NeighborhoodEntity neighborhood = neighborhoodOpt.get();

        // Load all relation rows for this neighbourhood
        List<LineNeighborhoodEntity> relations =
                lineNeighborhoodRepo.findByNeighborhoodIdAndDatasetVersion(neighborhoodId, version);

        // Group line_ids by relation type (a line may appear in multiple buckets)
        Map<String, List<Long>> lineIdsByRelation = relations.stream()
                .collect(Collectors.groupingBy(
                        LineNeighborhoodEntity::getRelation,
                        Collectors.mapping(LineNeighborhoodEntity::getLineId, Collectors.toList())));

        // Gather all distinct line IDs we need to load
        Set<Long> allLineIds = relations.stream()
                .map(LineNeighborhoodEntity::getLineId)
                .collect(Collectors.toSet());

        // Load line entities in one query; build a map for O(1) lookup
        Map<Long, LineSummary> lineById = lineRepo
                .findByIdInAndDatasetVersion(allLineIds, version)
                .stream()
                .collect(Collectors.toMap(
                        LineEntity::getId,
                        e -> new LineSummary(e.getId(), e.getShortName(), e.getLongName())));

        return Optional.of(new NeighborhoodLines(
                neighborhood.getId(),
                neighborhood.getName(),
                neighborhood.getBoundaryGeoJson(),
                toLineSummaries(lineIdsByRelation.getOrDefault(PASSES_THROUGH, List.of()), lineById),
                toLineSummaries(lineIdsByRelation.getOrDefault(DEPARTS_FROM,   List.of()), lineById),
                toLineSummaries(lineIdsByRelation.getOrDefault(ARRIVES_AT,     List.of()), lineById)));
    }

    @Override
    public List<LineSummary> searchLines(String query, long version) {
        List<LineEntity> lines;
        if (query == null || query.isBlank()) {
            lines = lineRepo.findByDatasetVersionOrderByShortNameAsc(version);
        } else {
            lines = lineRepo.searchByNameOrNumber(query.trim(), version);
        }
        return lines.stream()
                .map(l -> new LineSummary(l.getId(), l.getShortName(), l.getLongName()))
                .toList();
    }

    @Override
    public Optional<LineDetail> lineDetail(long lineId, long version) {
        Optional<LineEntity> lineOpt = lineRepo.findByIdAndDatasetVersion(lineId, version);
        if (lineOpt.isEmpty()) {
            return Optional.empty();
        }
        LineEntity line = lineOpt.get();

        List<LineShape> shapes = lineShapeRepo
                .findByLineIdAndDatasetVersionOrderByDirectionAsc(lineId, version)
                .stream()
                .map(s -> new LineShape(s.getId(), s.getDirection(), s.getGeometryGeoJson()))
                .toList();

        LineSummary summary = new LineSummary(line.getId(), line.getShortName(), line.getLongName());
        // Ordered stops are not persisted yet — follow-up migration required
        return Optional.of(new LineDetail(summary, shapes, List.of()));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private List<LineSummary> toLineSummaries(List<Long> lineIds,
                                               Map<Long, LineSummary> lineById) {
        return lineIds.stream()
                .map(lineById::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LineSummary::shortName))
                .toList();
    }
}
