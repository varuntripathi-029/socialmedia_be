package com.socialmedia.app.service;

import com.socialmedia.app.exception.BadRequestException;
import com.socialmedia.app.exception.ResourceNotFoundException;
import com.socialmedia.app.model.Like;
import com.socialmedia.app.model.Post;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.LikeRepository;
import com.socialmedia.app.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final UserService userService;

    @Transactional
    public void likePost(Long postId) {
        User currentUser = userService.getCurrentUser();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (likeRepository.existsByUserIdAndPostId(currentUser.getId(), postId)) {
            throw new BadRequestException("You already liked this post");
        }

        Like like = Like.builder()
                .user(currentUser)
                .post(post)
                .build();

        likeRepository.save(like);
    }

    @Transactional
    public void unlikePost(Long postId) {
        User currentUser = userService.getCurrentUser();

        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post not found");
        }

        if (!likeRepository.existsByUserIdAndPostId(currentUser.getId(), postId)) {
            throw new BadRequestException("You haven't liked this post");
        }

        likeRepository.deleteByUserIdAndPostId(currentUser.getId(), postId);
    }

    public Long getLikesCount(Long postId) {
        return likeRepository.countByPostId(postId);
    }

    public Boolean isPostLikedByUser(Long postId, Long userId) {
        return likeRepository.existsByUserIdAndPostId(userId, postId);
    }
}