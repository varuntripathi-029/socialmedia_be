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

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final EventReviewRepository eventReviewRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public EventResponse createEvent(EventCreateRequest request, String username) {
        User organizer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

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
        EventParticipant savedParticipant = eventParticipantRepository.save(participant);

        if (rsvpStatus == RSVPStatus.GOING && !event.getOrganizer().getId().equals(user.getId())) {
            notificationService.createNotification(
                    event.getOrganizer(), user, NotificationType.EVENT_RSVP, event.getId(),
                    user.getUsername() + " RSVP'd to your event: " + event.getTitle()
            );
        }

        return mapToParticipantResponse(savedParticipant);
    }

    @Transactional
    public void leaveEvent(Long eventId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        EventParticipant participant = eventParticipantRepository.findByEventIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Participation not found"));

        eventParticipantRepository.delete(participant);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getAllEvents() {
        return eventRepository.findAllByOrderByStartTimeAsc().stream()
                .map(this::mapToEventResponse)
                .collect(Collectors.toList());
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
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
