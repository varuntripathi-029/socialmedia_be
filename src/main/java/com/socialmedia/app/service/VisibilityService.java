package com.socialmedia.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.app.exception.ResourceNotFoundException;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.FollowRepository;
import com.socialmedia.app.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * The single place that answers "may this viewer see that account's content?".
 *
 * Before this existed the rule was re-implemented inline in three controllers and one service, and
 * omitted entirely from five other read paths. Anything that needs the rule for one target account
 * calls this; anything that needs it across many authors at once uses
 * {@code PostRepository.VISIBILITY_PREDICATE} instead, so the two stay in sync by being read from
 * the same definition of "visible".
 */
@Service
@RequiredArgsConstructor
public class VisibilityService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    /**
     * A private account's content is visible only to itself and to accepted followers. A pending
     * row in {@code follow_requests} is not an accepted follow and grants nothing.
     */
    @Transactional(readOnly = true)
    public boolean canViewUserContent(Long viewerId, Long targetUserId) {
        if (viewerId != null && viewerId.equals(targetUserId)) {
            return true;
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!target.isPrivate()) {
            return true;
        }
        if (viewerId == null) {
            return false; // anonymous viewer, private target
        }

        User viewer = userRepository.findById(viewerId).orElse(null);
        return viewer != null && followRepository.existsByFollowerAndFollowing(viewer, target);
    }
}
