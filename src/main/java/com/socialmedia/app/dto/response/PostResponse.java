package com.socialmedia.app.dto.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {

    private Long id;
    private UserResponse user;
    private String imageUrl;
    private String caption;
    private Long likesCount;
    private Long commentsCount;
    private Boolean isLikedByCurrentUser;
    private String eventLocation;
    private LocalDateTime eventDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private String type = "POST";
}
