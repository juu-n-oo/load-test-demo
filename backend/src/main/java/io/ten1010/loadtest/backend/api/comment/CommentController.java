package io.ten1010.loadtest.backend.api.comment;

import io.ten1010.loadtest.backend.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentResponse.Detail> create(
            @PathVariable Long postId,
            @Valid @RequestBody CommentRequest.Create request) {
        var comment = commentService.create(postId, request.userId(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.Detail.from(comment));
    }

    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<Page<CommentResponse.Detail>> getByPost(
            @PathVariable Long postId,
            Pageable pageable) {
        return ResponseEntity.ok(
                commentService.getByPost(postId, pageable).map(CommentResponse.Detail::from));
    }

    @PutMapping("/api/comments/{id}")
    public ResponseEntity<CommentResponse.Detail> update(
            @PathVariable Long id,
            @Valid @RequestBody CommentRequest.Update request) {
        var comment = commentService.update(id, request.content());
        return ResponseEntity.ok(CommentResponse.Detail.from(comment));
    }

    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
