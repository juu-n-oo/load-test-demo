package io.ten1010.loadtest.backend.service;

import io.ten1010.loadtest.backend.api.exception.ResourceNotFoundException;
import io.ten1010.loadtest.backend.domain.post.Post;
import io.ten1010.loadtest.backend.domain.post.PostRepository;
import io.ten1010.loadtest.backend.domain.post.PostSearchCondition;
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
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public Post create(Long userId, String title, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return postRepository.save(Post.builder()
                .user(user)
                .title(title)
                .content(content)
                .build());
    }

    @Transactional
    public Post getById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        post.incrementViewCount();
        return post;
    }

    public Page<Post> getAll(String title, String content, Long userId, Pageable pageable) {
        PostSearchCondition condition = PostSearchCondition.of(title, content, userId);
        return postRepository.search(condition, pageable);
    }

    @Transactional
    public Post update(Long id, String title, String content) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        post.update(title, content);
        return post;
    }

    @Transactional
    public void delete(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post", id));
        postRepository.delete(post);
    }
}
