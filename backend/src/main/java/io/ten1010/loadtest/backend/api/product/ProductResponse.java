package io.ten1010.loadtest.backend.api.product;

import io.ten1010.loadtest.backend.domain.product.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductResponse {

    public record Detail(
            Long id,
            String name,
            String description,
            BigDecimal price,
            Long stock,
            String category,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Detail from(Product product) {
            return new Detail(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getPrice(),
                    product.getStock(),
                    product.getCategory(),
                    product.getCreatedAt(),
                    product.getUpdatedAt()
            );
        }
    }

    public record Summary(
            Long id,
            String name,
            BigDecimal price,
            Long stock,
            String category
    ) {
        public static Summary from(Product product) {
            return new Summary(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    product.getStock(),
                    product.getCategory()
            );
        }
    }
}
