package com.socialmedia.app.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.app.dto.request.UpdateProfileRequest;
import com.socialmedia.app.dto.response.UserProfileResponse;
import com.socialmedia.app.dto.response.UserResponse;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.UserRepository;
import com.socialmedia.app.service.FollowService;
import com.socialmedia.app.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final FollowService followService;
    private final UserService userService;

    // -----------------------------
    // Check Username Availability
    // -----------------------------
    @GetMapping("/check-username/{username}")
    public ResponseEntity<java.util.Map<String, Object>> checkUsernameAvailability(@PathVariable String username) {
        boolean available = userService.isUsernameAvailable(username);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", available);
        response.put("message", available ? "Username is available" : "Username is not available");
        return ResponseEntity.ok(response);
    }

    // -----------------------------
    // Get full profile by username (followers + following)
    // -----------------------------
    @GetMapping("/{username}")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        var followers = followService.getFollowers(user.getId());
        var following = followService.getFollowing(user.getId());

        boolean isFollowingUser = false;
        boolean isSelf = false;
        var authObj = SecurityContextHolder.getContext().getAuthentication();
        if (authObj != null && authObj.isAuthenticated() && !"anonymousUser".equals(authObj.getPrincipal())) {
            String currentUsername = authObj.getName();
            User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
            if (currentUser != null) {
                if (currentUser.getId().equals(user.getId())) {
                    isSelf = true;
                } else {
                    isFollowingUser = followers.stream()
                        .anyMatch(f -> f.getFollower().getId().equals(currentUser.getId()));
                }
            }
        }

        boolean canViewDetails = !user.isPrivate() || isSelf || isFollowingUser;

        List<UserResponse> followerList = canViewDetails ? followers.stream()
                .map(f -> UserResponse.builder()
                        .id(f.getFollower().getId())
                        .username(f.getFollower().getUsername())
                        .email(f.getFollower().getEmail())
                        .fullName(f.getFollower().getFullName())
                        .build())
                .collect(Collectors.toList()) : java.util.Collections.emptyList();

        List<UserResponse> followingList = canViewDetails ? following.stream()
                .map(f -> UserResponse.builder()
                        .id(f.getFollowing().getId())
                        .username(f.getFollowing().getUsername())
                        .email(f.getFollowing().getEmail())
                        .fullName(f.getFollowing().getFullName())
                        .build())
                .collect(Collectors.toList()) : java.util.Collections.emptyList();

        UserProfileResponse response = UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .bio(user.getBio())
                .profileImageUrl(user.getProfileImageUrl())
                .followersCount(followers.size())
                .followingCount(following.size())
                .followers(followerList)
                .following(followingList)
                .isFollowing(isFollowingUser)
                .isPrivate(user.isPrivate())
                .build();

        return ResponseEntity.ok(response);
    }

    // -----------------------------
    // Simple search by username
    // -----------------------------
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(@RequestParam String username) {
        List<UserResponse> users = userRepository.findByUsernameContainingIgnoreCase(username)
                .stream()
                .map(u -> UserResponse.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .fullName(u.getFullName())
                        .bio(u.getBio())
                        .profileImageUrl(u.getProfileImageUrl())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).build();
        }

        String username = auth.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserResponse resp = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .bio(user.getBio())
                .profileImageUrl(user.getProfileImageUrl())
                .isPrivate(user.isPrivate())
                .createdAt(user.getCreatedAt())
                .build();

        return ResponseEntity.ok(resp);
    }

    // -----------------------------
    // Update Profile
    // -----------------------------
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        try {
            UserResponse response = userService.updateProfile(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // -----------------------------
    // Get Combined Profile Content
    // -----------------------------
    @GetMapping("/{userId}/content")
    public ResponseEntity<List<Object>> getUserProfileContent(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserProfileContent(userId));
    }
}
