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
public class EventReviewResponse {
    private Long id;
    private Long eventId;
    private UserResponse reviewer;
    private Integer stars;
    private String reviewText;
    private LocalDateTime createdAt;
}
