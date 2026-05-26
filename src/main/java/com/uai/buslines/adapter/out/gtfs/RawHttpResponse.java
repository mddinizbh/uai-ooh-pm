package com.uai.buslines.adapter.out.gtfs;

/**
 * Low-level HTTP response wrapper used internally by {@link HttpGtfsFeedGateway}.
 * Package-private — not part of any public API.
 *
 * @param statusCode HTTP status code
 * @param body       raw response body bytes (may be empty for 304 responses)
 * @param etag       value of the {@code ETag} response header; {@code null} if absent
 */
record RawHttpResponse(int statusCode, byte[] body, String etag) {}
