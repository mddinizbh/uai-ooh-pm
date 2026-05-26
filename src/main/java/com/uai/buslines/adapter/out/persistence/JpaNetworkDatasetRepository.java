package com.uai.buslines.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uai.buslines.domain.model.*;
import com.uai.buslines.domain.port.out.NetworkDatasetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JPA implementation of {@link NetworkDatasetRepository}.
 *
 * <h3>Transactional dataset swap</h3>
 * {@link #replaceWith(GtfsDataset)} executes entirely within a single Spring transaction:
 * <ol>
 *   <li>Insert a new {@code dataset_version} row with status {@code IMPORTING}.</li>
 *   <li>Persist all {@code neighborhood}, {@code line}, {@code line_shape}, {@code stop},
 *       and {@code line_neighborhood} rows tagged with the new version.</li>
 *   <li>Flip the new version status to {@code ACTIVE}.</li>
 *   <li>Archive all previously {@code ACTIVE} versions.</li>
 * </ol>
 * If any step throws, the whole transaction rolls back and the previously active
 * dataset remains active and serving.
 *
 * <p>No {@code tenant_id} anywhere (ADR-004). No PostGIS types (ADR-003).
 */
@Component
class JpaNetworkDatasetRepository implements NetworkDatasetRepository {

    static final String STATUS_IMPORTING = "IMPORTING";
    static final String STATUS_ACTIVE    = "ACTIVE";
    static final String STATUS_ARCHIVED  = "ARCHIVED";

    private final DatasetVersionJpaRepository versionRepo;
    private final NeighborhoodJpaRepository   neighborhoodRepo;
    private final LineJpaRepository           lineRepo;
    private final LineShapeJpaRepository      lineShapeRepo;
    private final StopJpaRepository           stopRepo;
    private final LineNeighborhoodJpaRepository lineNeighborhoodRepo;
    private final LineShapeMapper             lineShapeMapper;

    JpaNetworkDatasetRepository(
            DatasetVersionJpaRepository versionRepo,
            NeighborhoodJpaRepository neighborhoodRepo,
            LineJpaRepository lineRepo,
            LineShapeJpaRepository lineShapeRepo,
            StopJpaRepository stopRepo,
            LineNeighborhoodJpaRepository lineNeighborhoodRepo,
            ObjectMapper objectMapper) {
        this.versionRepo          = versionRepo;
        this.neighborhoodRepo     = neighborhoodRepo;
        this.lineRepo             = lineRepo;
        this.lineShapeRepo        = lineShapeRepo;
        this.stopRepo             = stopRepo;
        this.lineNeighborhoodRepo = lineNeighborhoodRepo;
        this.lineShapeMapper      = new LineShapeMapper(objectMapper);
    }

    // ── NetworkDatasetRepository ───────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public long currentVersion() {
        return versionRepo.findFirstByStatusOrderByVersionDesc(STATUS_ACTIVE)
                .map(DatasetVersionEntity::getVersion)
                .orElse(0L);
    }

    @Override
    @Transactional(readOnly = true)
    public String activeEtag() {
        return versionRepo.findFirstByStatusOrderByVersionDesc(STATUS_ACTIVE)
                .map(DatasetVersionEntity::getGtfsSourceEtag)
                .orElse(null);
    }

    @Override
    @Transactional
    public void replaceWith(GtfsDataset dataset) {
        // 1. Create new version in IMPORTING state
        DatasetVersionEntity newVersion = versionRepo.save(
                new DatasetVersionEntity(dataset.fetchedAt(), dataset.feedEtag(), STATUS_IMPORTING));
        long versionId = newVersion.getVersion();

        // 2. Persist neighbourhoods → name→id map for FK resolution
        Map<String, Long> neighborhoodIdByName = persistNeighborhoods(dataset.neighborhoods(), versionId);

        // 3. Persist lines → routeId→lineId map for FK resolution
        Map<String, Long> lineIdByRouteId = persistLines(dataset.routes(), versionId);

        // 4. Persist line shapes (use lineId map for FK)
        persistLineShapes(dataset.routeShapes(), lineIdByRouteId, versionId);

        // 5. Persist stops (global, no FK to line)
        persistStops(dataset.stops(), versionId);

        // 6. Persist line-neighbourhood classifications (use both maps for FKs)
        persistLineNeighborhoods(dataset.lineRelations(), lineIdByRouteId, neighborhoodIdByName, versionId);

        // 7. Activate new version (atomic flip: IMPORTING → ACTIVE)
        newVersion.setStatus(STATUS_ACTIVE);
        versionRepo.save(newVersion);

        // 8. Archive all previously ACTIVE versions (should be exactly 0 or 1)
        versionRepo.updateStatusExcluding(STATUS_ACTIVE, STATUS_ARCHIVED, versionId);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private Map<String, Long> persistNeighborhoods(List<Neighborhood> neighborhoods, long versionId) {
        List<NeighborhoodEntity> entities = neighborhoods.stream()
                .map(n -> NeighborhoodMapper.toEntity(n, versionId))
                .toList();
        List<NeighborhoodEntity> saved = neighborhoodRepo.saveAll(entities);
        return saved.stream()
                .collect(Collectors.toMap(NeighborhoodEntity::getName, NeighborhoodEntity::getId));
    }

    private Map<String, Long> persistLines(List<GtfsRoute> routes, long versionId) {
        List<LineEntity> entities = routes.stream()
                .map(r -> LineMapper.toEntity(r, versionId))
                .toList();
        List<LineEntity> saved = lineRepo.saveAll(entities);
        return saved.stream()
                .collect(Collectors.toMap(LineEntity::getGtfsRouteId, LineEntity::getId));
    }

    private void persistLineShapes(List<GtfsRouteShape> shapes,
                                    Map<String, Long> lineIdByRouteId,
                                    long versionId) {
        List<LineShapeEntity> entities = shapes.stream()
                .map(s -> {
                    Long lineId = lineIdByRouteId.get(s.routeId());
                    if (lineId == null) {
                        throw new IllegalStateException(
                                "line_shape references unknown routeId: '" + s.routeId() + "'");
                    }
                    return lineShapeMapper.toEntity(s, lineId, versionId);
                })
                .toList();
        lineShapeRepo.saveAll(entities);
    }

    private void persistStops(List<GtfsStop> stops, long versionId) {
        List<StopEntity> entities = stops.stream()
                .map(s -> StopMapper.toEntity(s, versionId))
                .toList();
        stopRepo.saveAll(entities);
    }

    private void persistLineNeighborhoods(List<LineNeighborhoodRelation> relations,
                                           Map<String, Long> lineIdByRouteId,
                                           Map<String, Long> neighborhoodIdByName,
                                           long versionId) {
        List<LineNeighborhoodEntity> entities = relations.stream()
                .map(r -> {
                    Long lineId = lineIdByRouteId.get(r.routeId());
                    if (lineId == null) {
                        throw new IllegalStateException(
                                "line_neighborhood references unknown routeId: '" + r.routeId() + "'");
                    }
                    Long neighborhoodId = neighborhoodIdByName.get(r.neighborhoodName());
                    if (neighborhoodId == null) {
                        throw new IllegalStateException(
                                "line_neighborhood references unknown neighborhoodName: '"
                                        + r.neighborhoodName() + "'");
                    }
                    return LineNeighborhoodMapper.toEntity(r, lineId, neighborhoodId, versionId);
                })
                .toList();
        lineNeighborhoodRepo.saveAll(entities);
    }
}
