package io.ten1010.loadtest.backend.api.order;

import io.ten1010.loadtest.backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse.Detail> create(@Valid @RequestBody OrderRequest.Create request) {
        var order = orderService.create(
                request.userId(), request.shippingAddress(), request.productQuantities());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.Detail.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse.Detail> getById(@PathVariable Long id) {
        return ResponseEntity.ok(OrderResponse.Detail.from(orderService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse.Summary>> getByUser(
            @RequestParam Long userId,
            Pageable pageable) {
        return ResponseEntity.ok(
                orderService.getByUser(userId, pageable).map(OrderResponse.Summary::from));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponse.Detail> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderRequest.UpdateStatus request) {
        var order = orderService.updateStatus(id, request.status());
        return ResponseEntity.ok(OrderResponse.Detail.from(order));
    }
}
