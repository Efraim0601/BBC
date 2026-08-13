package com.bbc.sms.platform.common;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Business exception carrying an HTTP status and an optional finance-friendly error contract. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;
    private final List<Blocker> blockers;
    private String correlationId;

    public record Blocker(String entityType, String entityId, String label, String action) {}

    public ApiException(HttpStatus status, String message) {
        this(status, "BUSINESS_ERROR", message, Map.of(), List.of());
    }

    public ApiException(HttpStatus status, String code, String message,
                        Map<String, String> fieldErrors, List<Blocker> blockers) {
        super(message);
        this.status = status;
        this.code = code == null || code.isBlank() ? "BUSINESS_ERROR" : code;
        this.fieldErrors = fieldErrors == null ? Map.of() : Collections.unmodifiableMap(fieldErrors);
        this.blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
    public List<Blocker> getBlockers() { return blockers; }
    public String getCorrelationId() { return correlationId; }

    public ApiException withCorrelationId(String id) {
        this.correlationId = id;
        return this;
    }

    public static ApiException structured(HttpStatus status, String code, String message,
                                          Map<String, String> fieldErrors, List<Blocker> blockers) {
        return new ApiException(status, code, message, fieldErrors, blockers);
    }

    public static ApiException notFound(String what) {
        return structured(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " introuvable", Map.of(), List.of());
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }
}
