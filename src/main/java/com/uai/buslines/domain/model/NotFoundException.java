package com.uai.buslines.domain.model;

/**
 * Thrown by use cases when a requested resource does not exist in the active dataset.
 *
 * <p>Caught by the web adapter's {@code GlobalExceptionHandler} and translated to an
 * HTTP 404 response with a structured JSON error body (no stack trace).
 *
 * <p>No framework dependencies — pure domain exception (RULE-JAVA-01).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
