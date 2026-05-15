package io.ten1010.loadtest.backend.service;

import io.ten1010.loadtest.backend.api.exception.BusinessException;
import io.ten1010.loadtest.backend.api.exception.ResourceNotFoundException;
import io.ten1010.loadtest.backend.domain.user.User;
import io.ten1010.loadtest.backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User create(String username, String email, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("Username already exists: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email already exists: " + email);
        }
        return userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(password)
                .build());
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    public Page<User> getAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional
    public User update(Long id, String email, String password) {
        User user = getById(id);
        user.update(email, password);
        return user;
    }

    @Transactional
    public void delete(Long id) {
        User user = getById(id);
        user.deactivate();
    }
}
