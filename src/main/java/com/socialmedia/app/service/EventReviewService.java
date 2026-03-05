package com.socialmedia.app.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.app.dto.request.EventReviewRequest;
import com.socialmedia.app.dto.response.EventReviewResponse;
import com.socialmedia.app.dto.response.UserResponse;
import com.socialmedia.app.exception.ResourceNotFoundException;
import com.socialmedia.app.model.Event;
import com.socialmedia.app.model.EventReview;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.EventRepository;
import com.socialmedia.app.repository.EventReviewRepository;
import com.socialmedia.app.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventReviewService {

    private final EventReviewRepository eventReviewRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Transactional
    public EventReviewResponse submitReview(Long eventId, String username, EventReviewRequest request) {
        User reviewer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found id: " + eventId));

        if (event.getIsActive()) {
            throw new IllegalStateException("Cannot review an active event. Wait for it to end.");
        }

        if (eventReviewRepository.existsByEventIdAndReviewerId(eventId, reviewer.getId())) {
            throw new IllegalStateException("You have already reviewed this event.");
        }

        EventReview review = EventReview.builder()
                .reviewer(reviewer)
                .event(event)
                .stars(request.getStars())
                .reviewText(request.getReviewText())
                .build();

        EventReview savedReview = eventReviewRepository.save(review);

        // Update Host Average Rating
        User host = event.getOrganizer();
        double currentRating = host.getHostRating() != null ? host.getHostRating() : 0.0;
        int currentCount = host.getRatingCount() != null ? host.getRatingCount() : 0;
        
        double newRating = ((currentRating * currentCount) + request.getStars()) / (currentCount + 1);
        
        host.setHostRating(newRating);
        host.setRatingCount(currentCount + 1);
        userRepository.save(host);

        return mapToReviewResponse(savedReview);
    }

    @Transactional(readOnly = true)
    public List<EventReviewResponse> getEventReviews(Long eventId) {
        return eventReviewRepository.findByEventId(eventId).stream()
                .map(this::mapToReviewResponse)
                .collect(Collectors.toList());
    }

    private EventReviewResponse mapToReviewResponse(EventReview review) {
        return EventReviewResponse.builder()
                .id(review.getId())
                .eventId(review.getEvent().getId())
                .reviewer(UserResponse.builder()
                        .id(review.getReviewer().getId())
                        .username(review.getReviewer().getUsername())
                        .fullName(review.getReviewer().getFullName())
                        .email(review.getReviewer().getEmail())
                        .profileImageUrl(review.getReviewer().getProfileImageUrl())
                        .build())
                .stars(review.getStars())
                .reviewText(review.getReviewText())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
