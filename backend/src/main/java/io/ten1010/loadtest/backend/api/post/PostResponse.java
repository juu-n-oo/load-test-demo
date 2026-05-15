package io.ten1010.loadtest.backend.api.post;

import io.ten1010.loadtest.backend.domain.post.Post;

import java.time.LocalDateTime;

public class PostResponse {

    public record Detail(
            Long id,
            Long userId,
            String username,
            String title,
            String content,
            Long viewCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Detail from(Post post) {
            return new Detail(
                    post.getId(),
                    post.getUser().getId(),
                    post.getUser().getUsername(),
                    post.getTitle(),
                    post.getContent(),
                    post.getViewCount(),
                    post.getCreatedAt(),
                    post.getUpdatedAt()
            );
        }
    }

    public record Summary(
            Long id,
            String username,
            String title,
            Long viewCount,
            LocalDateTime createdAt
    ) {
        public static Summary from(Post post) {
            return new Summary(
                    post.getId(),
                    post.getUser().getUsername(),
                    post.getTitle(),
                    post.getViewCount(),
                    post.getCreatedAt()
            );
        }
    }
}
