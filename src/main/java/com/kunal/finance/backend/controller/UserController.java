package com.kunal.finance.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kunal.finance.backend.dto.Dtos.MessageResponse;
import com.kunal.finance.backend.dto.Dtos.PaginatedResponse;
import com.kunal.finance.backend.dto.Dtos.UserRequest;
import com.kunal.finance.backend.dto.Dtos.UserResponse;
import com.kunal.finance.backend.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Management", description = "Admin only. The first admin comes from BOOTSTRAP_ADMIN_* environment variables.")
public class UserController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create a user")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request, auth.getName()));
    }

    @GetMapping
    @Operation(summary = "List users (paginated)")
    public ResponseEntity<PaginatedResponse<UserResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(userService.list(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user")
    public ResponseEntity<UserResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(userService.get(id));
    }

    @PutMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a user", description = "Takes effect immediately: existing tokens stop working because the active flag is checked on every request. You cannot deactivate yourself.")
    public ResponseEntity<MessageResponse> deactivate(@PathVariable Long id, Authentication auth) {
        return ok(userService.deactivate(id, auth.getName()));
    }

    @PutMapping("/{id}/activate")
    @Operation(summary = "Activate a user")
    public ResponseEntity<MessageResponse> activate(@PathVariable Long id, Authentication auth) {
        return ok(userService.activate(id, auth.getName()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user", description = "Refused for yourself and for users who own financial records (deactivate instead).")
    public ResponseEntity<MessageResponse> delete(@PathVariable Long id, Authentication auth) {
        return ok(userService.delete(id, auth.getName()));
    }

    private static ResponseEntity<MessageResponse> ok(String message) {
        return ResponseEntity.ok(new MessageResponse("SUCCESS", message));
    }
}
