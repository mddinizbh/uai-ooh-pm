package com.uai.buslines.domain.port.out;

import com.uai.buslines.domain.model.GtfsDownloadException;
import com.uai.buslines.domain.model.GtfsFeedResult;

/**
 * Driven port — downloads the official GTFS feed ZIP from the PBH/BHTRANS open-data
 * source and exposes conditional-GET semantics so unchanged feeds are not re-parsed.
 *
 * <p>Contract:
 * <ul>
 *   <li>Honour {@code ETag} / {@code If-None-Match} to skip re-parsing unchanged feeds.</li>
 *   <li>Retry on transient failures (5xx, I/O errors) with backoff.</li>
 *   <li>Throw {@link GtfsDownloadException} when retries are exhausted or a non-transient
 *       error occurs (4xx client errors).</li>
 *   <li>No framework or HTTP types in the return value — only sealed domain types.</li>
 * </ul>
 *
 * <p>Implemented by {@code adapter.out.gtfs.HttpGtfsFeedGateway}.
 */
public interface GtfsFeedGateway {

    /**
     * Attempts to download the latest GTFS feed.
     *
     * @param lastKnownEtag the {@code ETag} value from the previous successful download;
     *                      {@code null} or blank triggers an unconditional GET.
     * @return {@link GtfsFeedResult.Downloaded} with ZIP bytes + resolved ETag,
     *         or {@link GtfsFeedResult.NotModified} when the server reports the feed
     *         has not changed.
     * @throws GtfsDownloadException if the download fails after all retries.
     */
    GtfsFeedResult download(String lastKnownEtag);
}
