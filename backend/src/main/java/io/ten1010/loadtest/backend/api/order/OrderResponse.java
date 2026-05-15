package io.ten1010.loadtest.backend.api.order;

import io.ten1010.loadtest.backend.domain.order.Order;
import io.ten1010.loadtest.backend.domain.order.OrderItem;
import io.ten1010.loadtest.backend.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderResponse {

    public record ItemDetail(
            Long id,
            Long productId,
            String productName,
            Long quantity,
            BigDecimal purchasePrice
    ) {
        public static ItemDetail from(OrderItem item) {
            return new ItemDetail(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProduct().getName(),
                    item.getQuantity(),
                    item.getPurchasePrice()
            );
        }
    }

    public record Detail(
            Long id,
            Long userId,
            String username,
            BigDecimal totalPrice,
            String shippingAddress,
            OrderStatus status,
            List<ItemDetail> items,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Detail from(Order order) {
            return new Detail(
                    order.getId(),
                    order.getUser().getId(),
                    order.getUser().getUsername(),
                    order.getTotalPrice(),
                    order.getShippingAddress(),
                    order.getStatus(),
                    order.getItems().stream().map(ItemDetail::from).toList(),
                    order.getCreatedAt(),
                    order.getUpdatedAt()
            );
        }
    }

    public record Summary(
            Long id,
            BigDecimal totalPrice,
            OrderStatus status,
            LocalDateTime createdAt
    ) {
        public static Summary from(Order order) {
            return new Summary(
                    order.getId(),
                    order.getTotalPrice(),
                    order.getStatus(),
                    order.getCreatedAt()
            );
        }
    }
}
