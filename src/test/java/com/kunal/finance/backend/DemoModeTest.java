package com.kunal.finance.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kunal.finance.backend.audit.AuditService;
import com.kunal.finance.backend.audit.AuditVerification;
import com.kunal.finance.backend.audit.AuditVerification.Finding;

/** The public live demo: seeded at start, tamperable on purpose, restorable, and safe to leave open to strangers. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:demotest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.demo.enabled=true" })
class DemoModeTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditService audit;

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private String admin() throws Exception {
        return login("admin@demo.example", "Demo@Admin1");
    }

    @Test
    void isSeededAtStartupWithAConsistentLedger() {
        assertEquals(3, jdbc.queryForObject("select count(*) from users", Integer.class));
        Integer records = jdbc.queryForObject("select count(*) from financial_records", Integer.class);
        assertTrue(records > 100 && records < 400, "records=" + records);
        assertTrue(audit.verify().valid());
    }

    @Test
    void publicConfigAdvertisesTheSampleAccountsAndTheyWork() throws Exception {
        String body = mvc.perform(get("/api/public/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true)).andReturn().getResponse().getContentAsString();
        JsonNode accounts = json.readTree(body).get("accounts");
        assertEquals(3, accounts.size());
        for (JsonNode a : accounts) {
            login(a.get("email").asText(), a.get("password").asText());
        }
    }

    @Test
    void tamperingIsCaughtByVerifyAndResetRestoresTheLedger() throws Exception {
        String token = admin();
        assertTrue(audit.verify().valid());

        String result = mvc.perform(post("/api/demo/tamper").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sql").exists())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(result).get("recordId").asLong();

        AuditVerification v = audit.verify();
        assertFalse(v.valid());
        assertTrue(v.findings().contains(new Finding("RECORD", id, "MODIFIED")), v.findings().toString());

        mvc.perform(post("/api/demo/reset").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertTrue(audit.verify().valid());
        assertEquals(3, jdbc.queryForObject("select count(*) from users", Integer.class));
    }

    @Test
    void strangersCannotTamperOrBreakTheSampleAccounts() throws Exception {
        String viewer = login("viewer@demo.example", "Demo@Viewer1");
        mvc.perform(post("/api/demo/tamper").header("Authorization", "Bearer " + viewer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/demo/tamper")).andExpect(status().isUnauthorized());

        String admin = admin();
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"email\":\"x@t.com\",\"password\":\"Passw0rd@1\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("DEMO_RESTRICTED"));
        Long viewerId = jdbc.queryForObject("select id from users where email = 'viewer@demo.example'", Long.class);
        mvc.perform(put("/api/users/" + viewerId + "/deactivate").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
    }

    @Test
    void strangersCannotLockTheSampleAccountsOut() throws Exception {
        for (int i = 0; i < 8; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"admin@demo.example\",\"password\":\"nope\"}")).andExpect(status().isUnauthorized());
        }
        admin(); // still works
    }

    @Test
    void visitCounterIsAnonymousAndRejectsOversizedLabels() throws Exception {
        mvc.perform(post("/api/public/visit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"resume\",\"campaign\":\"acme\\ninjected line\",\"path\":\"/\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/public/visit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"" + "x".repeat(101) + "\"}")).andExpect(status().isBadRequest());
    }
}
