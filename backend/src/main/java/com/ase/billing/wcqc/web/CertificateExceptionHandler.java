package com.ase.billing.wcqc.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Billing's own {@code com.ase.billing.exception.ApiExceptionHandler} is a
 * global {@code @RestControllerAdvice}, so it already catches anything this
 * module throws that it doesn't have a specific handler for — but it maps
 * {@code IllegalStateException} to 422 and leaves {@code NoSuchElementException}
 * and {@code IllegalArgumentException} to fall through to its generic
 * catch-all, which returns a bare 500 ("Something went wrong on the server")
 * for what are actually ordinary, expected outcomes here (a bad certificate
 * id, an unsupported upload file type). This handler is scoped to
 * {@code com.ase.billing.wcqc.web} only — it does not touch or duplicate
 * anything billing's own handler already does correctly (bean validation,
 * IllegalStateException) — and does not reuse billing's own exception
 * classes (NotFoundException/ValidationException), so the module stays
 * independent of billing's exception types as well as its services.
 */
@RestControllerAdvice(basePackages = "com.ase.billing.wcqc.web")
public class CertificateExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CertificateExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException e) {
        log.warn("Not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", e.getMessage(), "problems", java.util.List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        log.warn("Rejected: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("message", e.getMessage(), "problems", java.util.List.of()));
    }
}
