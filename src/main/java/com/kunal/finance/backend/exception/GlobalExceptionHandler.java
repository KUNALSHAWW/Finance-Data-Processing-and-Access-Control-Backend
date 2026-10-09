package com.kunal.finance.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.kunal.finance.backend.dto.Dtos.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Extends ResponseEntityExceptionHandler so framework errors (bad enum in a query string, wrong HTTP method,
 * unknown path, malformed JSON) keep their correct 4xx status instead of falling into the catch-all 500.
 * Every body uses the same ErrorResponse shape.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = body instanceof ProblemDetail pd && pd.getDetail() != null ? pd.getDetail() : "Request failed";
        return ResponseEntity.status(status).headers(headers).body(ErrorResponse.of(codeOf(status), message));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED", "Request validation failed", fields));
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ErrorResponse> badCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid email or password");
    }

    @ExceptionHandler(DisabledException.class)
    ResponseEntity<ErrorResponse> disabled(DisabledException ex) {
        return build(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Account is disabled");
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    ResponseEntity<ErrorResponse> tooMany(TooManyAttemptsException ex) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", ex.getMessage());
    }

    @ExceptionHandler(DemoRestrictionException.class)
    ResponseEntity<ErrorResponse> demoRestricted(DemoRestrictionException ex) {
        return build(HttpStatus.FORBIDDEN, "DEMO_RESTRICTED", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorResponse> conflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> staleWrite(ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", "The record was changed by someone else, reload and retry");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT", "Database constraint violation");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong");
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
    }

    private static String codeOf(HttpStatusCode status) {
        HttpStatus s = HttpStatus.resolve(status.value());
        return s == null ? "ERROR" : s.name();
    }
}
