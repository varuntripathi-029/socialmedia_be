package com.socialmedia.app.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.socialmedia.app.model.Post;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * The visibility predicate, written once and reused by every multi-author read path.
     *
     * A post is visible to a viewer when its author is public, is the viewer themselves, or is
     * someone the viewer already follows (an accepted edge in `follows` — a pending row in
     * `follow_requests` deliberately does not count).
     *
     * This lives in the query rather than in a service-level filter for two reasons. Filtering
     * after the fact would break pagination — a page of 20 could come back with 3 rows and the
     * caller would have no way to know whether to fetch more. And a per-endpoint check is exactly
     * what let five read paths ship with no check at all; a new endpoint that forgets to pass a
     * viewer now fails to compile rather than silently leaking.
     */
    String VISIBILITY_PREDICATE = """
            (p.user.isPrivate = false
              OR p.user.id = :viewerId
              OR EXISTS (SELECT 1 FROM Follow f
                         WHERE f.follower.id = :viewerId AND f.following.id = p.user.id))
            """;

    List<Post> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Post> findByUserIdInOrderByCreatedAtDesc(List<Long> userIds, Pageable pageable);

    // Unfiltered reads — callers migrate to the visible* variants below, then these come out.
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT p FROM Post p WHERE LOWER(p.caption) LIKE LOWER(CONCAT('%', :tag, '%')) OR LOWER(p.eventLocation) LIKE LOWER(CONCAT('%', :tag, '%')) ORDER BY p.createdAt DESC")
    Page<Post> searchByTagOrLocation(String tag, Pageable pageable);

    /**
     * Global feed, restricted to what this viewer is allowed to see.
     * Replaces the unfiltered {@code findAllByOrderByCreatedAtDesc}.
     */
    @Query("SELECT p FROM Post p WHERE " + VISIBILITY_PREDICATE + " ORDER BY p.createdAt DESC")
    Page<Post> findVisiblePosts(@Param("viewerId") Long viewerId, Pageable pageable);

    /** Author's posts, restricted to what this viewer is allowed to see. */
    @Query("SELECT p FROM Post p WHERE p.user.id = :authorId AND " + VISIBILITY_PREDICATE
            + " ORDER BY p.createdAt DESC")
    List<Post> findVisiblePostsByAuthor(@Param("authorId") Long authorId, @Param("viewerId") Long viewerId);

    @Query("SELECT p FROM Post p WHERE "
            + "(LOWER(p.caption) LIKE LOWER(CONCAT('%', :tag, '%')) "
            + " OR LOWER(p.eventLocation) LIKE LOWER(CONCAT('%', :tag, '%'))) "
            + "AND " + VISIBILITY_PREDICATE + " ORDER BY p.createdAt DESC")
    Page<Post> searchVisibleByTagOrLocation(@Param("tag") String tag, @Param("viewerId") Long viewerId,
            Pageable pageable);
}
