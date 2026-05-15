package io.ten1010.loadtest.backend.domain.product;

import java.math.BigDecimal;

public record ProductSearchCondition(
        String category,
        String name,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean inStock
) {
    public static ProductSearchCondition of(String category, String name, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {
        return new ProductSearchCondition(category, name, minPrice, maxPrice, inStock);
    }
}
