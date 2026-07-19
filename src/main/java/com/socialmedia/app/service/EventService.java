package com.socialmedia.app.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.app.dto.request.EventCreateRequest;
import com.socialmedia.app.dto.response.EventParticipantResponse;
import com.socialmedia.app.dto.response.EventResponse;
import com.socialmedia.app.dto.response.UserResponse;
import com.socialmedia.app.exception.ResourceNotFoundException;
import com.socialmedia.app.model.Event;
import com.socialmedia.app.model.EventParticipant;
import com.socialmedia.app.model.EventStatus;
import com.socialmedia.app.model.NotificationType;
import com.socialmedia.app.model.RSVPStatus;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.EventParticipantRepository;
import com.socialmedia.app.repository.EventRepository;
import com.socialmedia.app.repository.EventReviewRepository;
import com.socialmedia.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import com.socialmedia.app.exception.BadRequestException;
import java.util.Set;
import java.util.Objects;
import java.util.Map;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final EventReviewRepository eventReviewRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String EVENTS_FEED_CACHE_KEY = "events:feed:all";
    private static final String TRENDING_EVENTS_KEY = "trending:events";
    private static final long EVENTS_CACHE_TTL_MINUTES = 5;

    /**
     * Safely evicts the events feed cache from Redis.
     * Keeps execution safe if Redis connection is unavailable.
     */
    private void evictEventsCache() {
        try {
            redisTemplate.delete(EVENTS_FEED_CACHE_KEY);
            log.info("Evicted events feed cache: {}", EVENTS_FEED_CACHE_KEY);
        } catch (Exception e) {
            log.warn("Failed to evict events feed cache from Redis (Redis down/timeout).", e);
        }
    }

    @Transactional
    public EventResponse createEvent(EventCreateRequest request, String username) {
        User organizer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        // Date Validation: End time must be after start time
        if (request.getEndTime().isBefore(request.getStartTime()) || request.getEndTime().isEqual(request.getStartTime())) {
            throw new BadRequestException("End time must be after start time");
        }

        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .location(request.getLocation())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .maxParticipants(request.getMaxParticipants())
                .city(request.getCity())
                .eventType(request.getEventType())
                .collegeName(request.getCollegeName())
                .dressCode(request.getDressCode())
                .targetAudience(request.getTargetAudience())
                .status(EventStatus.ACTIVE)
                .organizer(organizer)
                .mediaFiles(request.getMediaFiles() != null ? request.getMediaFiles() : List.of())
                .build();

        Event savedEvent = eventRepository.save(event);

        // Organizer automatically joins as GOING
        EventParticipant participant = EventParticipant.builder()
                .event(savedEvent)
                .user(organizer)
                .rsvpStatus(RSVPStatus.GOING)
                .build();
        eventParticipantRepository.save(participant);

        // Initialize trending events score
        try {
            redisTemplate.opsForZSet().incrementScore(TRENDING_EVENTS_KEY, savedEvent.getId().toString(), 1.0);
            log.info("Initialized trending score for event {}", savedEvent.getId());
        } catch (Exception e) {
            log.warn("Failed to initialize trending score in Redis for event {}", savedEvent.getId(), e);
        }

        // Evict events cache on creation
        evictEventsCache();

        return mapToEventResponse(savedEvent);
    }

    @Transactional
    public EventParticipantResponse joinEvent(Long eventId, String username, RSVPStatus rsvpStatus) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found id: " + eventId));

        if (rsvpStatus == RSVPStatus.GOING) {
            int currentGoing = eventParticipantRepository.countByEventIdAndRsvpStatus(eventId, RSVPStatus.GOING);
            if (currentGoing >= event.getMaxParticipants()) {
                throw new IllegalStateException("Event has reached its maximum participants limit.");
            }
        }

        EventParticipant participant = eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId())
                .orElse(EventParticipant.builder()
                        .event(event)
                        .user(user)
                        .build());

        participant.setRsvpStatus(rsvpStatus);

        // Robust prevention of concurrent duplicate joins using Database Unique Constraint
        try {
            EventParticipant savedParticipant = eventParticipantRepository.saveAndFlush(participant);
            
            if (rsvpStatus == RSVPStatus.GOING && !event.getOrganizer().getId().equals(user.getId())) {
                notificationService.createNotification(
                        event.getOrganizer(), user, NotificationType.EVENT_RSVP, event.getId(),
                        user.getUsername() + " RSVP'd to your event: " + event.getTitle()
                );
            }

            // Evict events cache on RSVP change
            evictEventsCache();

            // Increment trending events score
            if (rsvpStatus == RSVPStatus.GOING) {
                try {
                    redisTemplate.opsForZSet().incrementScore(TRENDING_EVENTS_KEY, eventId.toString(), 2.0);
                    log.info("Incremented trending score for event {} due to user join", eventId);
                } catch (Exception re) {
                    log.warn("Failed to update trending score in Redis for event {}", eventId, re);
                }
            }

            return mapToParticipantResponse(savedParticipant);
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            log.warn("Concurrent duplicate join attempt by user {} on event {}", username, eventId);
            throw new IllegalStateException("You have already joined this event.");
        }
    }

    @Transactional
    public void leaveEvent(Long eventId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        EventParticipant participant = eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Participation not found"));

        eventParticipantRepository.delete(participant);

        // Evict events cache when leaving an event
        evictEventsCache();

        // Decrement trending events score or adjust it when a user leaves an event
        try {
            redisTemplate.opsForZSet().incrementScore(TRENDING_EVENTS_KEY, eventId.toString(), -2.0);
            log.info("Decremented trending score for event {} due to user leave", eventId);
        } catch (Exception e) {
            log.warn("Failed to decrement trending score in Redis", e);
        }
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getAllEvents() {
        try {
            Object cachedFeed = redisTemplate.opsForValue().get(EVENTS_FEED_CACHE_KEY);
            if (cachedFeed != null) {
                log.info("Cache HIT for events feed: {}", EVENTS_FEED_CACHE_KEY);
                return (List<EventResponse>) cachedFeed;
            }
            log.info("Cache MISS for events feed: {}", EVENTS_FEED_CACHE_KEY);
        } catch (Exception e) {
            log.warn("Failed to query events feed cache from Redis. Falling back to DB query.", e);
        }

        List<EventResponse> events = eventRepository.findAllByOrderByStartTimeAsc().stream()
                .map(this::mapToEventResponse)
                .collect(Collectors.toList());

        try {
            redisTemplate.opsForValue().set(
                    EVENTS_FEED_CACHE_KEY,
                    events,
                    java.time.Duration.ofMinutes(EVENTS_CACHE_TTL_MINUTES)
            );
            log.info("Cached events feed in Redis with TTL of {} minutes", EVENTS_CACHE_TTL_MINUTES);
        } catch (Exception e) {
            log.warn("Failed to store events feed in Redis cache.", e);
        }

        return events;
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found id: " + eventId));
        return mapToEventResponse(event);
    }

    @Transactional(readOnly = true)
    public List<EventParticipantResponse> getEventParticipants(Long eventId) {
        return eventParticipantRepository.findByEventId(eventId).stream()
                .map(this::mapToParticipantResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventResponse endEvent(Long eventId, String username) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found id: " + eventId));

        if (!event.getOrganizer().getUsername().equals(username)) {
            throw new IllegalStateException("Only the event host can end the event.");
        }

        event.setStatus(EventStatus.ENDED);
        Event savedEvent = eventRepository.save(event);

        // Evict events cache when ending an event
        evictEventsCache();

        return mapToEventResponse(savedEvent);
    }

    @Transactional
    public void deleteEvent(Long eventId, String username) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found id: " + eventId));

        if (!event.getOrganizer().getUsername().equals(username)) {
            throw new IllegalStateException("Only the event host can delete this event.");
        }

        eventReviewRepository.deleteByEventId(eventId);
        eventParticipantRepository.deleteByEventId(eventId);
        eventRepository.delete(event);

        // Remove from trending events sorted set in Redis
        try {
            redisTemplate.opsForZSet().remove(TRENDING_EVENTS_KEY, eventId.toString());
            log.info("Removed event {} from trending events in Redis", eventId);
        } catch (Exception e) {
            log.warn("Failed to remove event {} from trending events in Redis", eventId, e);
        }

        // Evict events cache on deletion
        evictEventsCache();
    }

    /**
     * Resolves the top 10 trending events from Redis using a Sorted Set.
     * Offers high-availability through graceful fallback to SQL ranking.
     */
    @Transactional(readOnly = true)
    public List<EventResponse> getTrendingEvents() {
        Set<Object> topIds = null;
        try {
            topIds = redisTemplate.opsForZSet().reverseRange(TRENDING_EVENTS_KEY, 0, 9);
        } catch (Exception e) {
            log.warn("Failed to retrieve trending events from Redis. Falling back to DB logic.", e);
        }

        // Fallback: Fetch DB events sorted by active participant count if Redis fails or is empty
        if (topIds == null || topIds.isEmpty()) {
            log.info("Trending cache empty or Redis offline. Fetching events sorted by active participant count.");
            return eventRepository.findAll().stream()
                    .map(this::mapToEventResponse)
                    .sorted((e1, e2) -> Integer.compare(e2.getCurrentParticipantsCount(), e1.getCurrentParticipantsCount()))
                    .limit(10)
                    .collect(Collectors.toList());
        }

        List<Long> orderedIds = topIds.stream()
                .map(obj -> {
                    try {
                        return Long.parseLong(obj.toString());
                    } catch (NumberFormatException nfe) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (orderedIds.isEmpty()) {
            return List.of();
        }

        // Fetch corresponding events from PostgreSQL
        List<Event> events = eventRepository.findAllById(orderedIds);
        Map<Long, Event> eventMap = events.stream()
                .collect(Collectors.toMap(Event::getId, e -> e));

        // Preserve original Redis ZSet rank sorting in memory
        return orderedIds.stream()
                .map(eventMap::get)
                .filter(Objects::nonNull)
                .map(this::mapToEventResponse)
                .collect(Collectors.toList());
    }

    private EventResponse mapToEventResponse(Event event) {
        int activeParticipants = eventParticipantRepository.countByEventIdAndRsvpStatus(event.getId(), RSVPStatus.GOING);

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .maxParticipants(event.getMaxParticipants())
                .city(event.getCity())
                .eventType(event.getEventType())
                .collegeName(event.getCollegeName())
                .dressCode(event.getDressCode())
                .targetAudience(event.getTargetAudience())
                .status(event.getStatus() != null ? event.getStatus().name() : EventStatus.ACTIVE.name())
                .organizer(mapToUserResponse(event.getOrganizer()))
                .mediaFiles(event.getMediaFiles())
                .createdAt(event.getCreatedAt())
                .currentParticipantsCount(activeParticipants)
                .build();
    }

    private EventParticipantResponse mapToParticipantResponse(EventParticipant participant) {
        return EventParticipantResponse.builder()
                .id(participant.getId())
                .eventId(participant.getEvent().getId())
                .user(mapToUserResponse(participant.getUser()))
                .rsvpStatus(participant.getRsvpStatus())
                .joinedAt(participant.getJoinedAt())
                .build();
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
