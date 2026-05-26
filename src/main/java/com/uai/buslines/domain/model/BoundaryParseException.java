package com.uai.buslines.domain.model;

/**
 * Thrown when the neighbourhood boundary file cannot be parsed or fails validation.
 *
 * <p>Unchecked — callers (import orchestration) should catch this to abort the
 * current import and keep serving the last good dataset rather than a partial one.
 */
public class BoundaryParseException extends RuntimeException {

    public BoundaryParseException(String message) {
        super(message);
    }

    public BoundaryParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
