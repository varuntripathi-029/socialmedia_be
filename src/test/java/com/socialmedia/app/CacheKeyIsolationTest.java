package com.socialmedia.app;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.test.context.support.WithMockUser;

import com.socialmedia.app.model.Event;
import com.socialmedia.app.model.EventStatus;
import com.socialmedia.app.model.User;
import com.socialmedia.app.support.IntegrationTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The cache is where a visibility rule quietly stops applying.
 *
 * The posts feed used to write one shared {@code posts:feed:all:p0:s20} entry. Now that the feed's
 * contents depend on who is asking, that shared key would hand one viewer's visible set — private
 * posts included — to the next caller. These tests pin the key shape, because a regression here
 * would reintroduce the leak while every visibility test above still passed on a cold cache.
 */
@DisplayName("Cache keys isolate viewers, and participant counts are cached by expiry state")
class CacheKeyIsolationTest extends IntegrationTestBase {

    private User alice;
    private User bob;

    @BeforeEach
    void setUpUsers() {
        alice = createUser("cache_alice", false);
        bob = createUser("cache_bob", false);
        createPost(alice, "alice writes a post");
    }

    @Test
    @WithMockUser(username = "cache_alice")
    @DisplayName("the posts feed cache key is scoped to the viewer, not global")
    void feedCacheKeyIsViewerScoped() throws Exception {
        mockMvc.perform(get("/api/posts")).andExpect(status().isOk());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, atLeastOnce()).set(key.capture(), any(), any(Duration.class));

        assertThat(key.getAllValues())
                .as("feed must not be cached under a viewer-independent key")
                .noneMatch(k -> k.startsWith("posts:feed:all"));
        assertThat(key.getAllValues())
                .anyMatch(k -> k.equals("posts:feed:v" + alice.getId() + ":p0:s20"));
    }

    @Test
    @DisplayName("two viewers never share a posts feed cache entry")
    void twoViewersGetDistinctKeys() throws Exception {
        mockMvc.perform(get("/api/posts").with(user("cache_alice"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/posts").with(user("cache_bob"))).andExpect(status().isOk());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, atLeastOnce()).set(key.capture(), any(), any(Duration.class));

        assertThat(key.getAllValues()).contains(
                "posts:feed:v" + alice.getId() + ":p0:s20",
                "posts:feed:v" + bob.getId() + ":p0:s20");
    }

    @Test
    @DisplayName("an active event's participant count is cached with the short TTL")
    void activeEventCountUsesShortTtl() throws Exception {
        Event active = createEvent(alice, EventStatus.ACTIVE, LocalDateTime.now().plusDays(1));

        mockMvc.perform(get("/api/events/" + active.getId() + "/participant-summary"))
                .andExpect(status().isOk());

        verify(valueOperations).set(
                eq("event:participants:count:" + active.getId()),
                any(),
                eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("an expired event's count is cached for a day — it can never change again")
    void expiredEventCountUsesLongTtl() throws Exception {
        Event ended = createEvent(alice, EventStatus.ENDED, LocalDateTime.now().plusDays(1));

        mockMvc.perform(get("/api/events/" + ended.getId() + "/participant-summary"))
                .andExpect(status().isOk());

        verify(valueOperations).set(
                eq("event:participants:count:" + ended.getId()),
                any(),
                eq(Duration.ofHours(24)));
    }

    @Test
    @WithMockUser(username = "cache_alice")
    @DisplayName("a Redis outage degrades to a database read rather than failing the request")
    void redisFailureFallsBackToDatabase() throws Exception {
        // Every cache read in this codebase is fail-open. Confirm that contract still holds for
        // the paths this change touched.
        org.mockito.Mockito.when(valueOperations.get(startsWith("posts:feed:")))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("redis down"));

        mockMvc.perform(get("/api/posts")).andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor user(String username) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(username);
    }
}
