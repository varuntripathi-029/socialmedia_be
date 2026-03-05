package com.socialmedia.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.socialmedia.app.model.EventReview;

@Repository
public interface EventReviewRepository extends JpaRepository<EventReview, Long> {

    List<EventReview> findByEventId(Long eventId);

    Optional<EventReview> findByEventIdAndReviewerId(Long eventId, Long reviewerId);

    boolean existsByEventIdAndReviewerId(Long eventId, Long reviewerId);
}
