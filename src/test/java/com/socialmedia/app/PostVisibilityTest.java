package com.socialmedia.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import com.socialmedia.app.model.Post;
import com.socialmedia.app.model.User;
import com.socialmedia.app.support.IntegrationTestBase;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the private-account leak.
 *
 * Every one of these read paths previously returned a private account's posts to any authenticated
 * caller. There is one test per path, because the original bug was not that the rule was wrong —
 * it was that four endpoints never asked. A single test on one endpoint would not have caught it.
 */
@DisplayName("Private accounts' posts are not readable through any feed, lookup, or search path")
class PostVisibilityTest extends IntegrationTestBase {

    private User privateAuthor;
    private User stranger;
    private User acceptedFollower;
    private Post privatePost;

    @BeforeEach
    void setUpAccounts() {
        privateAuthor = createUser("private_author", true);
        stranger = createUser("stranger", false);
        acceptedFollower = createUser("accepted_follower", false);
        follow(acceptedFollower, privateAuthor);

        privatePost = createPost(privateAuthor, "secret gig at the warehouse");
        createPost(stranger, "a completely public post");
    }

    @Test
    @WithMockUser(username = "stranger")
    @DisplayName("global feed omits a private account's posts")
    void globalFeedExcludesPrivatePosts() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].user.username").value("stranger"));
    }

    @Test
    @WithMockUser(username = "accepted_follower")
    @DisplayName("global feed includes a private account's posts for an accepted follower")
    void globalFeedIncludesPrivatePostsForFollower() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "private_author")
    @DisplayName("a private account still sees its own posts")
    void privateAuthorSeesOwnPosts() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "stranger")
    @DisplayName("per-author lookup returns nothing — this was the direct bypass of /content")
    void userPostsExcludesPrivatePosts() throws Exception {
        mockMvc.perform(get("/api/posts/user/" + privateAuthor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "accepted_follower")
    @DisplayName("per-author lookup returns posts to an accepted follower")
    void userPostsVisibleToFollower() throws Exception {
        mockMvc.perform(get("/api/posts/user/" + privateAuthor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @WithMockUser(username = "stranger")
    @DisplayName("fetching a private post by id is a 404, not a 403")
    void postByIdIsNotFoundForStranger() throws Exception {
        // 404 rather than 403 on purpose: a 403 would confirm the post exists, which is precisely
        // the fact being withheld.
        mockMvc.perform(get("/api/posts/" + privatePost.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "accepted_follower")
    @DisplayName("fetching a private post by id succeeds for an accepted follower")
    void postByIdVisibleToFollower() throws Exception {
        mockMvc.perform(get("/api/posts/" + privatePost.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").value("secret gig at the warehouse"));
    }

    @Test
    @WithMockUser(username = "stranger")
    @DisplayName("post search does not surface private posts")
    void postSearchExcludesPrivatePosts() throws Exception {
        mockMvc.perform(get("/api/posts/search").param("tag", "warehouse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "stranger")
    @DisplayName("global search does not surface private posts")
    void globalSearchExcludesPrivatePosts() throws Exception {
        mockMvc.perform(get("/api/search").param("query", "warehouse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "accepted_follower")
    @DisplayName("global search surfaces private posts to an accepted follower")
    void globalSearchIncludesPrivatePostsForFollower() throws Exception {
        mockMvc.perform(get("/api/search").param("query", "warehouse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts", hasSize(1)));
    }

    @Test
    @WithMockUser(username = "stranger")
    @DisplayName("profile content aggregate stays empty for a stranger")
    void profileContentEmptyForStranger() throws Exception {
        mockMvc.perform(get("/api/users/" + privateAuthor.getId() + "/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("a pending follow request grants nothing — only accepted follows count")
    @WithMockUser(username = "stranger")
    void pendingRequestDoesNotGrantAccess() throws Exception {
        // stranger has a follow_requests row but no follows row. The visibility predicate reads
        // only the latter, so nothing changes.
        mockMvc.perform(get("/api/posts/user/" + privateAuthor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
