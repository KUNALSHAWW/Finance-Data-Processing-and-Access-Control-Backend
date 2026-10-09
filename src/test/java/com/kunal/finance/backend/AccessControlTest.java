package com.kunal.finance.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;

/** The README's role matrix, one row per cell, so docs and code cannot drift apart again. */
class AccessControlTest extends IntegrationTestBase {

    static Stream<Arguments> matrix() {
        String record = "{\"amount\":10.00,\"type\":\"INCOME\",\"category\":\"Salary\"}";
        String newUser = "{\"name\":\"N\",\"email\":\"%s@t.com\",\"password\":\"Passw0rd@1\",\"role\":\"VIEWER\"}";
        return Stream.of(
                // view records
                Arguments.of(ADMIN, HttpMethod.GET, "/api/records", null, 200),
                Arguments.of(ANALYST, HttpMethod.GET, "/api/records", null, 200),
                Arguments.of(VIEWER, HttpMethod.GET, "/api/records", null, 200),
                // dashboard summary and trends
                Arguments.of(ADMIN, HttpMethod.GET, "/api/dashboard/summary", null, 200),
                Arguments.of(ANALYST, HttpMethod.GET, "/api/dashboard/summary", null, 200),
                Arguments.of(VIEWER, HttpMethod.GET, "/api/dashboard/summary", null, 200),
                Arguments.of(VIEWER, HttpMethod.GET, "/api/dashboard/trends", null, 200),
                // insights and audit: analysts and admins only
                Arguments.of(ADMIN, HttpMethod.GET, "/api/dashboard/insights", null, 200),
                Arguments.of(ANALYST, HttpMethod.GET, "/api/dashboard/insights", null, 200),
                Arguments.of(VIEWER, HttpMethod.GET, "/api/dashboard/insights", null, 403),
                Arguments.of(ADMIN, HttpMethod.GET, "/api/audit/verify", null, 200),
                Arguments.of(ANALYST, HttpMethod.GET, "/api/audit/verify", null, 200),
                Arguments.of(VIEWER, HttpMethod.GET, "/api/audit/verify", null, 403),
                // writes: admin only
                Arguments.of(ADMIN, HttpMethod.POST, "/api/records", record, 201),
                Arguments.of(ANALYST, HttpMethod.POST, "/api/records", record, 403),
                Arguments.of(VIEWER, HttpMethod.POST, "/api/records", record, 403),
                Arguments.of(ANALYST, HttpMethod.PUT, "/api/records/999", record, 403),
                Arguments.of(VIEWER, HttpMethod.PUT, "/api/records/999", record, 403),
                Arguments.of(ANALYST, HttpMethod.DELETE, "/api/records/999", null, 403),
                Arguments.of(VIEWER, HttpMethod.DELETE, "/api/records/999", null, 403),
                // user management: admin only
                Arguments.of(ADMIN, HttpMethod.GET, "/api/users", null, 200),
                Arguments.of(ANALYST, HttpMethod.GET, "/api/users", null, 403),
                Arguments.of(VIEWER, HttpMethod.GET, "/api/users", null, 403),
                Arguments.of(ADMIN, HttpMethod.POST, "/api/users", newUser.formatted(UUID.randomUUID()), 201),
                Arguments.of(ANALYST, HttpMethod.POST, "/api/users", newUser.formatted("x"), 403),
                Arguments.of(VIEWER, HttpMethod.POST, "/api/users", newUser.formatted("y"), 403));
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {4}")
    @MethodSource("matrix")
    void roleMatrix(String email, HttpMethod method, String url, String body, int expected) throws Exception {
        call(method, url, token(email), body).andExpect(status().is(expected));
    }

    @Test
    void meReturnsTheCallersRole() throws Exception {
        call(HttpMethod.GET, "/api/auth/me", token(ANALYST), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(ANALYST))
                .andExpect(jsonPath("$.role").value("ANALYST"));
        call(HttpMethod.GET, "/api/auth/me", null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void missingTokenIs401WithJsonBody() throws Exception {
        call(HttpMethod.GET, "/api/records", null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void garbageTokenIs401AndSaysWhy() throws Exception {
        call(HttpMethod.GET, "/api/records", "not.a.jwt", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token is invalid or expired"));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", ADMIN, "password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUserLosesAccessImmediatelyAndCannotLogIn() throws Exception {
        String viewerToken = token(VIEWER);
        call(HttpMethod.GET, "/api/records", viewerToken, null).andExpect(status().isOk());

        Long viewerId = jdbc.queryForObject("select id from users where email = ?", Long.class, VIEWER);
        call(HttpMethod.PUT, "/api/users/" + viewerId + "/deactivate", token(ADMIN), null).andExpect(status().isOk());

        // the token issued before deactivation is dead
        call(HttpMethod.GET, "/api/records", viewerToken, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account is disabled"));
        // right password: told the account is disabled; wrong password: no hint the account exists
        call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", VIEWER, "password", PASSWORD))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
        call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", VIEWER, "password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fiveWrongPasswordsLockTheAccountEvenAgainstTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", ANALYST, "password", "wrong"))
                    .andExpect(status().isUnauthorized());
        }
        call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", ANALYST, "password", PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", ANALYST, "password", "wrong"));
        }
        token(ANALYST); // success resets the counter
        for (int i = 0; i < 4; i++) {
            call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", ANALYST, "password", "wrong"))
                    .andExpect(status().isUnauthorized());
        }
        call(HttpMethod.POST, "/api/auth/login", null, Map.of("email", ANALYST, "password", PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void userRules() throws Exception {
        String admin = token(ADMIN);
        String body = "{\"name\":\"D\",\"email\":\"ADMIN@test.com\",\"password\":\"Passw0rd@1\",\"role\":\"VIEWER\"}";
        call(HttpMethod.POST, "/api/users", admin, body).andExpect(status().isConflict()); // email is case-insensitive

        String noRole = "{\"name\":\"D\",\"email\":\"n@t.com\",\"password\":\"Passw0rd@1\"}";
        call(HttpMethod.POST, "/api/users", admin, noRole)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.role").exists());

        Long adminId = jdbc.queryForObject("select id from users where email = ?", Long.class, ADMIN);
        call(HttpMethod.PUT, "/api/users/" + adminId + "/deactivate", admin, null).andExpect(status().isConflict());
        call(HttpMethod.DELETE, "/api/users/" + adminId, admin, null).andExpect(status().isConflict());

        // a user who owns records cannot be deleted (deactivate instead); one who owns none can
        Long owner = user("owner@test.com", com.kunal.finance.backend.entity.Role.ADMIN);
        records.create(new com.kunal.finance.backend.dto.Dtos.FinancialRecordRequest(
                new java.math.BigDecimal("10.00"), com.kunal.finance.backend.entity.RecordType.INCOME, null, null, null),
                "owner@test.com");
        call(HttpMethod.DELETE, "/api/users/" + owner, admin, null).andExpect(status().isConflict());
        Long spare = user("spare@test.com", com.kunal.finance.backend.entity.Role.VIEWER);
        call(HttpMethod.DELETE, "/api/users/" + spare, admin, null).andExpect(status().isOk());

        call(HttpMethod.PUT, "/api/users/9999/deactivate", admin, null).andExpect(status().isNotFound());
    }
}
