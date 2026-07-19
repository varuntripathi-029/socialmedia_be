package com.socialmedia.app.repository;

import com.socialmedia.app.model.Like;
import com.socialmedia.app.model.Post;
import com.socialmedia.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserIdAndPostId(Long userId, Long postId);
    Boolean existsByUserIdAndPostId(Long userId, Long postId);
    Long countByPostId(Long postId);
    Optional<Like> findByUserAndPost(User user, Post post);
    @Transactional
    void deleteByUserIdAndPostId(Long userId, Long postId);

    // Used to overlay per-viewer "isLiked" onto a post list that may have come from a shared cache entry.
    @Query("SELECT l.post.id FROM Like l WHERE l.user.id = :userId AND l.post.id IN :postIds")
    Set<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}