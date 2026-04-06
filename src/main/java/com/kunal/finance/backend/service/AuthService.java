package com.kunal.finance.backend.service;

import com.kunal.finance.backend.dto.AuthRequest;
import com.kunal.finance.backend.dto.AuthResponse;

public interface AuthService {
    AuthResponse login(AuthRequest request);
}