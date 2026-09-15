package com.ase.billing.exception;

import com.ase.billing.web.dto.Dtos.ApiError;
import com.ase.billing.web.dto.Dtos.FieldProblem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Turns failures into JSON the UI can show next to the field that caused them.
 * A billing operator needs to know which line is wrong, not that "an error occurred".
 *
 * Every branch here also logs. A refusal (not found, validation) is expected
 * traffic and logs at WARN with just the message; anything that reaches the
 * catch-all is a bug and logs at ERROR with the full stack trace, since that is
 * otherwise the one place in the app where a failure could vanish silently.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        log.warn("Not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(e.getMessage(), List.of()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> validation(ValidationException e) {
        log.warn("Refused: {}", e.getMessage());
        List<FieldProblem> problems = e.getProblems().stream()
                .map(p -> new FieldProblem(null, p))
                .toList();
        return ResponseEntity.unprocessableEntity()
                .body(new ApiError(e.getMessage(), problems));
    }

    /** Bean Validation on a @RequestBody: report every field at once. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> beanValidation(MethodArgumentNotValidException e) {
        List<FieldProblem> problems = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldProblem(f.getField(), f.getDefaultMessage()))
                .toList();
        log.warn("Rejected request body: {}", problems);
        return ResponseEntity.badRequest()
                .body(new ApiError("Some details need fixing.", problems));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> illegalState(IllegalStateException e) {
        log.warn("Illegal state: {}", e.getMessage());
        return ResponseEntity.unprocessableEntity()
                .body(new ApiError(e.getMessage(), List.of()));
    }

    /** Anything not already handled above. This used to vanish with no trace at all. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unhandled exception reaching the API boundary", e);
        return ResponseEntity.internalServerError()
                .body(new ApiError("Something went wrong on the server. It has been logged.", List.of()));
    }
}
