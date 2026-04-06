package com.kunal.finance.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank
    private String email;
    @NotBlank
    private String password;
}