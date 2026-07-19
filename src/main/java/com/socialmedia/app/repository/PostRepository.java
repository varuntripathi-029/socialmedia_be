package com.socialmedia.app.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.socialmedia.app.model.Post;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Post> findByUserIdInOrderByCreatedAtDesc(List<Long> userIds, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE LOWER(p.caption) LIKE LOWER(CONCAT('%', :tag, '%')) OR LOWER(p.eventLocation) LIKE LOWER(CONCAT('%', :tag, '%')) ORDER BY p.createdAt DESC")
    Page<Post> searchByTagOrLocation(String tag, Pageable pageable);
}
