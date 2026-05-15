package io.ten1010.loadtest.backend.service;

import io.ten1010.loadtest.backend.api.exception.ResourceNotFoundException;
import io.ten1010.loadtest.backend.domain.product.Product;
import io.ten1010.loadtest.backend.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public Product create(String name, String description, BigDecimal price, Long stock, String category) {
        return productRepository.save(Product.builder()
                .name(name)
                .description(description)
                .price(price)
                .stock(stock)
                .category(category)
                .build());
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    public Page<Product> getAll(String category, String name, Pageable pageable) {
        if (category != null && !category.isBlank()) {
            return productRepository.findByCategory(category, pageable);
        }
        if (name != null && !name.isBlank()) {
            return productRepository.findByNameContainingIgnoreCase(name, pageable);
        }
        return productRepository.findAll(pageable);
    }

    @Transactional
    public Product update(Long id, String name, String description, BigDecimal price, Long stock, String category) {
        Product product = getById(id);
        product.update(name, description, price, stock, category);
        return product;
    }

    @Transactional
    public void delete(Long id) {
        Product product = getById(id);
        productRepository.delete(product);
    }
}
