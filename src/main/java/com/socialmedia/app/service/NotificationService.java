package com.socialmedia.app.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.socialmedia.app.dto.response.NotificationResponse;
import com.socialmedia.app.exception.ResourceNotFoundException;
import com.socialmedia.app.model.Notification;
import com.socialmedia.app.model.NotificationType;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void createNotification(User recipient, User actor, NotificationType type, Long referenceId, String message) {
        if (recipient.getId().equals(actor.getId())) {
            return; // Don't notify self
        }

        Notification notification = Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .notificationType(type)
                .referenceId(referenceId)
                .message(message)
                .build();

        notificationRepository.save(notification);
    }

    public List<NotificationResponse> getUserNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized");
        }
        
        notificationRepository.delete(notification);
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .recipientUserId(n.getRecipient().getId())
                .actorUserId(n.getActor().getId())
                .actorUsername(n.getActor().getUsername())
                .notificationType(n.getNotificationType().name())
                .referenceId(n.getReferenceId())
                .message(n.getMessage())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
