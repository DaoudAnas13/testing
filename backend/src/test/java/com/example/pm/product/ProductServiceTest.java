package com.example.pm.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductServiceTest {

    private InMemoryProductRepository repository;
    private ProductService service;

    @BeforeEach
    void setUp() {
        // Fresh objects for EVERY test, so tests can never affect each other
        repository = new InMemoryProductRepository();
        service = new ProductService(repository);
    }

    // ---------- helpers (keep tests short and focused on what matters) ----------

    private CreateProductRequest validCreateRequest() {
        return new CreateProductRequest("LAP-001", "Laptop", "14 inch", new BigDecimal("999.99"), 10);
    }

    private Product givenExistingProduct(String sku, String price, int stock) {
        return repository.save(new Product(sku, "Existing " + sku, null, new BigDecimal(price), stock));
    }

    // ================================ CREATE ================================

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("stores a product with the given values")
        void createsProductWithGivenValues() {
            // Given
            CreateProductRequest request = validCreateRequest();

            // When
            Product created = service.create(request);

            // Then
            assertThat(created.getId()).isNotNull();
            assertThat(created.getSku()).isEqualTo("LAP-001");
            assertThat(created.getName()).isEqualTo("Laptop");
            assertThat(created.getPrice()).isEqualByComparingTo("999.99");
            assertThat(created.getStockQuantity()).isEqualTo(10);
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("new products start as ACTIVE")
        void newProductIsActive() {
            Product created = service.create(validCreateRequest());

            assertThat(created.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("rejects a duplicate SKU (R1)")
        void rejectsDuplicateSku() {
            // Given: a product with this SKU already exists
            service.create(validCreateRequest());

            // When / Then
            assertThatThrownBy(() -> service.create(validCreateRequest()))
                    .isInstanceOf(DuplicateSkuException.class)
                    .hasMessageContaining("LAP-001");
            assertThat(repository.count()).isEqualTo(1); // nothing extra was saved
        }

        @ParameterizedTest(name = "price {0} is rejected")
        @ValueSource(strings = {"0", "-1", "-0.01"})
        @DisplayName("rejects zero or negative prices (R2)")
        void rejectsNonPositivePrice(String price) {
            CreateProductRequest request =
                    new CreateProductRequest("LAP-001", "Laptop", null, new BigDecimal(price), 1);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Price must be greater than zero");
            assertThat(repository.count()).isZero();
        }

        @Test
        @DisplayName("rejects a null price (R2)")
        void rejectsNullPrice() {
            CreateProductRequest request = new CreateProductRequest("LAP-001", "Laptop", null, null, 1);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Price must be greater than zero");
        }

        @ParameterizedTest(name = "name [{0}] is rejected")
        @NullAndEmptySource                  // runs with null AND ""
        @ValueSource(strings = {"   "})      // plus whitespace-only
        @DisplayName("rejects null, empty, or blank names (R3)")
        void rejectsBlankName(String name) {
            CreateProductRequest request =
                    new CreateProductRequest("LAP-001", name, null, new BigDecimal("10.00"), 1);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Name must not be blank");
        }

        @Test
        @DisplayName("rejects negative stock (R4)")
        void rejectsNegativeStock() {
            CreateProductRequest request =
                    new CreateProductRequest("LAP-001", "Laptop", null, new BigDecimal("10.00"), -1);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Stock quantity must not be negative");
        }
    }

    // ============================ YOUR TURN (1.11) ============================


    // ============================ YOUR TURN (1.11) ============================

    @Nested
    @DisplayName("getById() / findAll()")
    class Retrieve {

        @Test
        @DisplayName("returns a product when it exists")
        void returnsProductWhenItExists() {
            // Given
            Product existing = givenExistingProduct("LAP-001", "999.99", 10);

            // When
            Product result = service.getById(existing.getId());

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(existing.getId());
            assertThat(result.getSku()).isEqualTo("LAP-001");
        }

        @Test
        @DisplayName("throws ProductNotFoundException for an unknown ID (R8)")
        void throwsExceptionWhenProductDoesNotExist() {
            // Given
            Long unknownId = 999L;

            // When / Then
            assertThatThrownBy(() -> service.getById(unknownId))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining(unknownId.toString());
        }

        @Test
        @DisplayName("returns an empty list when no products exist")
        void returnsEmptyListWhenNoProductsExist() {
            // Given
            // Repository is empty (initialized in setUp)

            // When
            var products = service.findAll();

            // Then
            assertThat(products).isEmpty();
        }

        @Test
        @DisplayName("returns all stored products")
        void returnsAllProducts() {
            // Given
            givenExistingProduct("LAP-001", "999.99", 10);
            givenExistingProduct("MOU-001", "49.99", 20);
            givenExistingProduct("KEY-001", "79.99", 15);

            // When
            var products = service.findAll();

            // Then
            assertThat(products).hasSize(3);

            assertThat(products)
                    .extracting(Product::getSku)
                    .containsExactly("LAP-001", "MOU-001", "KEY-001");
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("updates all product fields with valid values")
        void updatesProductWithValidValues() {
            // Given
            Product existing = givenExistingProduct("LAP-001", "999.99", 10);

            UpdateProductRequest request = new UpdateProductRequest(
                    "Gaming Laptop",
                    "High-performance gaming laptop",
                    new BigDecimal("1200.00"),
                    25
            );

            // When
            Product updated = service.update(existing.getId(), request);

            // Then
            assertThat(updated.getName()).isEqualTo("Gaming Laptop");
            assertThat(updated.getDescription())
                    .isEqualTo("High-performance gaming laptop");
            assertThat(updated.getPrice()).isEqualByComparingTo("1200.00");
            assertThat(updated.getStockQuantity()).isEqualTo(25);
        }

        @Test
        @DisplayName("throws ProductNotFoundException for an unknown ID")
        void throwsExceptionWhenProductDoesNotExist() {
            // Given
            Long unknownId = 999L;

            UpdateProductRequest request = new UpdateProductRequest(
                    "Gaming Laptop",
                    "Updated description",
                    new BigDecimal("1200.00"),
                    25
            );

            // When / Then
            assertThatThrownBy(() -> service.update(unknownId, request))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining(unknownId.toString());
        }

        @Test
        @DisplayName("rejects updating a discontinued product (R5)")
        void rejectsUpdateOnDiscontinuedProduct() {
            // Given
            Product existing = givenExistingProduct("LAP-001", "999.99", 10);

            existing.setStatus(ProductStatus.DISCONTINUED);
            repository.save(existing);

            UpdateProductRequest request = new UpdateProductRequest(
                    "Gaming Laptop",
                    "Updated description",
                    new BigDecimal("1200.00"),
                    25
            );

            // When / Then
            assertThatThrownBy(() -> service.update(existing.getId(), request))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @ParameterizedTest(name = "new price {0} -> allowed: {1}")
        @CsvSource({
                "150.00, true",
                "149.99, true",
                "150.01, false"
        })
        @DisplayName("enforces the maximum 50% price increase (R6)")
        void validatesMaximumPriceIncrease(String newPrice, boolean allowed) {
            // Given
            Product existing = givenExistingProduct("LAP-001", "100.00", 10);

            UpdateProductRequest request = new UpdateProductRequest(
                    "Updated Laptop",
                    "Updated description",
                    new BigDecimal(newPrice),
                    20
            );

            // When / Then
            if (allowed) {
                Product updated = service.update(existing.getId(), request);

                assertThat(updated.getPrice())
                        .isEqualByComparingTo(newPrice);
            } else {
                assertThatThrownBy(() -> service.update(existing.getId(), request))
                        .isInstanceOf(BusinessRuleException.class);
            }
        }

        @Test
        @DisplayName("allows a price decrease of any size")
        void allowsPriceDecreaseOfAnySize() {
            // Given
            Product existing = givenExistingProduct("LAP-001", "1000.00", 10);

            UpdateProductRequest request = new UpdateProductRequest(
                    "Discounted Laptop",
                    "Updated description",
                    new BigDecimal("1.00"),
                    20
            );

            // When
            Product updated = service.update(existing.getId(), request);

            // Then
            assertThat(updated.getPrice()).isEqualByComparingTo("1.00");
        }

        @Test
        @DisplayName("leaves the product unchanged after a rejected update")
        void leavesProductUnchangedAfterRejectedUpdate() {
            // Given
            Product existing = givenExistingProduct("LAP-001", "100.00", 10);

            UpdateProductRequest request = new UpdateProductRequest(
                    "Changed Laptop",
                    "Changed description",
                    new BigDecimal("150.01"),
                    50
            );

            // When
            assertThatThrownBy(() -> service.update(existing.getId(), request))
                    .isInstanceOf(BusinessRuleException.class);

            // Then
            Product result = service.getById(existing.getId());

            assertThat(result.getName()).isEqualTo("Existing LAP-001");
            assertThat(result.getDescription()).isNull();
            assertThat(result.getPrice()).isEqualByComparingTo("100.00");
            assertThat(result.getStockQuantity()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deletes a product when its stock is zero")
        void deletesProductWithZeroStock() {
            // Given
            Product existing = givenExistingProduct("LAP-001", "999.99", 0);

            assertThat(repository.count()).isEqualTo(1);

            // When
            service.delete(existing.getId());

            // Then
            assertThat(repository.count()).isZero();
        }

        @Test
        @DisplayName("rejects deleting a product with stock greater than zero (R7)")
        void rejectsDeletingProductWithStock() {
            // Given
            Product existing = givenExistingProduct("LAP-001", "999.99", 10);

            // When / Then
            assertThatThrownBy(() -> service.delete(existing.getId()))
                    .isInstanceOf(BusinessRuleException.class);

            // Product must still exist
            assertThat(repository.count()).isEqualTo(1);
            assertThat(service.getById(existing.getId())).isNotNull();
        }

        @Test
        @DisplayName("throws ProductNotFoundException when deleting an unknown product")
        void throwsExceptionWhenProductDoesNotExist() {
            // Given
            Long unknownId = 999L;

            // When / Then
            assertThatThrownBy(() -> service.delete(unknownId))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining(unknownId.toString());
        }
    }

}