package com.uai.buslines.domain.port.in;

import com.uai.buslines.domain.model.*;

import java.util.List;

/**
 * Driving port — read-only queries over the active GTFS dataset.
 *
 * <p>Implemented by {@code QueryNetworkUseCaseImpl} in the application layer.
 * Consumed by the web adapter ({@code NeighborhoodController}, {@code LineController},
 * {@code MetaController}).
 *
 * <p>All methods operate on the currently active dataset version. If no import has
 * run yet, list methods return empty lists and detail methods throw
 * {@link NotFoundException}.
 *
 * <p>No framework, JPA, or HTTP types (RULE-JAVA-01).
 */
public interface QueryNetworkUseCase {

    /**
     * Returns all neighbourhoods in the active dataset, sorted by name, with
     * per-relation line counts.
     *
     * @return list of neighbourhood summaries; empty if no active dataset exists
     */
    List<NeighborhoodSummary> listNeighborhoods();

    /**
     * Returns lines serving the given neighbourhood, grouped into the three
     * spatial-relation buckets.
     *
     * <p>A single line may appear in more than one bucket (e.g. a bidirectional
     * route may both depart from and arrive at the same neighbourhood).
     *
     * @param neighborhoodId database primary key of the neighbourhood
     * @return grouped lines
     * @throws NotFoundException if no neighbourhood with the given id exists in
     *                           the active dataset
     */
    NeighborhoodLines linesByNeighborhood(long neighborhoodId);

    /**
     * Searches lines by number ({@code short_name}) or name ({@code long_name}).
     *
     * <p>The match is case-insensitive and substring-based. An empty or blank query
     * returns all lines in the active dataset, sorted by {@code short_name}.
     *
     * @param query search term; may be empty or blank
     * @return matching line summaries; empty if no active dataset exists or no match
     */
    List<LineSummary> searchLines(String query);

    /**
     * Returns full detail for a bus line: route shapes (GeoJSON geometry) and
     * ordered stops.
     *
     * <p>Stops are currently always empty — per-line stop ordering requires a
     * follow-up migration.
     *
     * @param lineId database primary key of the line
     * @return line detail with shapes and (empty) stops
     * @throws NotFoundException if no line with the given id exists in the active
     *                           dataset
     */
    LineDetail lineDetail(long lineId);
}
