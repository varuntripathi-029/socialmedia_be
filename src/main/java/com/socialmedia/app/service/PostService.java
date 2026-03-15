package com.socialmedia.app.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.app.dto.request.CreatePostRequest;
import com.socialmedia.app.dto.response.PostResponse;
import com.socialmedia.app.dto.response.UserResponse;
import com.socialmedia.app.exception.ResourceNotFoundException;
import com.socialmedia.app.model.Post;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.CommentRepository;
import com.socialmedia.app.repository.LikeRepository;
import com.socialmedia.app.repository.PostRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final FollowService followService;

    @Transactional
    public PostResponse createPost(CreatePostRequest request) {
        User currentUser = userService.getCurrentUser();
if (request.getImageUrl() == null || request.getImageUrl().isBlank()) {
    throw new RuntimeException("Post must contain an image");
}

if (request.getImageUrl() == null || request.getImageUrl().isBlank()) {
    throw new RuntimeException("Image is required to create a post");
}

Post post = Post.builder()
        .user(currentUser)
        .imageUrl(request.getImageUrl())
        .caption(request.getCaption())
        .eventLocation(request.getEventLocation())
        .eventDate(request.getEventDate() != null
                ? java.time.LocalDateTime.parse(request.getEventDate())
                : null)
        .build();

        Post savedPost = postRepository.save(post);
        return mapToPostResponse(savedPost, currentUser.getId());
    }

    public PostResponse getPostById(Long postId) {
        User currentUser = userService.getCurrentUser();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        return mapToPostResponse(post, currentUser.getId());
    }

    public List<PostResponse> getAllPosts() {
        User currentUser = userService.getCurrentUser();
        List<Post> posts = postRepository.findAllOrderByCreatedAtDesc();
        return posts.stream()
                .map(post -> mapToPostResponse(post, currentUser.getId()))
                .collect(Collectors.toList());
    }

    public List<PostResponse> getUserPosts(Long userId) {
        User currentUser = userService.getCurrentUser();
        List<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return posts.stream()
                .map(post -> mapToPostResponse(post, currentUser.getId()))
                .collect(Collectors.toList());
    }

    public List<PostResponse> getHomeFeed() {
        User currentUser = userService.getCurrentUser();
        List<Long> followingIds = followService.getFollowing(currentUser.getId()).stream()
                .map(f -> f.getFollowing().getId())
                .collect(Collectors.toList());

        if (followingIds.isEmpty()) {
            return List.of();
        }

        List<Post> posts = postRepository.findByUserIdInOrderByCreatedAtDesc(followingIds);
        return posts.stream()
                .map(post -> mapToPostResponse(post, currentUser.getId()))
                .collect(Collectors.toList());
    }

    public List<PostResponse> searchPosts(String tag) {
        User currentUser = userService.getCurrentUser();
        List<Post> posts = postRepository.searchByTagOrLocation(tag);
        return posts.stream()
                .map(post -> mapToPostResponse(post, currentUser.getId()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deletePost(Long postId) {
        User currentUser = userService.getCurrentUser();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("You can only delete your own posts");
        }

        postRepository.delete(post);
    }

    private PostResponse mapToPostResponse(Post post, Long currentUserId) {
        Long likesCount = likeRepository.countByPostId(post.getId());
        Long commentsCount = commentRepository.countByPostId(post.getId());
        Boolean isLiked = likeRepository.existsByUserIdAndPostId(currentUserId, post.getId());

        return PostResponse.builder()
                .id(post.getId())
                .user(mapToUserResponse(post.getUser()))
                .imageUrl(post.getImageUrl())
                .caption(post.getCaption())
                .likesCount(likesCount)
                .commentsCount(commentsCount)
                .isLikedByCurrentUser(isLiked)
                .eventLocation(post.getEventLocation())
                .eventDate(post.getEventDate())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .bio(user.getBio())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
