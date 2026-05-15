package io.ten1010.loadtest.backend.api.user;

import io.ten1010.loadtest.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse.Detail> create(@Valid @RequestBody UserRequest.Create request) {
        var user = userService.create(request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.Detail.from(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse.Detail> getById(@PathVariable Long id) {
        return ResponseEntity.ok(UserResponse.Detail.from(userService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse.Detail> update(
            @PathVariable Long id,
            @Valid @RequestBody UserRequest.Update request) {
        var user = userService.update(id, request.email(), request.password());
        return ResponseEntity.ok(UserResponse.Detail.from(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
