package com.example.pm.product;

import java.math.BigDecimal;

public record CreateProductRequest(
        String sku,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity) {
}