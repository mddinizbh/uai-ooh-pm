package com.uai.buslines.domain.model;

/**
 * Thrown when the GTFS ZIP content cannot be parsed into a valid {@link GtfsDataset}.
 *
 * <p>Covers: missing required GTFS files, missing required columns, invalid coordinate
 * values (outside WGS84 range), and structurally empty feeds.
 *
 * <p>The import orchestrator (task_05) catches this to keep the last successfully
 * imported dataset active rather than serving a partial or empty dataset.
 */
public class GtfsParseException extends RuntimeException {

    /**
     * @param message human-readable description, including the file name, column name,
     *                or coordinate value that caused the failure
     */
    public GtfsParseException(String message) {
        super(message);
    }

    /**
     * @param message human-readable description
     * @param cause   underlying I/O exception (e.g. ZIP read failure)
     */
    public GtfsParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
