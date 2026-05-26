package com.uai.buslines.adapter.out.boundary;

import java.io.IOException;

/**
 * Package-private functional interface for the raw HTTP call in
 * {@link HttpBoundaryGateway}.
 *
 * <p>Extracted to allow test injection of a mock without a live HTTP server —
 * the same pattern used by {@code RawHttpClient} in the GTFS adapter.
 */
@FunctionalInterface
interface RawBoundaryClient {

    /**
     * Performs an HTTP GET request and returns the response body as bytes.
     *
     * @param url target URL
     * @return response body bytes
     * @throws IOException on network or I/O failure
     * @throws InterruptedException if the thread is interrupted during the request
     */
    byte[] get(String url) throws IOException, InterruptedException;
}
