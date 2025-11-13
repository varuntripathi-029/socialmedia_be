package com.socialmedia.app.repository;

import com.socialmedia.app.model.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserIdAndPostId(Long userId, Long postId);
    Boolean existsByUserIdAndPostId(Long userId, Long postId);
    Long countByPostId(Long postId);

    @Transactional
    void deleteByUserIdAndPostId(Long userId, Long postId);
}