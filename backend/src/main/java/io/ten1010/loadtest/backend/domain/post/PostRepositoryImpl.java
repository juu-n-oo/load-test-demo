package io.ten1010.loadtest.backend.domain.post;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> search(PostSearchCondition condition, Pageable pageable) {
        QPost post = QPost.post;

        BooleanBuilder where = buildWhereClause(condition, post);
        List<OrderSpecifier<?>> orders = buildOrderSpecifiers(pageable, post);

        List<Post> content = queryFactory
                .selectFrom(post)
                .where(where)
                .orderBy(orders.toArray(new OrderSpecifier[0]))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanBuilder buildWhereClause(PostSearchCondition condition, QPost post) {
        BooleanBuilder builder = new BooleanBuilder();

        if (condition.title() != null && !condition.title().isBlank()) {
            builder.and(post.title.containsIgnoreCase(condition.title()));
        }
        if (condition.content() != null && !condition.content().isBlank()) {
            builder.and(post.content.containsIgnoreCase(condition.content()));
        }
        if (condition.userId() != null) {
            builder.and(post.user.id.eq(condition.userId()));
        }

        return builder;
    }

    private List<OrderSpecifier<?>> buildOrderSpecifiers(Pageable pageable, QPost post) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        PathBuilder<Post> path = new PathBuilder<>(Post.class, "post");

        for (Sort.Order sortOrder : pageable.getSort()) {
            Order direction = sortOrder.isAscending() ? Order.ASC : Order.DESC;
            orders.add(new OrderSpecifier<>(direction, path.getComparable(sortOrder.getProperty(), Comparable.class)));
        }

        if (orders.isEmpty()) {
            orders.add(post.id.desc());
        }

        return orders;
    }
}
