package io.ten1010.loadtest.backend.api.comment;

import io.ten1010.loadtest.backend.domain.comment.Comment;

import java.time.LocalDateTime;

public class CommentResponse {

    public record Detail(
            Long id,
            Long postId,
            Long userId,
            String username,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Detail from(Comment comment) {
            return new Detail(
                    comment.getId(),
                    comment.getPost().getId(),
                    comment.getUser().getId(),
                    comment.getUser().getUsername(),
                    comment.getContent(),
                    comment.getCreatedAt(),
                    comment.getUpdatedAt()
            );
        }
    }
}
