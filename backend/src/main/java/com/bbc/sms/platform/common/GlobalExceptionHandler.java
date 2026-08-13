package com.bbc.sms.platform.common;

import jakarta.persistence.OptimisticLockException;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Turns exceptions into a consistent JSON error body. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** The extra fields are additive; existing clients may continue reading status/message. */
    public record ApiError(OffsetDateTime timestamp, int status, String error, Object message,
                           String code, Map<String, String> fieldErrors,
                           List<ApiException.Blocker> blockers, String correlationId) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        String correlation = ex.getCorrelationId() == null ? correlationId() : ex.getCorrelationId();
        ex.withCorrelationId(correlation);
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(
                OffsetDateTime.now(), ex.getStatus().value(), ex.getStatus().getReasonPhrase(),
                ex.getMessage(), ex.getCode(), ex.getFieldErrors(), ex.getBlockers(), correlation));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, "Accès refusé.", "PERMISSION_DENIED", Map.of(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return body(HttpStatus.BAD_REQUEST, "Corrigez les champs signalés.",
                "VALIDATION_ERROR", fields, List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "Le format de la requête est invalide. Vérifiez les champs et les dates envoyés.",
                "MALFORMED_REQUEST", Map.of(), List.of());
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockException ex) {
        return body(HttpStatus.CONFLICT, "Cet enregistrement a changé ailleurs. Rechargez-le avant de réessayer.",
                "VERSION_CONFLICT", Map.of(), List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "Ressource introuvable.", "NOT_FOUND", Map.of(), List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "Méthode non autorisée.", "METHOD_NOT_ALLOWED", Map.of(), List.of());
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
        return body(HttpStatus.BAD_REQUEST, msg, "DATA_INTEGRITY_ERROR", Map.of(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur.", "INTERNAL_ERROR", Map.of(), List.of());
    }

    private ResponseEntity<ApiError> body(HttpStatus status, Object message, String code,
                                          Map<String, String> fields, List<ApiException.Blocker> blockers) {
        String correlation = correlationId();
        return ResponseEntity.status(status).body(new ApiError(
                OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message,
                code, fields, blockers, correlation));
    }

    private String correlationId() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            String incoming = servlet.getRequest().getHeader("X-Correlation-ID");
            if (incoming != null && !incoming.isBlank()) return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }
}
