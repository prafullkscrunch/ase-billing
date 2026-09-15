package com.ase.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks every read endpoint and asserts it returns 200 rather than 500.
 *
 * This exists because open-in-view is disabled, so any handler that touches a
 * lazy association outside a transaction throws LazyInitializationException and
 * returns a 500 — a failure no unit test sees, because the entity is still
 * attached there. Three separate endpoints shipped with exactly that fault
 * before this test existed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebEndpointsTest {

    @Autowired private MockMvc mvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("every master-data endpoint resolves its lazy associations")
    void masterDataEndpoints() throws Exception {
        mvc.perform(get("/api/customers")).andExpect(status().isOk());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
        mvc.perform(get("/api/transport-routes")).andExpect(status().isOk());
        // Reads ServiceItem.category, which is lazy.
        mvc.perform(get("/api/services?category=CNF&customerId=1")).andExpect(status().isOk());
        mvc.perform(get("/api/services")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("invoice and report endpoints resolve their lazy associations")
    void reportEndpoints() throws Exception {
        mvc.perform(get("/api/invoices")).andExpect(status().isOk());
        mvc.perform(get("/api/invoices?category=CNF&status=DRAFT")).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the API is closed to anonymous callers")
    void apiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("a missing invoice is a 404, not a 500")
    void missingInvoiceIsNotFound() throws Exception {
        mvc.perform(get("/api/invoices/999999")).andExpect(status().isNotFound());
    }
}
