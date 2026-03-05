package com.socialmedia.app.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.app.dto.response.EventResponse;
import com.socialmedia.app.dto.response.PostResponse;
import com.socialmedia.app.dto.response.SearchResultResponse;
import com.socialmedia.app.dto.response.UserResponse;
import com.socialmedia.app.model.Event;
import com.socialmedia.app.model.Post;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.EventRepository;
import com.socialmedia.app.repository.PostRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final PostRepository postRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public SearchResultResponse search(String query) {
        List<Post> posts = postRepository.searchByTagOrLocation(query);
        List<Event> events = eventRepository.searchEvents(query);

        List<PostResponse> postResponses = posts.stream()
                .map(this::mapToPostResponse)
                .collect(Collectors.toList());

        List<EventResponse> eventResponses = events.stream()
                .map(this::mapToEventResponse)
                .collect(Collectors.toList());

        return SearchResultResponse.builder()
                .posts(postResponses)
                .events(eventResponses)
                .build();
    }

    private PostResponse mapToPostResponse(Post post) {
        UserResponse user = mapToUserResponse(post.getUser());

        return PostResponse.builder()
                .id(post.getId())
                .user(user)
                .imageUrl(post.getImageUrl())
                .caption(post.getCaption())
                .eventLocation(post.getEventLocation())
                .eventDate(post.getEventDate())
                .createdAt(post.getCreatedAt())
                .likesCount(post.getLikes() != null ? (long) post.getLikes().size() : 0L)
                .commentsCount(post.getComments() != null ? (long) post.getComments().size() : 0L)
                .build();
    }

    private EventResponse mapToEventResponse(Event event) {
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
                .isActive(event.getIsActive())
                .organizer(mapToUserResponse(event.getOrganizer()))
                .mediaFiles(event.getMediaFiles())
                .createdAt(event.getCreatedAt())
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
