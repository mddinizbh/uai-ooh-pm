package com.uai.buslines.adapter.out.boundary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the neighbourhood boundary downloader.
 *
 * <p>Bound from the {@code boundary.*} namespace in {@code application.yml}.
 * The boundary URL is a mandatory external parameter — configure via the
 * {@code BOUNDARY_URL} environment variable or the {@code boundary.url} property
 * at deploy time.
 *
 * <p>Note: the exact PBH boundary URL/format is an open question (TechSpec
 * "Technical Dependencies"). The default value below is a best-effort placeholder;
 * verify and update before production import.
 *
 * @param url                   Full URL of the GeoJSON boundary file to download
 * @param requestTimeoutSeconds Per-request HTTP read timeout in seconds (default 60)
 */
@ConfigurationProperties(prefix = "boundary")
public record BoundaryProperties(
        String url,
        int requestTimeoutSeconds
) {}
