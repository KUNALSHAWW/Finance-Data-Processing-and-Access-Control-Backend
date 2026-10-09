package com.kunal.finance.backend.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunal.finance.backend.dto.Dtos.ErrorResponse;

import jakarta.servlet.http.HttpServletResponse;

/** Writes the same JSON error body as GlobalExceptionHandler for errors raised inside the security filter chain. */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static void write(HttpServletResponse res, ObjectMapper mapper, HttpStatus status, String code,
            String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ErrorResponse.of(code, message));
    }
}
