package com.socialmedia.app.support;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.socialmedia.app.model.Event;
import com.socialmedia.app.model.EventParticipant;
import com.socialmedia.app.model.EventStatus;
import com.socialmedia.app.model.Follow;
import com.socialmedia.app.model.Post;
import com.socialmedia.app.model.RSVPStatus;
import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.EventParticipantRepository;
import com.socialmedia.app.repository.EventRepository;
import com.socialmedia.app.repository.FollowRepository;
import com.socialmedia.app.repository.PostRepository;
import com.socialmedia.app.repository.UserRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Shared setup for the HTTP-level suites: H2 instead of Neon, a mocked Redis instead of Upstash,
 * and fixture builders for the handful of entities the privacy rules turn on.
 *
 * Redis is mocked rather than embedded because every cache read in this codebase already treats a
 * failure as a miss. A mock that returns null for every GET therefore exercises the exact path
 * production takes on a cold cache, and keeps the assertions about *visibility* from depending on
 * cache state. The one test that genuinely cares about cache keys asserts against this mock
 * directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PostRepository postRepository;

    @Autowired
    protected FollowRepository followRepository;

    @Autowired
    protected EventRepository eventRepository;

    @Autowired
    protected EventParticipantRepository eventParticipantRepository;

    /**
     * Replaces the real RedisTemplate for every suite. {@code @MockitoBean} is reset by Spring
     * between test methods, so no stub leaks from one test into the next.
     */
    @MockitoBean
    protected RedisTemplate<String, Object> redisTemplate;

    /**
     * Not a bean — {@code opsForValue()} is a method on the template, so this is a plain mock
     * wired in fresh each test.
     */
    protected ValueOperations<String, Object> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpRedisMock() {
        valueOperations = mock(ValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(mock(ZSetOperations.class));
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
    }

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    protected User createUser(String username, boolean isPrivate) {
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("{noop}irrelevant")
                .fullName(username + " Example")
                .isPrivate(isPrivate)
                .build();
        return userRepository.save(user);
    }

    protected Post createPost(User author, String caption) {
        Post post = Post.builder()
                .user(author)
                .imageUrl("https://example.test/" + caption.hashCode() + ".jpg")
                .caption(caption)
                .eventLocation("Testville")
                .build();
        return postRepository.save(post);
    }

    protected void follow(User follower, User following) {
        followRepository.save(Follow.builder()
                .follower(follower)
                .following(following)
                .build());
    }

    protected Event createEvent(User organizer, EventStatus status, LocalDateTime endTime) {
        Event event = Event.builder()
                .title("Test Event")
                .description("An event for tests")
                .location("Somewhere")
                .startTime(endTime.minusHours(2))
                .endTime(endTime)
                .maxParticipants(50)
                .city("Testville")
                .eventType("MEETUP")
                .status(status)
                .organizer(organizer)
                .build();
        Event saved = eventRepository.save(event);
        joinEvent(saved, organizer);
        return saved;
    }

    protected void joinEvent(Event event, User user) {
        eventParticipantRepository.save(EventParticipant.builder()
                .event(event)
                .user(user)
                .rsvpStatus(RSVPStatus.GOING)
                .build());
    }

    /** Convenience for stubbing a cache hit in the one suite that asserts on cache behaviour. */
    protected void stubCacheHit(String key, Object value) {
        lenient().when(valueOperations.get(key)).thenReturn(value);
    }

    protected static Object anyValue() {
        return any();
    }
}
