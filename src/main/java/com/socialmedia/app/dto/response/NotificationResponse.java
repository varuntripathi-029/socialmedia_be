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
public class NotificationResponse {
    private Long id;
    private Long recipientUserId;
    private Long actorUserId;
    private String actorUsername;
    private String notificationType;
    private Long referenceId;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;
}
