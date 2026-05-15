package io.ten1010.loadtest.backend.api.product;

import io.ten1010.loadtest.backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse.Detail> create(@Valid @RequestBody ProductRequest.Create request) {
        var product = productService.create(
                request.name(), request.description(), request.price(), request.stock(), request.category());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.Detail.from(product));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse.Detail> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ProductResponse.Detail.from(productService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse.Summary>> getAll(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String name,
            Pageable pageable) {
        return ResponseEntity.ok(
                productService.getAll(category, name, pageable).map(ProductResponse.Summary::from));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse.Detail> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest.Update request) {
        var product = productService.update(
                id, request.name(), request.description(), request.price(), request.stock(), request.category());
        return ResponseEntity.ok(ProductResponse.Detail.from(product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
