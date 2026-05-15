package io.ten1010.loadtest.backend.domain.order;

public record OrderSearchCondition(
        Long userId,
        OrderStatus status
) {
    public static OrderSearchCondition of(Long userId, OrderStatus status) {
        return new OrderSearchCondition(userId, status);
    }
}
