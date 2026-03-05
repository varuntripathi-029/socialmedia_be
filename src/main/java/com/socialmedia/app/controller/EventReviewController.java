package com.socialmedia.app.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.app.dto.request.EventReviewRequest;
import com.socialmedia.app.dto.response.EventReviewResponse;
import com.socialmedia.app.service.EventReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events/{eventId}/reviews")
@RequiredArgsConstructor
public class EventReviewController {

    private final EventReviewService eventReviewService;

    @PostMapping
    public ResponseEntity<EventReviewResponse> submitReview(
            @PathVariable Long eventId,
            @Valid @RequestBody EventReviewRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventReviewService.submitReview(eventId, username, request));
    }

    @GetMapping
    public ResponseEntity<List<EventReviewResponse>> getEventReviews(@PathVariable Long eventId) {
        return ResponseEntity.ok(eventReviewService.getEventReviews(eventId));
    }
}
