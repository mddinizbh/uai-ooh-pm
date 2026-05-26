package com.uai.buslines.adapter.out.gtfs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the GTFS feed downloader.
 *
 * <p>Bound from the {@code gtfs.feed.*} namespace in {@code application.yml}.
 * The feed URL is a mandatory external parameter — configure via {@code GTFS_FEED_URL}
 * environment variable or the {@code gtfs.feed.url} property at deploy time.
 *
 * @param url                    Full URL of the GTFS ZIP to download
 * @param maxRetries             Number of retry attempts after a transient failure (default 3)
 * @param retryDelayMs           Base delay in milliseconds between retries; scales linearly
 *                               with attempt number (default 2000)
 * @param requestTimeoutSeconds  Per-request HTTP read timeout in seconds (default 60)
 */
@ConfigurationProperties(prefix = "gtfs.feed")
public record GtfsFeedProperties(
        String url,
        int maxRetries,
        long retryDelayMs,
        int requestTimeoutSeconds
) {}
