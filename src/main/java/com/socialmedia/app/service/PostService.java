package com.socialmedia.app.service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
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
import com.socialmedia.app.util.Constants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final FollowService followService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final long POSTS_FEED_CACHE_TTL_SECONDS = 60;

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

    /**
     * The global feed is cache-aside like the events feed, but — unlike events — posts are
     * created often enough that evicting the whole cache on every write would defeat the
     * point, so this relies on a short TTL instead of active eviction. The cached entries never
     * carry isLikedByCurrentUser (that's per-viewer, not safe to share across users); it's
     * overlaid fresh on every request via a single batched query.
     */
    public List<PostResponse> getAllPosts(int page, int size) {
        User currentUser = userService.getCurrentUser();
        int safeSize = Constants.clampPageSize(size);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        String cacheKey = "posts:feed:all:p" + pageable.getPageNumber() + ":s" + safeSize;

        List<PostResponse> shared = getCachedOrNull(cacheKey);
        if (shared == null) {
            Page<Post> posts = postRepository.findAllByOrderByCreatedAtDesc(pageable);
            shared = posts.stream()
                    .map(this::mapToSharedPostResponse)
                    .collect(Collectors.toList());
            cachePostsFeed(cacheKey, shared);
        }

        return overlayLikedStatus(shared, currentUser.getId());
    }

    public List<PostResponse> getUserPosts(Long userId) {
        User currentUser = userService.getCurrentUser();
        List<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return posts.stream()
                .map(post -> mapToPostResponse(post, currentUser.getId()))
                .collect(Collectors.toList());
    }

    public List<PostResponse> getHomeFeed(int page, int size) {
        User currentUser = userService.getCurrentUser();
        List<Long> followingIds = followService.getFollowing(currentUser.getId()).stream()
                .map(f -> f.getFollowing().getId())
                .collect(Collectors.toList());

        if (followingIds.isEmpty()) {
            return List.of();
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), Constants.clampPageSize(size));
        Page<Post> posts = postRepository.findByUserIdInOrderByCreatedAtDesc(followingIds, pageable);
        return posts.stream()
                .map(post -> mapToPostResponse(post, currentUser.getId()))
                .collect(Collectors.toList());
    }

    public List<PostResponse> searchPosts(String tag, int page, int size) {
        User currentUser = userService.getCurrentUser();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Constants.clampPageSize(size));
        Page<Post> posts = postRepository.searchByTagOrLocation(tag, pageable);
        return posts.stream()
                .map(post -> mapToPostResponse(post, currentUser.getId()))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<PostResponse> getCachedOrNull(String cacheKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Cache HIT for posts feed: {}", cacheKey);
                return (List<PostResponse>) cached;
            }
            log.info("Cache MISS for posts feed: {}", cacheKey);
        } catch (Exception e) {
            log.warn("Failed to query posts feed cache from Redis. Falling back to DB query.", e);
        }
        return null;
    }

    private void cachePostsFeed(String cacheKey, List<PostResponse> shared) {
        try {
            redisTemplate.opsForValue().set(cacheKey, shared, Duration.ofSeconds(POSTS_FEED_CACHE_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("Failed to store posts feed in Redis cache.", e);
        }
    }

    /**
     * Applies the current viewer's like status onto an otherwise-shared, possibly-cached list —
     * a single indexed query rather than N existsByUserIdAndPostId calls.
     */
    private List<PostResponse> overlayLikedStatus(List<PostResponse> shared, Long currentUserId) {
        List<Long> postIds = shared.stream().map(PostResponse::getId).collect(Collectors.toList());
        if (postIds.isEmpty()) {
            return shared;
        }

        Set<Long> likedIds;
        try {
            likedIds = likeRepository.findLikedPostIds(currentUserId, postIds);
        } catch (Exception e) {
            log.warn("Failed to resolve liked posts for user {}; defaulting to not-liked.", currentUserId, e);
            likedIds = Set.of();
        }

        for (PostResponse response : shared) {
            response.setIsLikedByCurrentUser(likedIds.contains(response.getId()));
        }
        return shared;
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
        Boolean isLiked = likeRepository.existsByUserIdAndPostId(currentUserId, post.getId());
        PostResponse response = mapToSharedPostResponse(post);
        response.setIsLikedByCurrentUser(isLiked);
        return response;
    }

    /**
     * Everything about a post that's the same no matter who's viewing it — safe to cache and
     * share across users. isLikedByCurrentUser is deliberately left unset; callers must overlay
     * it per-viewer (see overlayLikedStatus).
     */
    private PostResponse mapToSharedPostResponse(Post post) {
        Long likesCount = likeRepository.countByPostId(post.getId());
        Long commentsCount = commentRepository.countByPostId(post.getId());

        return PostResponse.builder()
                .id(post.getId())
                .user(mapToUserResponse(post.getUser()))
                .imageUrl(post.getImageUrl())
                .caption(post.getCaption())
                .likesCount(likesCount)
                .commentsCount(commentsCount)
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
