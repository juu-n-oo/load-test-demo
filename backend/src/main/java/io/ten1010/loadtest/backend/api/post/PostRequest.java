package io.ten1010.loadtest.backend.api.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PostRequest {

    public record Create(
            @NotNull Long userId,
            @NotBlank String title,
            @NotBlank String content
    ) {}

    public record Update(
            String title,
            String content
    ) {}
}
