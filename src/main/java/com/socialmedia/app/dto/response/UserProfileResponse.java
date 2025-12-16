package com.socialmedia.app.dto.response;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String profileImage; // optional, use null for now
    private int followersCount;
    private int followingCount;
    private List<UserResponse> followers;
    private List<UserResponse> following;
    private boolean isFollowing;
    private List<PostResponse> posts;
}
