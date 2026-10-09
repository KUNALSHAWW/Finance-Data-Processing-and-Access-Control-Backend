package com.kunal.finance.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kunal.finance.backend.dto.Dtos.AuthRequest;
import com.kunal.finance.backend.dto.Dtos.AuthResponse;
import com.kunal.finance.backend.dto.Dtos.MeResponse;
import com.kunal.finance.backend.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and token issuance")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Log in",
            description = "Returns a bearer token. Five wrong passwords lock the account for a configurable period.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Who am I? Returns the caller's email and role")
    public ResponseEntity<MeResponse> me(Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replaceFirst("^ROLE_", "");
        return ResponseEntity.ok(new MeResponse(auth.getName(), role));
    }
}
