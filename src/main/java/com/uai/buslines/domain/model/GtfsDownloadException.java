package com.uai.buslines.domain.model;

/**
 * Thrown when the GTFS feed cannot be downloaded after all retries are exhausted
 * or when a non-transient HTTP error is received.
 *
 * <p>The import orchestrator (task_05) catches this to keep the last successfully
 * imported dataset active rather than serving a partial or empty dataset.
 */
public class GtfsDownloadException extends RuntimeException {

    /** HTTP status code, or {@code -1} for connection-level (non-HTTP) failures. */
    private final int statusCode;

    /**
     * @param message    human-readable description of the failure
     * @param statusCode HTTP status code that caused the failure
     */
    public GtfsDownloadException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * @param message description of the failure
     * @param cause   underlying I/O or interruption exception
     */
    public GtfsDownloadException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /** HTTP status code that triggered this failure, or {@code -1} for non-HTTP errors. */
    public int statusCode() {
        return statusCode;
    }
}
