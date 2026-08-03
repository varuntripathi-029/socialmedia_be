package com.socialmedia.app;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import com.socialmedia.app.model.Event;
import com.socialmedia.app.model.EventStatus;
import com.socialmedia.app.model.User;
import com.socialmedia.app.support.IntegrationTestBase;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The attendee roster is host-only while an event runs, and closed to everyone once it expires.
 * The headcount is public throughout — that is the deliberate escape valve so turnout stays
 * visible even though attendance is not.
 */
@DisplayName("Event attendance: roster is host-only, headcount is public")
class EventParticipantPrivacyTest extends IntegrationTestBase {

    private User host;
    private User attendee;
    private User outsider;
    private Event activeEvent;
    private Event endedEvent;
    private Event timedOutEvent;

    @BeforeEach
    void setUpEvents() {
        host = createUser("event_host", false);
        attendee = createUser("event_attendee", false);
        outsider = createUser("event_outsider", false);

        activeEvent = createEvent(host, EventStatus.ACTIVE, LocalDateTime.now().plusDays(1));
        joinEvent(activeEvent, attendee);

        endedEvent = createEvent(host, EventStatus.ENDED, LocalDateTime.now().plusDays(1));
        joinEvent(endedEvent, attendee);

        // Still marked ACTIVE, but its end time is in the past. The host never pressed "end".
        timedOutEvent = createEvent(host, EventStatus.ACTIVE, LocalDateTime.now().minusDays(1));
        joinEvent(timedOutEvent, attendee);
    }

    // -----------------------------------------------------------------
    // Roster
    // -----------------------------------------------------------------

    @Test
    @WithMockUser(username = "event_host")
    @DisplayName("host sees the roster of a running event")
    void hostSeesRoster() throws Exception {
        mockMvc.perform(get("/api/events/" + activeEvent.getId() + "/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "event_attendee")
    @DisplayName("an attendee cannot see the roster — attending is not the same as hosting")
    void attendeeCannotSeeRoster() throws Exception {
        mockMvc.perform(get("/api/events/" + activeEvent.getId() + "/participants"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "event_outsider")
    @DisplayName("a non-participant cannot see the roster")
    void outsiderCannotSeeRoster() throws Exception {
        mockMvc.perform(get("/api/events/" + activeEvent.getId() + "/participants"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous caller cannot see the roster — this route left the permitAll set")
    void anonymousCannotSeeRoster() throws Exception {
        // Previously GET /api/events/** was blanket-permitAll, so this returned the full roster
        // with no account at all.
        //
        // 403 rather than 401 because the app configures no AuthenticationEntryPoint, so Spring
        // Security denies anonymous access to every protected route this way. That is pre-existing
        // application-wide behaviour, not something specific to this endpoint — asserting it here
        // pins the deny, and the status code matches what every other protected route returns.
        mockMvc.perform(get("/api/events/" + activeEvent.getId() + "/participants"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "event_host")
    @DisplayName("even the host loses the roster once the host ends the event")
    void hostLosesRosterAfterEnding() throws Exception {
        mockMvc.perform(get("/api/events/" + endedEvent.getId() + "/participants"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "event_host")
    @DisplayName("expiry is by end time too, not only by the host pressing end")
    void hostLosesRosterAfterEndTimePasses() throws Exception {
        mockMvc.perform(get("/api/events/" + timedOutEvent.getId() + "/participants"))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------
    // Public headcount
    // -----------------------------------------------------------------

    @Test
    @DisplayName("anonymous callers can read the headcount of a running event")
    void anonymousReadsHeadcount() throws Exception {
        mockMvc.perform(get("/api/events/" + activeEvent.getId() + "/participant-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.viewerAttending").doesNotExist());
    }

    @Test
    @DisplayName("the headcount survives expiry — this is what replaces the roster")
    void headcountSurvivesExpiry() throws Exception {
        mockMvc.perform(get("/api/events/" + endedEvent.getId() + "/participant-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.expired").value(true));
    }

    @Test
    @DisplayName("a timed-out event reports itself expired without the host ending it")
    void timedOutEventReportsExpired() throws Exception {
        mockMvc.perform(get("/api/events/" + timedOutEvent.getId() + "/participant-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(true))
                .andExpect(jsonPath("$.participantCount").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @WithMockUser(username = "event_attendee")
    @DisplayName("the summary tells a caller whether they personally are attending")
    void summaryReportsViewerAttendance() throws Exception {
        // The frontend needs this to render Join vs Leave without being handed the roster.
        mockMvc.perform(get("/api/events/" + activeEvent.getId() + "/participant-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerAttending").value(true));
    }

    @Test
    @WithMockUser(username = "event_outsider")
    @DisplayName("the summary reports a non-attendee as not attending, without naming anyone")
    void summaryReportsViewerNotAttending() throws Exception {
        mockMvc.perform(get("/api/events/" + activeEvent.getId() + "/participant-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerAttending").value(false))
                .andExpect(jsonPath("$.participantCount").value(2));
    }

    @Test
    @DisplayName("the event listing still exposes the public count and an expiry flag")
    void eventListingCarriesCountAndExpiry() throws Exception {
        mockMvc.perform(get("/api/events/" + endedEvent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentParticipantsCount").value(2))
                .andExpect(jsonPath("$.expired").value(true));
    }
}
