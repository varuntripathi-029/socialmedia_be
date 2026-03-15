package com.socialmedia.app.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.app.dto.request.UpdateProfileRequest;
import com.socialmedia.app.dto.response.EventResponse;
import com.socialmedia.app.dto.response.PostResponse;
import com.socialmedia.app.dto.response.UserResponse;
import com.socialmedia.app.exception.ResourceNotFoundException;
import com.socialmedia.app.model.Event;
import com.socialmedia.app.model.EventStatus;
import com.socialmedia.app.model.Post;
import com.socialmedia.app.model.RSVPStatus;
import com.socialmedia.app.model.User;
import com.socialmedia.app.model.UsernameHistory;
import com.socialmedia.app.repository.EventParticipantRepository;
import com.socialmedia.app.repository.EventRepository;
import com.socialmedia.app.repository.FollowRepository;
import com.socialmedia.app.repository.PostRepository;
import com.socialmedia.app.repository.UserRepository;
import com.socialmedia.app.repository.UsernameHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UsernameHistoryRepository usernameHistoryRepository;
    private final PostRepository postRepository;
    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final FollowRepository followRepository;

    public UserResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    public List<UserResponse> searchUsers(String query) {
        List<User> users = userRepository.findByUsernameContainingIgnoreCase(query);
        return users.stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    public boolean isUsernameAvailable(String username) {
        if (username == null || username.trim().isEmpty() || username.length() < 3) {
            return false;
        }
        return !userRepository.existsByUsername(username) && !usernameHistoryRepository.existsByUsername(username);
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        User currentUser = getCurrentUser();

        // Check if username is being changed
        if (!currentUser.getUsername().equals(request.getUsername())) {
            // New username must not be empty
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                throw new IllegalArgumentException("Username cannot be empty");
            }

            // Check if ANY user currently has this username
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new IllegalArgumentException("Username is already taken");
            }

            // Check if ANY user has EVER used this username (including this user)
            if (usernameHistoryRepository.existsByUsername(request.getUsername())) {
                throw new IllegalArgumentException("Username has been used before and cannot be reused");
            }

            // Record old username in history BEFORE changing it
            UsernameHistory history = UsernameHistory.builder()
                    .user(currentUser)
                    .username(currentUser.getUsername())
                    .build();
            usernameHistoryRepository.save(history);

            currentUser.setUsername(request.getUsername());
        }

        if (request.getBio() != null) {
            currentUser.setBio(request.getBio());
        }

        if (request.getProfileImageUrl() != null) {
            currentUser.setProfileImageUrl(request.getProfileImageUrl());
        }

        if (request.getIsPrivate() != null) {
            currentUser.setPrivate(request.getIsPrivate());
        }

        userRepository.save(currentUser);

        return mapToUserResponse(currentUser);
    }

    public List<Object> getUserProfileContent(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isPrivate()) {
            User currentUser = getCurrentUser();
            if (!currentUser.getId().equals(user.getId()) && !followRepository.existsByFollowerAndFollowing(currentUser, user)) {
                return new java.util.ArrayList<>();
            }
        }

        List<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Event> events = eventRepository.findByOrganizerIdOrderByCreatedAtDesc(userId);

        List<Object> combinedOutput = new java.util.ArrayList<>();
        
        List<PostResponse> postResponses = posts.stream()
                .map(this::mapToPostResponse)
                .collect(Collectors.toList());

        List<EventResponse> eventResponses = events.stream()
                .map(this::mapToEventResponse)
                .collect(Collectors.toList());

        combinedOutput.addAll(postResponses);
        combinedOutput.addAll(eventResponses);

        combinedOutput.sort((a, b) -> {
            java.time.LocalDateTime dateA = (a instanceof PostResponse) ? 
                ((PostResponse) a).getCreatedAt() : ((EventResponse) a).getCreatedAt();
            java.time.LocalDateTime dateB = (b instanceof PostResponse) ? 
                ((PostResponse) b).getCreatedAt() : ((EventResponse) b).getCreatedAt();
            
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            
            return dateB.compareTo(dateA); // Descending
        });

        return combinedOutput;
    }

    private PostResponse mapToPostResponse(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .user(mapToUserResponse(post.getUser()))
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

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .bio(user.getBio())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .isPrivate(user.isPrivate())
                .build();
    }
}