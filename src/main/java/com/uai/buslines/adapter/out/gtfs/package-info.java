/**
 * GTFS feed adapter — HTTP downloader with conditional GET and retry/backoff.
 *
 * <p>Implements the {@code GtfsFeedGateway} driven port ({@code domain.port.out}).
 * HTTP mechanics ({@code HttpGtfsFeedGateway}) are package-private; only
 * {@code GtfsFeedGateway} is exposed as a Spring bean via
 * {@code GtfsAdapterConfiguration}.
 *
 * <p>Populated in task_02.
 */
package com.uai.buslines.adapter.out.gtfs;
