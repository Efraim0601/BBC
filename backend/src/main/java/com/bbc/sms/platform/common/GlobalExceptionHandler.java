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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/** Turns exceptions into a consistent JSON error body. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ApiError(OffsetDateTime timestamp, int status, String error, Object message) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return body(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, "Accès refusé");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
            .body(new ApiError(OffsetDateTime.now(), 400, "Validation", fields));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "Ressource introuvable.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "Méthode non autorisée.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        log.warn("Data integrity violation: {}", detail);
        String msg = "Données invalides ou en conflit (une contrainte n'est pas respectée).";
        if (detail != null && detail.toLowerCase().contains("value too long")) {
            msg = "Texte trop long pour un des champs (vérifiez le nom / libellé).";
        } else if (detail != null && detail.toLowerCase().contains("unique")) {
            msg = "Une entrée avec ces valeurs existe déjà.";
        }
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    /**
     * Envoi trop volumineux — rejeté par le conteneur AVANT d'atteindre le
     * service, qui n'a donc pas l'occasion de dire poliment non. Sans ce
     * traitement, un utilisateur qui joint un fichier trop lourd reçoit une
     * « erreur interne » là où il n'a commis qu'une erreur ordinaire.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE,
            "Fichier trop lourd. La taille maximale par document est de 25 Mo.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);   // full detail in logs, not in the response
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur.");
    }

    private ResponseEntity<ApiError> body(HttpStatus status, Object message) {
        return ResponseEntity.status(status)
            .body(new ApiError(OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message));
    }
}
