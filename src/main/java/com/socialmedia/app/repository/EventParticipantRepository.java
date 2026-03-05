package com.socialmedia.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.socialmedia.app.model.EventParticipant;
import com.socialmedia.app.model.RSVPStatus;

@Repository
public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {

    Optional<EventParticipant> findByEventIdAndUserId(Long eventId, Long userId);
    
    List<EventParticipant> findByEventId(Long eventId);

    int countByEventIdAndRsvpStatus(Long eventId, RSVPStatus rsvpStatus);

    boolean existsByEventIdAndUserId(Long eventId, Long userId);
}
