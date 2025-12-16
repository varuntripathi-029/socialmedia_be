package com.socialmedia.app.repository;

import com.socialmedia.app.model.Like;
import com.socialmedia.app.model.Post;
import com.socialmedia.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserIdAndPostId(Long userId, Long postId);
    Boolean existsByUserIdAndPostId(Long userId, Long postId);
    Long countByPostId(Long postId);
    Optional<Like> findByUserAndPost(User user, Post post);
    @Transactional
    void deleteByUserIdAndPostId(Long userId, Long postId);
}