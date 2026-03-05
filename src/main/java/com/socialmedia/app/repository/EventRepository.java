package com.socialmedia.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.socialmedia.app.model.Event;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByOrderByStartTimeAsc();

    @org.springframework.data.jpa.repository.Query("SELECT e FROM Event e WHERE " +
            "LOWER(e.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(e.description) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(e.city) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(e.eventType) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(e.collegeName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(e.targetAudience) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "ORDER BY e.startTime DESC")
    List<Event> searchEvents(String query);
}
