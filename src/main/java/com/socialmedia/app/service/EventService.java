package com.socialmedia.app.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.app.dto.request.EventCreateRequest;
import com.socialmedia.app.dto.response.EventParticipantResponse;
import com.socialmedia.app.dto.response.EventParticipantSummaryResponse;
import com.socialmedia.app.dto.response.EventResponse;
import com.socialmedia.app.dto.response.UserResponse;
import com.socialmedia.app.exception.ForbiddenException;
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

    // Attendance counts are cached separately from the feed because their staleness tolerance is
    // different on each side of expiry. An active event's count still moves, so it gets a short
    // TTL; an expired event's count can never change again, so it gets a long one and effectively
    // stops touching PostgreSQL entirely.
    private static final String PARTICIPANT_COUNT_KEY_PREFIX = "event:participants:count:";
    private static final long ACTIVE_COUNT_TTL_SECONDS = 60;
    private static final long EXPIRED_COUNT_TTL_HOURS = 24;

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

    private static String participantCountKey(Long eventId) {
        return PARTICIPANT_COUNT_KEY_PREFIX + eventId;
    }

    /**
     * An event is expired once the host has ended it or its end time has passed. Both count,
     * because the host is not obliged to press the button — an event that ran last month is over
     * whether or not anyone told the system so.
     */
    private boolean isExpired(Event event) {
        if (EventStatus.ENDED.equals(event.getStatus())) {
            return true;
        }
        return event.getEndTime() != null && event.getEndTime().isBefore(LocalDateTime.now());
    }

    /**
     * Confirmed-attendee count, read through Redis.
     *
     * This is the hot path: {@code mapToEventResponse} needs a count for every event it renders,
     * so the events feed was issuing one {@code countByEventIdAndRsvpStatus} per event — a
     * textbook N+1 that scaled with the size of the feed. Serving it from Redis collapses that to
     * one round trip per event id, and for expired events the value is immutable so the entry
     * survives a full day.
     *
     * Fails open like every other cache read here: a Redis outage degrades this to the previous
     * per-event query rather than failing the request.
     */
    private int getParticipantCount(Event event) {
        String key = participantCountKey(event.getId());
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Integer.parseInt(cached.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to read participant count from Redis for event {}. Falling back to DB.",
                    event.getId(), e);
        }

        int count = eventParticipantRepository.countByEventIdAndRsvpStatus(event.getId(), RSVPStatus.GOING);

        try {
            Duration ttl = isExpired(event)
                    ? Duration.ofHours(EXPIRED_COUNT_TTL_HOURS)
                    : Duration.ofSeconds(ACTIVE_COUNT_TTL_SECONDS);
            redisTemplate.opsForValue().set(key, Integer.toString(count), ttl);
        } catch (Exception e) {
            log.warn("Failed to cache participant count in Redis for event {}.", event.getId(), e);
        }

        return count;
    }

    private void evictParticipantCount(Long eventId) {
        try {
            redisTemplate.delete(participantCountKey(eventId));
        } catch (Exception e) {
            log.warn("Failed to evict participant count cache for event {} (Redis down/timeout).", eventId, e);
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

        // Evict events cache on creation. The organizer auto-joins above, so the count moved too.
        evictEventsCache();
        evictParticipantCount(savedEvent.getId());

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
            evictParticipantCount(eventId);

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
        evictParticipantCount(eventId);

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

    /**
     * The attendee roster — host-only, and only while the event is still running.
     *
     * Two separate refusals, in this order:
     *
     * 1. Once an event expires the roster is gone for everybody, the host included. Attendance
     *    collapses to the headcount served by {@link #getParticipantSummary}. The rows are not
     *    deleted — this is a read-time policy, so it is reversible and the count stays derivable.
     * 2. While the event is live, only the organizer may see who is coming.
     *
     * Expiry is checked first so that a non-host asking about an expired event is told the roster
     * is closed rather than that they are not the host — the second message would confirm the
     * event has an identifiable organizer to someone who has no business enumerating it.
     */
    @Transactional(readOnly = true)
    public List<EventParticipantResponse> getEventParticipants(Long eventId, String username) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found id: " + eventId));

        if (isExpired(event)) {
            throw new ForbiddenException(
                    "This event has ended. Only the attendee count remains available.");
        }

        if (username == null || !event.getOrganizer().getUsername().equals(username)) {
            throw new ForbiddenException("Only the event host can see who is attending.");
        }

        return eventParticipantRepository.findByEventId(eventId).stream()
                .map(this::mapToParticipantResponse)
                .collect(Collectors.toList());
    }

    /**
     * The headcount — public to everyone, forever, for every event.
     *
     * This is the deliberate counterweight to locking down the roster: turnout stays legible so
     * people can tell which events actually draw a crowd, while who showed up stays private.
     *
     * {@code viewerAttending} is per-viewer and so is resolved fresh on every call rather than
     * cached with the count — the same separation the posts feed makes between the shared list
     * and the viewer's like state.
     */
    @Transactional(readOnly = true)
    public EventParticipantSummaryResponse getParticipantSummary(Long eventId, String username) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found id: " + eventId));

        Boolean viewerAttending = null;
        if (username != null) {
            viewerAttending = userRepository.findByUsername(username)
                    .map(viewer -> eventParticipantRepository.existsByEventIdAndUserId(eventId, viewer.getId()))
                    .orElse(false);
        }

        return EventParticipantSummaryResponse.builder()
                .eventId(eventId)
                .participantCount(getParticipantCount(event))
                .maxParticipants(event.getMaxParticipants())
                .expired(isExpired(event))
                .viewerAttending(viewerAttending)
                .build();
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

        // Evict events cache when ending an event. The count entry must go too: it was written
        // with the short active-event TTL, and ending the event makes it immutable, so it should
        // be re-cached under the 24h expired TTL on next read.
        evictEventsCache();
        evictParticipantCount(eventId);

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
        evictParticipantCount(eventId);
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
        // Served from Redis. Previously one COUNT query per event, which meant the events feed
        // issued N of them for N events.
        int activeParticipants = getParticipantCount(event);

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
                .expired(isExpired(event))
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
