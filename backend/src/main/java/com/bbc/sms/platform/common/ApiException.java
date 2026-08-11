package com.bbc.sms.platform.common;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/** Business exception translated to the audited, structured API error envelope. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;
    private final List<Map<String, Object>> conflicts;
    private final List<String> blockers;
    private final Long currentVersion;
    private final Long staleVersion;
    private final String messageKey;
    private final Map<String, Object> messageParams;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String message) {
        this(status, defaultCode(status), message, Map.of(), List.of(), List.of(), null, null, null, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message,
                        Map<String, String> fieldErrors,
                        List<Map<String, Object>> conflicts,
                        List<String> blockers,
                        Long currentVersion, Long staleVersion,
                        String messageKey, Map<String, Object> messageParams) {
        this(status, code, message, fieldErrors, conflicts, blockers, currentVersion, staleVersion,
                messageKey, messageParams, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message,
                        Map<String, String> fieldErrors,
                        List<Map<String, Object>> conflicts,
                        List<String> blockers,
                        Long currentVersion, Long staleVersion,
                        String messageKey, Map<String, Object> messageParams,
                        Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code == null || code.isBlank() ? defaultCode(status) : code;
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        this.blockers = blockers == null ? List.of() : List.copyOf(blockers);
        this.currentVersion = currentVersion;
        this.staleVersion = staleVersion;
        this.messageKey = messageKey;
        this.messageParams = messageParams == null ? Map.of() : Map.copyOf(messageParams);
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
    public List<Map<String, Object>> getConflicts() { return conflicts; }
    public List<String> getBlockers() { return blockers; }
    public Long getCurrentVersion() { return currentVersion; }
    public Long getStaleVersion() { return staleVersion; }
    public String getMessageKey() { return messageKey; }
    public Map<String, Object> getMessageParams() { return messageParams; }
    public Map<String, Object> getDetails() { return details; }

    public static ApiException notFound(String what) {
        return coded(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", what + " introuvable");
    }

    public static ApiException badRequest(String message) {
        return coded(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    public static ApiException conflict(String message) {
        return coded(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static ApiException conflict(String code, String message,
                                        List<Map<String, Object>> conflicts) {
        return new ApiException(HttpStatus.CONFLICT, code, message, Map.of(), conflicts,
                List.of(), null, null, null, Map.of());
    }

    public static ApiException conflictWithDetails(String code, String message,
                                                   Map<String, Object> details) {
        return new ApiException(HttpStatus.CONFLICT, code, message, Map.of(), List.of(), List.of(),
                null, null, null, Map.of(), details);
    }

    public static ApiException forbidden(String message) {
        return coded(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException coded(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message, Map.of(), List.of(), List.of(), null, null, null, Map.of());
    }

    public static ApiException field(HttpStatus status, String code, String message,
                                     String field, String fieldMessage) {
        return new ApiException(status, code, message, Map.of(field, fieldMessage), List.of(), List.of(),
                null, null, null, Map.of());
    }

    public static ApiException fields(HttpStatus status, String code, String message,
                                      Map<String, String> fieldErrors) {
        return new ApiException(status, code, message, fieldErrors, List.of(), List.of(),
                null, null, null, Map.of());
    }

    public static ApiException blockers(String code, String message, List<String> blockers) {
        return new ApiException(HttpStatus.CONFLICT, code, message, Map.of(), List.of(), blockers,
                null, null, null, Map.of());
    }

    public static ApiException staleVersion(String message, long currentVersion, long staleVersion) {
        return new ApiException(HttpStatus.CONFLICT, "STALE_VERSION", message, Map.of(), List.of(), List.of(),
                currentVersion, staleVersion, "stale_version",
                Map.of("currentVersion", currentVersion, "staleVersion", staleVersion));
    }

    public static ApiException staleVersion(String message, long currentVersion, long staleVersion, String field) {
        return new ApiException(HttpStatus.CONFLICT, "STALE_VERSION", message,
                Map.of(field, message), List.of(), List.of(), currentVersion, staleVersion,
                "stale_version", Map.of("currentVersion", currentVersion, "staleVersion", staleVersion));
    }

    private static String defaultCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "VALIDATION_ERROR";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case FORBIDDEN -> "FORBIDDEN";
            case CONFLICT -> "CONFLICT";
            default -> "API_ERROR";
        };
    }
}
