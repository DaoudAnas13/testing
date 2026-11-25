package com.example.pm.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceMockitoTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private StockAlertNotifier notifier;

    @InjectMocks
    private ProductService service;

    @Captor
    private ArgumentCaptor<Product> productCaptor;

    // ---------- helpers ----------

    private CreateProductRequest validCreateRequest() {
        return new CreateProductRequest("LAP-001", "Laptop", "14 inch", new BigDecimal("999.99"), 10);
    }

    /** A REAL Product (not a mock). Entities are data, so we never mock them. */
    private Product existingProduct(Long id, String price, int stock) {
        Product product = new Product("LAP-001", "Laptop", null, new BigDecimal(price), stock);
        product.setId(id);
        return product;
    }

    // ================================ CREATE ================================

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("saves a product built from the request")
        void savesProductBuiltFromRequest() {
            // Given: SKU is free; save() echoes back whatever it receives
            when(repository.existsBySku("LAP-001")).thenReturn(false);
            when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            service.create(validCreateRequest());

            // Then: capture what the service handed to the repository
            verify(repository).save(productCaptor.capture());
            Product saved = productCaptor.getValue();
            assertThat(saved.getSku()).isEqualTo("LAP-001");
            assertThat(saved.getName()).isEqualTo("Laptop");
            assertThat(saved.getPrice()).isEqualByComparingTo("999.99");
            assertThat(saved.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("rejects a duplicate SKU and never saves")
        void rejectsDuplicateSku() {
            when(repository.existsBySku("LAP-001")).thenReturn(true);

            assertThatThrownBy(() -> service.create(validCreateRequest()))
                    .isInstanceOf(DuplicateSkuException.class)
                    .hasMessageContaining("LAP-001");

            verify(repository, never()).save(any());
        }
    }

    // ============================== RETRIEVE ================================

    @Nested
    @DisplayName("getById()")
    class Retrieve {

        @Test
        @DisplayName("returns the product when it exists")
        void returnsProductWhenFound() {
            Product product = existingProduct(42L, "10.00", 3);
            when(repository.findById(42L)).thenReturn(Optional.of(product));

            Product result = service.getById(42L);

            assertThat(result).isSameAs(product);
        }

        @Test
        @DisplayName("throws ProductNotFoundException when missing (R8)")
        void throwsWhenMissing() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(99L))
                    .isInstanceOf(ProductNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ================================ UPDATE ================================

    @Nested
    @DisplayName("update(): low-stock alerts")
    class UpdateAlerts {

        @Test
        @DisplayName("notifies when the new stock is below the threshold (R9)")
        void notifiesWhenStockBelowThreshold() {
            Product existing = existingProduct(1L, "100.00", 20);
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);

            service.update(1L, new UpdateProductRequest("Laptop", null, new BigDecimal("100.00"), 4));

            verify(notifier).notifyLowStock(existing);
        }

        @Test
        @DisplayName("does NOT notify when stock equals the threshold (boundary of R9)")
        void doesNotNotifyAtThreshold() {
            Product existing = existingProduct(1L, "100.00", 20);
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);

            service.update(1L, new UpdateProductRequest("Laptop", null, new BigDecimal("100.00"), 5));

            verifyNoInteractions(notifier);
        }

        @Test
        @DisplayName("update still succeeds when the notifier throws (R10)")
        void updateSucceedsWhenNotifierFails() {
            Product existing = existingProduct(1L, "100.00", 20);
            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.save(existing)).thenReturn(existing);
            // void method, so we use doThrow(...).when(mock).method(...)
            doThrow(new RuntimeException("smtp down")).when(notifier).notifyLowStock(any());

            UpdateProductRequest request = new UpdateProductRequest("Laptop", null, new BigDecimal("100.00"), 1);

            assertThatCode(() -> service.update(1L, request)).doesNotThrowAnyException();
            verify(repository).save(existing);
        }
    }

    // ============================ YOUR TURN (2.12) ============================
}