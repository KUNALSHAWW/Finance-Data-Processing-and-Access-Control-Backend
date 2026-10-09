package com.kunal.finance.backend.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kunal.finance.backend.dto.Dtos.AuthRequest;
import com.kunal.finance.backend.dto.Dtos.AuthResponse;
import com.kunal.finance.backend.entity.User;
import com.kunal.finance.backend.exception.TooManyAttemptsException;
import com.kunal.finance.backend.repository.UserRepository;
import com.kunal.finance.backend.security.JwtUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.security.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.security.lock-minutes:15}")
    private long lockMinutes;

    /** noRollbackFor: the failed-attempt counter must be saved even though we throw. */
    @Transactional(noRollbackFor = { BadCredentialsException.class, DisabledException.class })
    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new TooManyAttemptsException("Too many failed attempts, try again in a few minutes");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= maxFailedAttempts) {
                user.setLockedUntil(now.plusMinutes(lockMinutes));
                user.setFailedAttempts(0);
            }
            throw new BadCredentialsException("Invalid credentials");
        }

        // Checked only after the password is proven, so the response does not reveal which emails are disabled.
        if (!user.isActive()) {
            throw new DisabledException("Account is disabled");
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        return new AuthResponse(jwtUtil.generateToken(user.getEmail()), "Bearer", jwtUtil.getExpirationMs());
    }
}
