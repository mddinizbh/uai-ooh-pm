package com.uai.buslines.adapter.out.gtfs;

import com.uai.buslines.domain.model.GtfsDownloadException;
import com.uai.buslines.domain.model.GtfsFeedResult;
import com.uai.buslines.domain.port.out.GtfsFeedGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP adapter that implements {@link GtfsFeedGateway}.
 *
 * <p>Features:
 * <ul>
 *   <li>Conditional GET with {@code If-None-Match} → returns
 *       {@link GtfsFeedResult.NotModified} on HTTP 304.</li>
 *   <li>Linear backoff retry on 5xx and connection-level failures.</li>
 *   <li>Immediate {@link GtfsDownloadException} on 4xx (non-transient).</li>
 * </ul>
 *
 * <p>Package-private; exposed as {@link GtfsFeedGateway} through
 * {@link GtfsAdapterConfiguration}.
 */
class HttpGtfsFeedGateway implements GtfsFeedGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpGtfsFeedGateway.class);

    private final GtfsFeedProperties props;
    private final RawHttpClient      httpClient;

    /**
     * Production constructor — wires a real {@link java.net.http.HttpClient}.
     */
    HttpGtfsFeedGateway(GtfsFeedProperties props) {
        this.props = props;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                .build();
        this.httpClient = (url, ifNoneMatch) -> {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                    .GET();
            if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
                rb.header("If-None-Match", ifNoneMatch);
            }
            HttpResponse<byte[]> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
            String etag = resp.headers().firstValue("ETag").orElse(null);
            return new RawHttpResponse(resp.statusCode(), resp.body(), etag);
        };
    }

    /**
     * Test constructor — injects a mock {@link RawHttpClient} to avoid a live HTTP server.
     */
    HttpGtfsFeedGateway(GtfsFeedProperties props, RawHttpClient httpClient) {
        this.props      = props;
        this.httpClient = httpClient;
    }

    // ── GtfsFeedGateway ─────────────────────────────────────────────────────────

    @Override
    public GtfsFeedResult download(String lastKnownEtag) {
        int maxAttempts = props.maxRetries() + 1;
        GtfsDownloadException lastFailure = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (attempt > 0) {
                long delay = props.retryDelayMs() * attempt; // linear backoff
                log.warn("GTFS download attempt {} of {} failed; retrying in {} ms",
                        attempt, maxAttempts, delay);
                sleep(delay);
            }

            try {
                RawHttpResponse resp = httpClient.download(props.url(), lastKnownEtag);
                int status = resp.statusCode();

                if (status == 200) {
                    log.info("GTFS feed downloaded (ETag: {})", resp.etag());
                    return new GtfsFeedResult.Downloaded(resp.body(), resp.etag());
                }
                if (status == 304) {
                    log.info("GTFS feed not modified (ETag: {})", lastKnownEtag);
                    return new GtfsFeedResult.NotModified();
                }
                if (status >= 500) {
                    // Transient server error — eligible for retry
                    lastFailure = new GtfsDownloadException(
                            "GTFS server error on attempt " + (attempt + 1) + ": HTTP " + status,
                            status);
                } else {
                    // Non-transient client error (4xx) — fail immediately
                    throw new GtfsDownloadException(
                            "GTFS download failed with non-retryable HTTP " + status, status);
                }

            } catch (GtfsDownloadException e) {
                throw e; // re-throw non-transient and interrupted errors immediately
            } catch (IOException e) {
                lastFailure = new GtfsDownloadException(
                        "GTFS download I/O error on attempt " + (attempt + 1) + ": " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GtfsDownloadException("GTFS download interrupted", e);
            }
        }

        throw lastFailure != null ? lastFailure
                : new GtfsDownloadException("GTFS download failed after " + maxAttempts + " attempts", -1);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GtfsDownloadException("GTFS download sleep interrupted", e);
        }
    }
}
