package io.ten1010.loadtest.backend.api.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class ProductRequest {

    public record Create(
            @NotBlank String name,
            String description,
            @NotNull @Positive BigDecimal price,
            @NotNull @PositiveOrZero Long stock,
            @NotBlank String category
    ) {}

    public record Update(
            String name,
            String description,
            @Positive BigDecimal price,
            @PositiveOrZero Long stock,
            String category
    ) {}
}
