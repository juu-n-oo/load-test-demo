package io.ten1010.loadtest.backend.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderRepositoryCustom {

    Page<Order> search(OrderSearchCondition condition, Pageable pageable);
}
