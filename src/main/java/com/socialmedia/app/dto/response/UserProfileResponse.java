package com.socialmedia.app.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String profileImageUrl;
    private String bio;
    private int followersCount;
    private int followingCount;
    private List<UserResponse> followers;
    private List<UserResponse> following;
    private Boolean isFollowing;
    private Boolean isPrivate;
    private List<PostResponse> posts;
}
