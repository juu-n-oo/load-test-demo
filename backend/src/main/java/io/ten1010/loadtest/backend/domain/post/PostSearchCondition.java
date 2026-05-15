package io.ten1010.loadtest.backend.domain.post;

public record PostSearchCondition(
        String title,
        String content,
        Long userId
) {
    public static PostSearchCondition of(String title, String content, Long userId) {
        return new PostSearchCondition(title, content, userId);
    }
}
