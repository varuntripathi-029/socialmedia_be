package com.socialmedia.app.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.app.dto.response.ApiResponse;
import com.socialmedia.app.dto.response.UserResponse;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.UserRepository;
import com.socialmedia.app.service.FollowService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final UserRepository userRepository;

    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse> followUser(@PathVariable Long userId, Authentication auth) {
        User currentUser = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        followService.followUser(currentUser.getId(), userId);
        return ResponseEntity.ok(ApiResponse.builder().success(true).message("User followed successfully").build());
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse> unfollowUser(@PathVariable Long userId, Authentication auth) {
        User currentUser = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        followService.unfollowUser(currentUser.getId(), userId);
        return ResponseEntity.ok(ApiResponse.builder().success(true).message("User unfollowed successfully").build());
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<UserResponse>> getFollowers(@PathVariable Long userId) {
        var followers = followService.getFollowers(userId).stream()
                .map(f -> UserResponse.builder()
                .id(f.getFollower().getId())
                .username(f.getFollower().getUsername())
                .email(f.getFollower().getEmail())
                .fullName(f.getFollower().getFullName())
                .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(followers);
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<List<UserResponse>> getFollowing(@PathVariable Long userId) {
        var following = followService.getFollowing(userId).stream()
                .map(f -> UserResponse.builder()
                .id(f.getFollowing().getId())
                .username(f.getFollowing().getUsername())
                .email(f.getFollowing().getEmail())
                .fullName(f.getFollowing().getFullName())
                .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(following);
    }
}
