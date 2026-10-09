package com.kunal.finance.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunal.finance.backend.dto.Dtos.FinancialRecordRequest;
import com.kunal.finance.backend.dto.Dtos.UserRequest;
import com.kunal.finance.backend.entity.RecordType;
import com.kunal.finance.backend.entity.Role;
import com.kunal.finance.backend.service.FinancialRecordService;
import com.kunal.finance.backend.service.UserService;

@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    static final String PASSWORD = "Passw0rd@1";
    static final String ADMIN = "admin@test.com";
    static final String ANALYST = "analyst@test.com";
    static final String VIEWER = "viewer@test.com";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired FinancialRecordService records;

    /** Fresh database and a fresh audit chain for every test. */
    @BeforeEach
    void resetDatabase() {
        jdbc.update("delete from audit_log");
        jdbc.update("update audit_head set last_seq = 0, last_hash = '" + "0".repeat(64) + "' where id = 1");
        jdbc.update("delete from financial_records");
        jdbc.update("delete from users");
        user(ADMIN, Role.ADMIN);
        user(ANALYST, Role.ANALYST);
        user(VIEWER, Role.VIEWER);
    }

    Long user(String email, Role role) {
        return users.create(new UserRequest("Test " + role, email, PASSWORD, role), "system").id();
    }

    Long record(String amount, RecordType type, String category, LocalDate date) {
        return records.create(new FinancialRecordRequest(new BigDecimal(amount), type, category, date, null), ADMIN).id();
    }

    String token(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("email", email, "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    ResultActions call(HttpMethod method, String url, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder b = request(method, url);
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
        }
        return mvc.perform(b);
    }
}
