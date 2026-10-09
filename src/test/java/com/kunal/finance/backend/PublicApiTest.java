package com.kunal.finance.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** With demo mode off (the default), none of the demo machinery is reachable. */
class PublicApiTest extends IntegrationTestBase {

    @Test
    void configSaysNotDemoAndExposesNoAccounts() throws Exception {
        call(HttpMethod.GET, "/api/public/config", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(false))
                .andExpect(jsonPath("$.accounts.length()").value(0));
    }

    @Test
    void demoEndpointsDoNotExist() throws Exception {
        call(HttpMethod.POST, "/api/demo/tamper", token(ADMIN), null).andExpect(status().isNotFound());
        call(HttpMethod.POST, "/api/demo/reset", token(ADMIN), null).andExpect(status().isNotFound());
    }

    @Test
    void userManagementIsNotRestrictedOutsideDemo() throws Exception {
        call(HttpMethod.POST, "/api/users", token(ADMIN),
                "{\"name\":\"N\",\"email\":\"ok@t.com\",\"password\":\"Passw0rd@1\",\"role\":\"VIEWER\"}")
                .andExpect(status().isCreated());
    }
}
