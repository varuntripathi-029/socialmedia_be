package com.socialmedia.app.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.socialmedia.app.model.Follow;
import com.socialmedia.app.model.FollowRequest;
import com.socialmedia.app.model.NotificationType;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.FollowRepository;
import com.socialmedia.app.repository.FollowRequestRepository;
import com.socialmedia.app.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final FollowRequestRepository followRequestRepository;
    private final NotificationService notificationService;

    public void followUser(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("You cannot follow yourself");
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new IllegalArgumentException("Follower not found"));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new IllegalArgumentException("User to follow not found"));

        if (followRepository.findByFollowerAndFollowing(follower, following).isPresent()) {
            throw new IllegalStateException("Already following this user");
        }

        if (following.isPrivate()) {
            if (followRequestRepository.existsByFollowerAndFollowing(follower, following)) {
                throw new IllegalStateException("Follow request already sent");
            }

            FollowRequest request = FollowRequest.builder()
                    .follower(follower)
                    .following(following)
                    .build();
            followRequestRepository.save(request);

            notificationService.createNotification(
                    following, follower, NotificationType.FOLLOW_REQUEST, request.getId(),
                    follower.getUsername() + " requested to follow you."
            );
        } else {
            Follow follow = Follow.builder()
                    .follower(follower)
                    .following(following)
                    .build();

            followRepository.save(follow);

            notificationService.createNotification(
                    following, follower, NotificationType.NEW_FOLLOWER, following.getId(),
                    follower.getUsername() + " started following you."
            );
        }
    }

    public void requestFollow(Long followerId, Long followingId) {
        followUser(followerId, followingId); // Handles both private/public logic implicitly
    }

    public void acceptFollowRequest(Long requestId, Long currentUserId) {
        FollowRequest request = followRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Follow request not found"));

        if (!request.getFollowing().getId().equals(currentUserId)) {
            throw new IllegalArgumentException("Unauthorized to accept this request");
        }

        User follower = request.getFollower();
        User following = request.getFollowing();

        if (followRepository.findByFollowerAndFollowing(follower, following).isEmpty()) {
            Follow follow = Follow.builder()
                    .follower(follower)
                    .following(following)
                    .build();
            followRepository.save(follow);

            notificationService.createNotification(
                    follower, following, NotificationType.NEW_FOLLOWER, following.getId(),
                    following.getUsername() + " accepted your follow request."
            );
        }

        followRequestRepository.delete(request);
    }

    public void rejectFollowRequest(Long requestId, Long currentUserId) {
        FollowRequest request = followRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Follow request not found"));

        if (!request.getFollowing().getId().equals(currentUserId)) {
            throw new IllegalArgumentException("Unauthorized to reject this request");
        }

        followRequestRepository.delete(request);
    }

    public void unfollowUser(Long followerId, Long followingId) {
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new IllegalArgumentException("Follower not found"));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new IllegalArgumentException("User to unfollow not found"));

        Follow follow = followRepository.findByFollowerAndFollowing(follower, following)
                .orElseThrow(() -> new IllegalArgumentException("Not following this user"));

        followRepository.delete(follow);
    }

    public List<Follow> getFollowers(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return followRepository.findByFollowing(user);
    }

    public List<Follow> getFollowing(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return followRepository.findByFollower(user);
    }
}
