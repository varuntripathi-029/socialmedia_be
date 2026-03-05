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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialmedia.app.dto.request.EventCreateRequest;
import com.socialmedia.app.dto.response.EventParticipantResponse;
import com.socialmedia.app.dto.response.EventResponse;
import com.socialmedia.app.model.RSVPStatus;
import com.socialmedia.app.service.EventService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody EventCreateRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createEvent(request, username));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<EventParticipantResponse> joinEvent(
            @PathVariable Long id,
            @RequestParam(defaultValue = "GOING") RSVPStatus rsvpStatus,
            Authentication authentication
    ) {
        String username = authentication.getName();
        return ResponseEntity.ok(eventService.joinEvent(id, username, rsvpStatus));
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveEvent(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String username = authentication.getName();
        eventService.leaveEvent(id, username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/participants")
    public ResponseEntity<List<EventParticipantResponse>> getEventParticipants(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventParticipants(id));
    }
}
