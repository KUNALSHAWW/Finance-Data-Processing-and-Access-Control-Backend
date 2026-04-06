package com.kunal.finance.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kunal.finance.backend.dto.MessageResponse;
import com.kunal.finance.backend.dto.UserRequest;
import com.kunal.finance.backend.dto.UserResponse;
import com.kunal.finance.backend.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name="User Management", description="Endpoints for managing users (Admin only)")
public class UserController {

    private final UserService userService;

    @PreAuthorize("hasRole('ADMIN')") // this must be disabled for creating admin user for the first time and should be enabled later
    @PostMapping
    @Operation(summary = "Create a new user (Admin only)", description = "Creates a new user with the specified details. This endpoint is intended for creating the first admin user and should be protected or removed after initial setup.")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "Get all users (Admin only)", description = "Retrieves a list of all users in the system. This endpoint is restricted to admin users.")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID (Admin only)", description = "Retrieves the details of a specific user by their ID. This endpoint is restricted to admin users.")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a user (Admin only)", description = "Deactivates the user with the specified ID. This endpoint is restricted to admin users.")
    public ResponseEntity<MessageResponse> deactivateUser(@PathVariable Long id) {

        String message = userService.deactivateUser(id);

        return ResponseEntity.ok(
                MessageResponse.builder()
                        .status("SUCCESS")
                        .message(message)
                        .build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/activate")
    @Operation(summary = "Activate a user (Admin only)", description = "Activates the user with the specified ID. This endpoint is restricted to admin users.")
    public ResponseEntity<MessageResponse> activateUser(@PathVariable Long id) {

        String message = userService.activateUser(id);

        return ResponseEntity.ok(
                MessageResponse.builder()
                        .status("SUCCESS")
                        .message(message)
                        .build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user (Admin only)", description = "Deletes the user with the specified ID. This endpoint is restricted to admin users.")
    public ResponseEntity<MessageResponse> deleteUser(@PathVariable Long id) {

        String message = userService.deleteUser(id);

        return ResponseEntity.ok(
                MessageResponse.builder()
                        .status("SUCCESS")
                        .message(message)
                        .build());
    }
}