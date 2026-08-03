package com.socialmedia.app.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxParticipants;
    
    private String city;
    private String eventType;
    private String collegeName;
    private String dressCode;
    private String targetAudience;
    private String status;
    @Builder.Default
    private String type = "EVENT";

    private UserResponse organizer;
    private List<String> mediaFiles;
    private LocalDateTime createdAt;

    /**
     * Confirmed attendee headcount. Public for every event — this is the signal that survives
     * once the roster itself becomes host-only, so people can still see which events draw a crowd.
     */
    private int currentParticipantsCount;

    /** True once the host ended the event or its endTime passed. Drives the UI's roster hiding. */
    private boolean expired;
}
