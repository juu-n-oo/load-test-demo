package io.ten1010.loadtest.backend.service;

import io.ten1010.loadtest.backend.api.exception.ResourceNotFoundException;
import io.ten1010.loadtest.backend.domain.comment.Comment;
import io.ten1010.loadtest.backend.domain.comment.CommentRepository;
import io.ten1010.loadtest.backend.domain.post.Post;
import io.ten1010.loadtest.backend.domain.post.PostRepository;
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
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public Comment create(Long postId, Long userId, String content) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return commentRepository.save(Comment.builder()
                .post(post)
                .user(user)
                .content(content)
                .build());
    }

    public Page<Comment> getByPost(Long postId, Pageable pageable) {
        return commentRepository.findByPostId(postId, pageable);
    }

    @Transactional
    public Comment update(Long id, String content) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id));
        comment.update(content);
        return comment;
    }

    @Transactional
    public void delete(Long id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id));
        commentRepository.delete(comment);
    }
}
