package io.ten1010.loadtest.backend.domain.product;

import io.ten1010.loadtest.backend.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "name", "price"})
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Long stock;

    @Column(nullable = false, length = 100)
    private String category;

    @Builder
    public Product(String name, String description, BigDecimal price, Long stock, String category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.category = category;
    }

    public void update(String name, String description, BigDecimal price, Long stock, String category) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (price != null) this.price = price;
        if (stock != null) this.stock = stock;
        if (category != null) this.category = category;
    }

    public boolean decreaseStock(long quantity) {
        if (this.stock < quantity) return false;
        this.stock -= quantity;
        return true;
    }
}
