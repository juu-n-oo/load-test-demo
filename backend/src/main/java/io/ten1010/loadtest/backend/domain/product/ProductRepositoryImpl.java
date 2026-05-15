package io.ten1010.loadtest.backend.domain.product;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.PathBuilder;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Product> search(ProductSearchCondition condition, Pageable pageable) {
        QProduct product = QProduct.product;

        BooleanBuilder where = buildWhereClause(condition, product);
        List<OrderSpecifier<?>> orders = buildOrderSpecifiers(pageable, product);

        List<Product> content = queryFactory
                .selectFrom(product)
                .where(where)
                .orderBy(orders.toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(product.count())
                .from(product)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanBuilder buildWhereClause(ProductSearchCondition condition, QProduct product) {
        BooleanBuilder builder = new BooleanBuilder();

        if (condition.category() != null && !condition.category().isBlank()) {
            builder.and(product.category.eq(condition.category()));
        }
        if (condition.name() != null && !condition.name().isBlank()) {
            builder.and(product.name.containsIgnoreCase(condition.name()));
        }
        if (condition.minPrice() != null) {
            builder.and(product.price.goe(condition.minPrice()));
        }
        if (condition.maxPrice() != null) {
            builder.and(product.price.loe(condition.maxPrice()));
        }
        if (Boolean.TRUE.equals(condition.inStock())) {
            builder.and(product.stock.gt(0L));
        }

        return builder;
    }

    private List<OrderSpecifier<?>> buildOrderSpecifiers(Pageable pageable, QProduct product) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        PathBuilder<Product> path = new PathBuilder<>(Product.class, "product");

        for (Sort.Order sortOrder : pageable.getSort()) {
            Order direction = sortOrder.isAscending() ? Order.ASC : Order.DESC;
            orders.add(new OrderSpecifier<>(direction, path.getComparable(sortOrder.getProperty(), Comparable.class)));
        }

        if (orders.isEmpty()) {
            orders.add(product.id.desc());
        }

        return orders;
    }
}
