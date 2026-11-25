package com.example.pm.product;

import com.example.pm.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)      // slices do NOT scan @Configuration classes, so we import it explicitly
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean                       // replaces ProductService with a Mockito mock inside the Spring context
    private ProductService service;

    // ---------- helpers ----------

    private Product product(long id, String sku, String name, String price, int stock) {
        Product p = new Product(sku, name, null, new BigDecimal(price), stock);
        p.setId(id);
        return p;
    }

    // ================================ READ ================================

    @Nested
    @DisplayName("GET /api/products")
    class Read {

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("returns 200 and a JSON array of products")
        void listsProducts() throws Exception {
            when(service.findAll()).thenReturn(List.of(
                    product(1, "LAP-001", "Laptop", "999.99", 10),
                    product(2, "MOU-001", "Mouse", "19.90", 100)));

            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].sku").value("LAP-001"))
                    .andExpect(jsonPath("$[1].name").value("Mouse"));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("GET /{id} returns the product as JSON")
        void returnsProductById() throws Exception {
            when(service.getById(1L)).thenReturn(product(1, "LAP-001", "Laptop", "999.99", 10));

            mockMvc.perform(get("/api/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.price").value(999.99))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("GET /{id} returns 404 with a problem body when the product is missing")
        void returns404WhenMissing() throws Exception {
            when(service.getById(99L)).thenThrow(new ProductNotFoundException(99L));

            mockMvc.perform(get("/api/products/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Product not found"))
                    .andExpect(jsonPath("$.detail", containsString("99")));
        }
    }

    // ================================ CREATE ================================

    @Nested
    @DisplayName("POST /api/products")
    class Create {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 201, a Location header, and passes the parsed JSON to the service")
        void createsProduct() throws Exception {
            when(service.create(any(CreateProductRequest.class)))
                    .thenReturn(product(7, "LAP-001", "Laptop", "999.99", 10));

            String body = """
                    {"sku":"LAP-001","name":"Laptop","description":"14 inch","price":999.99,"stockQuantity":10}
                    """;

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/api/products/7")))
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.sku").value("LAP-001"));

            // Proves JSON -> Java deserialization worked as intended
            ArgumentCaptor<CreateProductRequest> captor = ArgumentCaptor.forClass(CreateProductRequest.class);
            verify(service).create(captor.capture());
            assertThat(captor.getValue().sku()).isEqualTo("LAP-001");
            assertThat(captor.getValue().price()).isEqualByComparingTo("999.99");
            assertThat(captor.getValue().stockQuantity()).isEqualTo(10);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 400 with field errors for an invalid body, and never calls the service")
        void rejectsInvalidBody() throws Exception {
            String body = """
                    {"sku":"","name":"   ","price":-5,"stockQuantity":-1}
                    """;

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.errors.sku").exists())
                    .andExpect(jsonPath("$.errors.name").exists())
                    .andExpect(jsonPath("$.errors.price").exists())
                    .andExpect(jsonPath("$.errors.stockQuantity").exists());

            verifyNoInteractions(service);   // validation stops the request BEFORE the controller body runs
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 400 for malformed JSON")
        void rejectsMalformedJson() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ this is not json"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(service);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 409 when the service reports a duplicate SKU")
        void returns409OnDuplicateSku() throws Exception {
            when(service.create(any(CreateProductRequest.class)))
                    .thenThrow(new DuplicateSkuException("LAP-001"));

            String body = """
                    {"sku":"LAP-001","name":"Laptop","price":999.99,"stockQuantity":10}
                    """;

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail", containsString("LAP-001")));
        }
    }

    // ================================ SECURITY ================================

    @Nested
    @DisplayName("security rules")
    class Security {

        @Test
        @DisplayName("anonymous requests get 401 and a Basic challenge")
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists("WWW-Authenticate"));

            verifyNoInteractions(service);
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("a USER cannot create products: 403, and the service is never reached")
        void userCannotCreate() throws Exception {
            String body = """
                    {"sku":"LAP-001","name":"Laptop","price":999.99,"stockQuantity":10}
                    """;

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(service);
        }
    }

    // ============================ YOUR TURN (3.11) ============================
}