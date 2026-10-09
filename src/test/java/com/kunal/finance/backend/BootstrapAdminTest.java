package com.kunal.finance.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.kunal.finance.backend.audit.AuditService;

/** The first admin is created from environment config on an empty database, with no code edits. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:bootstrap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.bootstrap.admin-email=First.Admin@Example.com",
        "app.bootstrap.admin-password=Str0ng@Passw0rd" })
class BootstrapAdminTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditService audit;

    @Test
    void createsExactlyOneAuditedAdminWhoCanLogIn() throws Exception {
        assertEquals(1, jdbc.queryForObject("select count(*) from users", Integer.class));
        assertEquals("ADMIN", jdbc.queryForObject("select role from users", String.class));
        assertEquals("first.admin@example.com", jdbc.queryForObject("select email from users", String.class));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"first.admin@example.com\",\"password\":\"Str0ng@Passw0rd\"}"))
                .andExpect(status().isOk());

        assertEquals(true, audit.verify().valid());   // the bootstrap itself is in the ledger
    }
}
