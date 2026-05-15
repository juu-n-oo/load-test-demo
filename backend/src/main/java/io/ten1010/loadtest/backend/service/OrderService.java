package io.ten1010.loadtest.backend.service;

import io.ten1010.loadtest.backend.api.exception.BusinessException;
import io.ten1010.loadtest.backend.api.exception.ResourceNotFoundException;
import io.ten1010.loadtest.backend.domain.order.Order;
import io.ten1010.loadtest.backend.domain.order.OrderItem;
import io.ten1010.loadtest.backend.domain.order.OrderRepository;
import io.ten1010.loadtest.backend.domain.order.OrderStatus;
import io.ten1010.loadtest.backend.domain.product.Product;
import io.ten1010.loadtest.backend.domain.product.ProductRepository;
import io.ten1010.loadtest.backend.domain.user.User;
import io.ten1010.loadtest.backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Order create(Long userId, String shippingAddress, Map<Long, Long> productQuantities) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<Product> products = productRepository.findAllById(productQuantities.keySet());

        BigDecimal totalPrice = BigDecimal.ZERO;
        for (Product product : products) {
            Long quantity = productQuantities.get(product.getId());
            if (!product.decreaseStock(quantity)) {
                throw new BusinessException("Insufficient stock for product: " + product.getName());
            }
            totalPrice = totalPrice.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        Order order = Order.builder()
                .user(user)
                .totalPrice(totalPrice)
                .shippingAddress(shippingAddress)
                .build();

        for (Product product : products) {
            Long quantity = productQuantities.get(product.getId());
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(quantity)
                    .purchasePrice(product.getPrice())
                    .build();
            order.addItem(item);
        }

        return orderRepository.save(order);
    }

    public Order getById(Long id) {
        return orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    public Page<Order> getByUser(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable);
    }

    @Transactional
    public Order updateStatus(Long id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        order.updateStatus(status);
        return order;
    }
}
