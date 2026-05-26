package com.uai.buslines.domain.model;

/**
 * The result of a GTFS feed download attempt.
 *
 * <p>uAI RULE-JAVA-02: sealed type — switch expressions must cover all permits
 * without a {@code default} branch; the compiler enforces exhaustiveness.
 */
public sealed interface GtfsFeedResult
        permits GtfsFeedResult.Downloaded, GtfsFeedResult.NotModified {

    /**
     * The server returned a new feed ZIP (HTTP 200).
     *
     * @param content raw ZIP bytes
     * @param etag    value of the {@code ETag} response header; {@code null} if absent
     */
    record Downloaded(byte[] content, String etag) implements GtfsFeedResult {}

    /**
     * The server returned HTTP 304 — the feed has not changed since {@code lastKnownEtag}.
     * The caller should keep the last successfully parsed dataset active.
     */
    record NotModified() implements GtfsFeedResult {}
}
