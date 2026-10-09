package com.kunal.finance.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.kunal.finance.backend.dto.Dtos;
import com.kunal.finance.backend.dto.Dtos.UserRequest;
import com.kunal.finance.backend.entity.Role;
import com.kunal.finance.backend.repository.UserRepository;
import com.kunal.finance.backend.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Solves the "who creates the first admin" problem without editing code: on an empty users table, creates one admin
 * from BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD. Does nothing once any user exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UserRepository users;
    private final UserService userService;

    @Value("${app.bootstrap.admin-email:}")
    private String email;

    @Value("${app.bootstrap.admin-password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0 || email.isBlank() || password.isBlank()) {
            return;
        }
        if (!password.matches(Dtos.PASSWORD_PATTERN)) {
            log.error("BOOTSTRAP_ADMIN_PASSWORD does not meet the password policy; no admin created");
            return;
        }
        userService.create(new UserRequest("Administrator", email, password, Role.ADMIN), "system");
        log.info("Bootstrap admin created: {}", email);
    }
}
