package io.ten1010.loadtest.backend.api.post;

import io.ten1010.loadtest.backend.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostResponse.Detail> create(@Valid @RequestBody PostRequest.Create request) {
        var post = postService.create(request.userId(), request.title(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.Detail.from(post));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse.Detail> getById(@PathVariable Long id) {
        return ResponseEntity.ok(PostResponse.Detail.from(postService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<Page<PostResponse.Summary>> getAll(
            @RequestParam(required = false) String title,
            Pageable pageable) {
        return ResponseEntity.ok(postService.getAll(title, pageable).map(PostResponse.Summary::from));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponse.Detail> update(
            @PathVariable Long id,
            @Valid @RequestBody PostRequest.Update request) {
        var post = postService.update(id, request.title(), request.content());
        return ResponseEntity.ok(PostResponse.Detail.from(post));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
