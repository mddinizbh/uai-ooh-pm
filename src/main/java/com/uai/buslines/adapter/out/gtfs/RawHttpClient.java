package com.uai.buslines.adapter.out.gtfs;

import java.io.IOException;

/**
 * Package-private functional interface that performs the actual HTTP download call.
 *
 * <p>Extracted from {@link HttpGtfsFeedGateway} to enable unit testing without a
 * real HTTP server: tests inject a lambda instead of a live {@code HttpClient}.
 *
 * @param url         URL of the GTFS ZIP
 * @param ifNoneMatch value for the {@code If-None-Match} header; {@code null} to skip
 * @return raw HTTP response (status + body + etag)
 */
@FunctionalInterface
interface RawHttpClient {
    RawHttpResponse download(String url, String ifNoneMatch) throws IOException, InterruptedException;
}
