package com.uai.buslines.adapter.in.web;

import com.uai.buslines.domain.model.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

/**
 * Centralised error handler for all REST controllers.
 *
 * <p>Returns structured JSON ({@link ErrorResponse}) for all errors — no stack
 * traces are included in responses (uAI convention).
 *
 * <p>Handled cases:
 * <ul>
 *   <li>{@link NotFoundException} → 404 Not Found</li>
 *   <li>{@link IllegalArgumentException} → 400 Bad Request</li>
 *   <li>Unhandled {@link Exception} → 500 Internal Server Error (logged at ERROR)</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "An unexpected error occurred", Instant.now()));
    }

    /**
     * Structured error response body — no stack traces.
     *
     * @param status    HTTP status code
     * @param message   human-readable error description
     * @param timestamp when the error occurred (UTC)
     */
    public record ErrorResponse(int status, String message, Instant timestamp) {}
}
