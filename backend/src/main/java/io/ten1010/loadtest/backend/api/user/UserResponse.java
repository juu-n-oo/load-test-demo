package io.ten1010.loadtest.backend.api.user;

import io.ten1010.loadtest.backend.domain.user.User;

import java.time.LocalDateTime;

public class UserResponse {

    public record Detail(
            Long id,
            String username,
            String email,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static Detail from(User user) {
            return new Detail(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.isActive(),
                    user.getCreatedAt(),
                    user.getUpdatedAt()
            );
        }
    }

    public record Summary(
            Long id,
            String username,
            String email
    ) {
        public static Summary from(User user) {
            return new Summary(user.getId(), user.getUsername(), user.getEmail());
        }
    }
}
