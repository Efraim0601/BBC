package com.bbc.sms.platform.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Turns exceptions into one stable, bilingual-client-friendly JSON envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(OffsetDateTime timestamp, int status, String error, String message,
                           String code, Map<String, String> fieldErrors,
                           List<Map<String, Object>> conflicts, List<String> blockers,
                           String correlationId, Long currentVersion, Long staleVersion,
                           String messageKey, Map<String, Object> messageParams) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        String correlationId = UUID.randomUUID().toString();
        return ResponseEntity.status(ex.getStatus()).header("X-Correlation-Id", correlationId)
                .body(error(ex.getStatus(), ex.getMessage(), ex.getCode(), ex.getFieldErrors(), ex.getConflicts(),
                        ex.getBlockers(), correlationId, ex.getCurrentVersion(), ex.getStaleVersion(),
                        ex.getMessageKey(), ex.getMessageParams()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "Accès refusé");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Corrigez les champs indiqués.", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Le format de la requête est invalide. Vérifiez les champs et les dates envoyés.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return body(HttpStatus.BAD_REQUEST, "REQUEST_PARAMETER_REQUIRED",
                "A required request parameter is missing.",
                Map.of(ex.getParameterName(), "This request parameter is required."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleParameterType(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, "REQUEST_PARAMETER_INVALID",
                "A request parameter has an invalid format.",
                Map.of(ex.getName(), "Use the expected identifier or date format."));
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiError> handleMissingPathVariable(MissingPathVariableException ex) {
        return body(HttpStatus.BAD_REQUEST, "PATH_VARIABLE_REQUIRED",
                "A required resource identifier is missing.",
                Map.of(ex.getVariableName(), "This path value is required."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Ressource introuvable.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Méthode non autorisée.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        log.warn("Data integrity violation: {}", detail);
        String msg = "Données invalides ou en conflit (une contrainte n'est pas respectée).";
        if (detail != null && detail.toLowerCase().contains("value too long")) {
            msg = "Texte trop long pour un des champs (vérifiez le nom / libellé).";
        } else if (detail != null && detail.contains("chk_student_enrollment_dates")) {
            msg = "La date de sortie ou de transfert ne peut pas précéder la date d’inscription de l’élève.";
        } else if (detail != null && detail.contains("uq_student_enrollment_active_session")) {
            msg = "Cet élève possède déjà une inscription active pour cette session académique.";
        } else if (detail != null && detail.toLowerCase().contains("unique")) {
            msg = "Une entrée avec ces valeurs existe déjà.";
        }
        return body(HttpStatus.CONFLICT, "DATA_INTEGRITY_CONFLICT", msg);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erreur interne du serveur.");
    }

    private ResponseEntity<ApiError> body(HttpStatus status, String code, String message) {
        return body(status, code, message, Map.of());
    }

    private ResponseEntity<ApiError> body(HttpStatus status, String code, String message,
                                         Map<String, String> fields) {
        String correlationId = UUID.randomUUID().toString();
        return ResponseEntity.status(status).header("X-Correlation-Id", correlationId)
                .body(error(status, message, code, fields, List.of(), List.of(), correlationId,
                        null, null, null, Map.of()));
    }

    private ApiError error(HttpStatus status, String message, String code,
                           Map<String, String> fields, List<Map<String, Object>> conflicts,
                           List<String> blockers, String correlationId,
                           Long currentVersion, Long staleVersion,
                           String messageKey, Map<String, Object> messageParams) {
        return new ApiError(OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, code,
                fields == null ? Map.of() : fields,
                conflicts == null ? List.of() : conflicts,
                blockers == null ? List.of() : blockers,
                correlationId, currentVersion, staleVersion, messageKey,
                messageParams == null ? Map.of() : messageParams);
    }
}
