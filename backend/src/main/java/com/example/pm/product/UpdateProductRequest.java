package com.example.pm.product;

import java.math.BigDecimal;

public record UpdateProductRequest(
        String name,
        String description,
        BigDecimal price,
        int stockQuantity) {
}