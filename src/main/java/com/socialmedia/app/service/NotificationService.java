package com.socialmedia.app.service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.socialmedia.app.dto.response.NotificationResponse;
import com.socialmedia.app.exception.ResourceNotFoundException;
import com.socialmedia.app.model.Notification;
import com.socialmedia.app.model.NotificationType;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.NotificationRepository;
import com.socialmedia.app.util.Constants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // Notifications are per-user, so — unlike the global posts/events feeds — eviction is cheap
    // and precise: one key per user, cleared on every mutation. TTL is just a safety net.
    private static final long NOTIFICATIONS_CACHE_TTL_SECONDS = 60;

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
        evictUserCache(recipient.getId());
    }

    public List<NotificationResponse> getUserNotifications(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Constants.clampPageSize(size));
        boolean cacheable = isCacheableFirstPage(pageable);
        String cacheKey = notificationsCacheKey(userId);

        if (cacheable) {
            List<NotificationResponse> cached = getCachedOrNull(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        List<NotificationResponse> result = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(userId, pageable)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());

        if (cacheable) {
            cache(cacheKey, result);
        }
        return result;
    }

    public List<NotificationResponse> getUnreadNotifications(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Constants.clampPageSize(size));
        boolean cacheable = isCacheableFirstPage(pageable);
        String cacheKey = unreadCacheKey(userId);

        if (cacheable) {
            List<NotificationResponse> cached = getCachedOrNull(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        List<NotificationResponse> result = notificationRepository
                .findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                .stream().map(this::mapToResponse)
                .collect(Collectors.toList());

        if (cacheable) {
            cache(cacheKey, result);
        }
        return result;
    }

    /**
     * Only the first page at the default size is cached — that covers the overwhelming majority
     * of real requests (a notifications dropdown, not deep pagination) and keeps eviction to two
     * fixed keys per user instead of a Redis KEYS scan over every page/size combination.
     */
    private boolean isCacheableFirstPage(Pageable pageable) {
        return pageable.getPageNumber() == 0 && pageable.getPageSize() == Constants.DEFAULT_PAGE_SIZE;
    }

    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getRecipient().getId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
        evictUserCache(userId);
    }

    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        evictUserCache(userId);
    }

    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getRecipient().getId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized");
        }

        notificationRepository.delete(notification);
        evictUserCache(userId);
    }

    private String notificationsCacheKey(Long userId) {
        return "notifications:user:" + userId + ":all:first-page";
    }

    private String unreadCacheKey(Long userId) {
        return "notifications:user:" + userId + ":unread:first-page";
    }

    @SuppressWarnings("unchecked")
    private List<NotificationResponse> getCachedOrNull(String cacheKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return (List<NotificationResponse>) cached;
            }
        } catch (Exception e) {
            log.warn("Failed to query notifications cache from Redis. Falling back to DB query.", e);
        }
        return null;
    }

    private void cache(String cacheKey, List<NotificationResponse> value) {
        try {
            redisTemplate.opsForValue().set(cacheKey, value, Duration.ofSeconds(NOTIFICATIONS_CACHE_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("Failed to store notifications in Redis cache.", e);
        }
    }

    private void evictUserCache(Long userId) {
        try {
            redisTemplate.delete(notificationsCacheKey(userId));
            redisTemplate.delete(unreadCacheKey(userId));
        } catch (Exception e) {
            log.warn("Failed to evict notifications cache for user {} (Redis down/timeout).", userId, e);
        }
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
