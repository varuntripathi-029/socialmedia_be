package com.socialmedia.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The public face of an event's attendance: a headcount, never a roster.
 *
 * This is what everyone except the host gets. It exists so that turnout stays discoverable —
 * people can still tell whether an event is drawing a crowd — without exposing who attended.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventParticipantSummaryResponse {

    private Long eventId;

    /** Number of confirmed (GOING) attendees. Public for every event, active or expired. */
    private int participantCount;

    private Integer maxParticipants;

    /** True once the event has ended — either the host ended it, or endTime has passed. */
    private boolean expired;

    /**
     * Whether the *calling* user is attending. Per-viewer, so it is resolved fresh on every
     * request and never cached alongside the shared count. Null for anonymous callers.
     */
    private Boolean viewerAttending;
}
