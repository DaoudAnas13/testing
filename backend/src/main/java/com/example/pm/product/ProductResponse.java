package com.example.pm.product;

import java.math.BigDecimal;
import java.time.Instant;

/** What the API returns. We never expose the JPA entity directly. */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        ProductStatus status,
        Instant createdAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getPrice(), p.getStockQuantity(), p.getStatus(), p.getCreatedAt());
    }
}