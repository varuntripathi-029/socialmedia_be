package com.socialmedia.app.controller;

import com.socialmedia.app.dto.response.UserProfileResponse;
import com.socialmedia.app.dto.response.UserResponse;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.UserRepository;
import com.socialmedia.app.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final FollowService followService;

    // -----------------------------
    // Get full profile by username (followers + following)
    // -----------------------------
    @GetMapping("/{username}")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        var followers = followService.getFollowers(user.getId());
        var following = followService.getFollowing(user.getId());

        List<UserResponse> followerList = followers.stream()
                .map(f -> UserResponse.builder()
                        .id(f.getFollower().getId())
                        .username(f.getFollower().getUsername())
                        .email(f.getFollower().getEmail())
                        .fullName(f.getFollower().getFullName())
                        .build())
                .collect(Collectors.toList());

        List<UserResponse> followingList = following.stream()
                .map(f -> UserResponse.builder()
                        .id(f.getFollowing().getId())
                        .username(f.getFollowing().getUsername())
                        .email(f.getFollowing().getEmail())
                        .fullName(f.getFollowing().getFullName())
                        .build())
                .collect(Collectors.toList());

        UserProfileResponse response = UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .followersCount(followerList.size())
                .followingCount(followingList.size())
                .followers(followerList)
                .following(followingList)
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
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }
}
