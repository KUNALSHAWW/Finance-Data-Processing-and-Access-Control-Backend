package com.kunal.finance.backend.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.kunal.finance.backend.dto.ErrorResponse;

import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
                log.warn("Authentication failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ErrorResponse.builder()
                                                .error("UNAUTHORIZED")
                                                .message("Invalid email or password")
                                                .build());
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
                log.warn("Resource not found: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ErrorResponse.builder()
                                                .error("NOT_FOUND")
                                                .message(ex.getMessage())
                                                .build());
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
                log.warn("Access denied: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ErrorResponse.builder()
                                                .error("FORBIDDEN")
                                                .message(ex.getMessage())
                                                .build());
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {

                log.warn("Validation failed");

                Map<String, String> errors = new HashMap<>();

                ex.getBindingResult().getFieldErrors()
                                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

                return ResponseEntity.badRequest().body(errors);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ErrorResponse> handleInvalidJson(HttpMessageNotReadableException ex) {
                log.warn("Malformed JSON request");
                return ResponseEntity.badRequest()
                                .body(ErrorResponse.builder()
                                                .error("INVALID_REQUEST")
                                                .message("Malformed JSON or invalid data")
                                                .build());
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ErrorResponse> handleDatabaseError(DataIntegrityViolationException ex) {
                log.error("Database error");
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ErrorResponse.builder()
                                                .error("DATA_CONFLICT")
                                                .message("Database constraint violation")
                                                .build());
        }

        @ExceptionHandler(JwtException.class)
        public ResponseEntity<ErrorResponse> handleJwt(JwtException ex) {
                log.warn("JWT error");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ErrorResponse.builder()
                                                .error("INVALID_TOKEN")
                                                .message("JWT token is invalid or expired")
                                                .build());
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
                log.warn("Illegal argument: {}", ex.getMessage());
                return ResponseEntity.badRequest()
                                .body(ErrorResponse.builder()
                                                .error("BAD_REQUEST")
                                                .message(ex.getMessage())
                                                .build());
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
                log.error("Unhandled exception occurred", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ErrorResponse.builder()
                                                .error("INTERNAL_ERROR")
                                                .message("Something went wrong")
                                                .build());
        }
}