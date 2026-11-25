package com.example.pm.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEMPORARY SETUP: no database exists yet, so JPA/DataSource auto-configuration is excluded
 * and the repository is a mock. In Phase 4 this class is rebuilt against a real PostgreSQL
 * (Testcontainers) and the exclusions disappear.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration")
@AutoConfigureMockMvc
class ProductApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductRepository repository;

    @Test
    void rejectsRequestsWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        mockMvc.perform(get("/api/products").with(httpBasic("admin", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanReadProducts() throws Exception {
        when(repository.findAll()).thenReturn(List.of(
                new Product("LAP-001", "Laptop", null, new BigDecimal("999.99"), 10)));

        mockMvc.perform(get("/api/products").with(httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void userCannotCreateProducts() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(httpBasic("user", "user123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"LAP-001","name":"Laptop","price":999.99,"stockQuantity":10}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatingADiscontinuedProductIsRejectedByTheRealBusinessRule() throws Exception {
        Product discontinued = new Product("LAP-001", "Laptop", null, new BigDecimal("100.00"), 10);
        discontinued.setId(1L);
        discontinued.setStatus(ProductStatus.DISCONTINUED);
        when(repository.findById(1L)).thenReturn(Optional.of(discontinued));

        mockMvc.perform(put("/api/products/1")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Laptop","price":100.00,"stockQuantity":10}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Discontinued products cannot be updated"));
    }
}