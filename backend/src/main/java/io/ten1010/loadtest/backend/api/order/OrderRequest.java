package io.ten1010.loadtest.backend.api.order;

import io.ten1010.loadtest.backend.domain.order.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

public class OrderRequest {

    public record Create(
            @NotNull Long userId,
            @NotBlank String shippingAddress,
            @NotEmpty Map<Long, Long> productQuantities
    ) {}

    public record UpdateStatus(
            @NotNull OrderStatus status
    ) {}
}
