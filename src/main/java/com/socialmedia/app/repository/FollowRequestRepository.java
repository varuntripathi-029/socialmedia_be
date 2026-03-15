package com.socialmedia.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.socialmedia.app.model.FollowRequest;
import com.socialmedia.app.model.User;

public interface FollowRequestRepository extends JpaRepository<FollowRequest, Long> {
    Optional<FollowRequest> findByFollowerAndFollowing(User follower, User following);
    boolean existsByFollowerAndFollowing(User follower, User following);
}
