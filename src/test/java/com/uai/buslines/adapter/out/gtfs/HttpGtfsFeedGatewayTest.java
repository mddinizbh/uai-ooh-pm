package com.uai.buslines.adapter.out.gtfs;

import com.uai.buslines.GtfsFixtures;
import com.uai.buslines.domain.model.GtfsDownloadException;
import com.uai.buslines.domain.model.GtfsFeedResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HttpGtfsFeedGateway}.
 *
 * <p>Uses a mock {@link RawHttpClient} (lambda) injected via the package-private
 * test constructor — no live HTTP server required.
 *
 * <p>This test class is in the {@code adapter.out.gtfs} package so it can access
 * the package-private types {@link RawHttpClient} and {@link RawHttpResponse}.
 */
class HttpGtfsFeedGatewayTest {

    /**
     * Test properties with 0 ms retry delay to keep tests fast.
     */
    private static GtfsFeedProperties fastProps(int maxRetries) {
        return new GtfsFeedProperties("http://example.com/gtfs.zip", maxRetries, 0L, 30);
    }

    // ── Conditional GET / ETag ──────────────────────────────────────────────────

    @Test
    void returnsNotModifiedWhenServerReturns304() {
        RawHttpClient mock = (url, ifNoneMatch) -> new RawHttpResponse(304, new byte[0], null);
        var gateway = new HttpGtfsFeedGateway(fastProps(3), mock);

        GtfsFeedResult result = gateway.download("\"abc123\"");

        assertThat(result).isInstanceOf(GtfsFeedResult.NotModified.class);
    }

    @Test
    void returnsDownloadedWith200AndEtag() {
        byte[] content = GtfsFixtures.minimalGtfsZip();
        RawHttpClient mock = (url, ifNoneMatch) -> new RawHttpResponse(200, content, "\"v2\"");
        var gateway = new HttpGtfsFeedGateway(fastProps(3), mock);

        GtfsFeedResult result = gateway.download(null);

        assertThat(result).isInstanceOf(GtfsFeedResult.Downloaded.class);
        GtfsFeedResult.Downloaded downloaded = (GtfsFeedResult.Downloaded) result;
        assertThat(downloaded.etag()).isEqualTo("\"v2\"");
        assertThat(downloaded.content()).isEqualTo(content);
    }

    @Test
    void downloadsWhenNoPreviousEtag() {
        byte[] content = GtfsFixtures.minimalGtfsZip();
        RawHttpClient mock = (url, ifNoneMatch) -> {
            assertThat(ifNoneMatch).isNull();
            return new RawHttpResponse(200, content, null);
        };
        var gateway = new HttpGtfsFeedGateway(fastProps(3), mock);

        GtfsFeedResult result = gateway.download(null);

        assertThat(result).isInstanceOf(GtfsFeedResult.Downloaded.class);
    }

    // ── Retry on 5xx ────────────────────────────────────────────────────────────

    @Test
    void retriesOnServerErrorAndEventuallySucceeds() {
        AtomicInteger callCount = new AtomicInteger(0);
        byte[] content = GtfsFixtures.minimalGtfsZip();

        RawHttpClient mock = (url, ifNoneMatch) -> {
            if (callCount.getAndIncrement() < 2) {
                return new RawHttpResponse(503, new byte[0], null);
            }
            return new RawHttpResponse(200, content, "\"v1\"");
        };

        var gateway = new HttpGtfsFeedGateway(fastProps(3), mock);
        GtfsFeedResult result = gateway.download(null);

        assertThat(result).isInstanceOf(GtfsFeedResult.Downloaded.class);
        assertThat(callCount.get()).isEqualTo(3); // 2 failures + 1 success
    }

    @Test
    void throwsDownloadExceptionWhenAllRetriesExhausted() {
        RawHttpClient mock = (url, ifNoneMatch) -> new RawHttpResponse(503, new byte[0], null);
        // maxRetries=2 → total 3 attempts
        var gateway = new HttpGtfsFeedGateway(fastProps(2), mock);

        assertThatThrownBy(() -> gateway.download(null))
                .isInstanceOf(GtfsDownloadException.class)
                .hasMessageContaining("503");
    }

    // ── Non-transient 4xx ────────────────────────────────────────────────────────

    @Test
    void throwsImmediatelyOnClientError404() {
        AtomicInteger callCount = new AtomicInteger(0);
        RawHttpClient mock = (url, ifNoneMatch) -> {
            callCount.incrementAndGet();
            return new RawHttpResponse(404, new byte[0], null);
        };
        var gateway = new HttpGtfsFeedGateway(fastProps(3), mock);

        assertThatThrownBy(() -> gateway.download(null))
                .isInstanceOf(GtfsDownloadException.class)
                .hasMessageContaining("404");

        // Non-transient: must NOT retry
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void throwsImmediatelyOnClientError403() {
        RawHttpClient mock = (url, ifNoneMatch) -> new RawHttpResponse(403, new byte[0], null);
        var gateway = new HttpGtfsFeedGateway(fastProps(3), mock);

        assertThatThrownBy(() -> gateway.download(null))
                .isInstanceOf(GtfsDownloadException.class)
                .hasMessageContaining("403");
    }

    // ── I/O failure ─────────────────────────────────────────────────────────────

    @Test
    void throwsDownloadExceptionOnIoFailure() {
        RawHttpClient mock = (url, ifNoneMatch) -> {
            throw new IOException("connection refused");
        };
        var gateway = new HttpGtfsFeedGateway(fastProps(0), mock);

        assertThatThrownBy(() -> gateway.download(null))
                .isInstanceOf(GtfsDownloadException.class)
                .hasMessageContaining("I/O error");
    }

    @Test
    void statusCodeIsSetOnDownloadException() {
        RawHttpClient mock = (url, ifNoneMatch) -> new RawHttpResponse(503, new byte[0], null);
        var gateway = new HttpGtfsFeedGateway(fastProps(0), mock);

        try {
            gateway.download(null);
        } catch (GtfsDownloadException e) {
            assertThat(e.statusCode()).isEqualTo(503);
        }
    }
}
