package org.openelisglobal.nidanresult;

/**
 * Typed exception thrown by {@link NidanResultService} for all anticipated
 * error conditions (bad input, not-found, pipeline failure).
 *
 * <p>
 * The controller catches this and maps it to an appropriate HTTP status code
 * plus a structured JSON error body. Callers never receive a raw stack trace.
 */
public class NidanResultException extends Exception {

    private static final long serialVersionUID = 1L;

    /** HTTP status hint carried with this exception. */
    public enum Kind {
        /** Caller supplied invalid or missing parameters. Maps to HTTP 400. */
        BAD_REQUEST,
        /** Referenced resource (sample, analysis) not found. Maps to HTTP 404. */
        NOT_FOUND,
        /** An internal system error occurred. Maps to HTTP 500. */
        INTERNAL_ERROR
    }

    private final Kind kind;

    public NidanResultException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public NidanResultException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
