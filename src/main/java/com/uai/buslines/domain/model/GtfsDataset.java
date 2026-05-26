package com.uai.buslines.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * The complete parsed and classified GTFS dataset — the aggregate consumed by:
 * <ul>
 *   <li>task_03: JTS neighbourhood classification (populates {@link #neighborhoods()}
 *       and {@link #lineRelations()})</li>
 *   <li>task_04: persistence adapter</li>
 * </ul>
 *
 * <p>No framework, JPA, or HTTP types (RULE-JAVA-01).
 *
 * @param feedEtag         HTTP {@code ETag} returned by the GTFS server; {@code null} if
 *                         the server did not provide one. Stored in
 *                         {@code dataset_version.gtfs_source_etag} for observability.
 * @param fetchedAt        Timestamp of the download request.
 * @param routes           All routes from {@code routes.txt} (coordinates validated WGS84).
 * @param stops            All stops from {@code stops.txt} (coordinates validated WGS84).
 * @param routeShapes      Per-direction route shapes assembled from {@code shapes.txt}
 *                         and {@code trips.txt}.
 * @param neighborhoods    Neighbourhood polygons loaded and classified in task_03.
 *                         Empty list before classification runs.
 * @param lineRelations    Classified line↔neighbourhood relations produced by task_03.
 *                         Empty list before classification runs.
 */
public record GtfsDataset(
        String feedEtag,
        Instant fetchedAt,
        List<GtfsRoute> routes,
        List<GtfsStop> stops,
        List<GtfsRouteShape> routeShapes,
        List<Neighborhood> neighborhoods,
        List<LineNeighborhoodRelation> lineRelations
) {

    /**
     * Convenience constructor used by {@link com.uai.buslines.application.GtfsParser}
     * before neighbourhood classification runs.
     *
     * <p>{@link #neighborhoods()} and {@link #lineRelations()} default to empty
     * unmodifiable lists; {@code NeighborhoodClassifier} (task_03) enriches these
     * by returning a new {@code GtfsDataset} instance with all seven fields populated.
     */
    public GtfsDataset(String feedEtag, Instant fetchedAt,
                       List<GtfsRoute> routes, List<GtfsStop> stops,
                       List<GtfsRouteShape> routeShapes) {
        this(feedEtag, fetchedAt, routes, stops, routeShapes, List.of(), List.of());
    }
}
