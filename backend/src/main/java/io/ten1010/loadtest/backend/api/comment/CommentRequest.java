package io.ten1010.loadtest.backend.api.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CommentRequest {

    public record Create(
            @NotNull Long userId,
            @NotBlank String content
    ) {}

    public record Update(
            @NotBlank String content
    ) {}
}
