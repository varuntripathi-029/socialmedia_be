package com.socialmedia.app.dto.response;

import java.time.LocalDateTime;

import com.socialmedia.app.model.RSVPStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventParticipantResponse {
    private Long id;
    private UserResponse user;
    private Long eventId;
    private RSVPStatus rsvpStatus;
    private LocalDateTime joinedAt;
}
