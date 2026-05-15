package io.ten1010.loadtest.backend.domain.order;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Order> search(OrderSearchCondition condition, Pageable pageable) {
        QOrder order = QOrder.order;

        BooleanBuilder where = buildWhereClause(condition, order);

        List<Order> content = queryFactory
                .selectFrom(order)
                .where(where)
                .orderBy(order.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(order.count())
                .from(order)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanBuilder buildWhereClause(OrderSearchCondition condition, QOrder order) {
        BooleanBuilder builder = new BooleanBuilder();

        if (condition.userId() != null) {
            builder.and(order.user.id.eq(condition.userId()));
        }
        if (condition.status() != null) {
            builder.and(order.status.eq(condition.status()));
        }

        return builder;
    }
}
