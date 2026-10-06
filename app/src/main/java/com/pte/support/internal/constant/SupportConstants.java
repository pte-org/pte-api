package com.pte.support.internal.constant;

public final class SupportConstants {

    public static final String TICKET_NOT_FOUND = "TICKET_NOT_FOUND";
    public static final String TICKET_NOT_FOUND_FRIENDLY = "The support ticket could not be found.";

    public static final String INVALID_TICKET_ENTITY_PAIR = "INVALID_TICKET_ENTITY_PAIR";
    public static final String INVALID_TICKET_ENTITY_PAIR_FRIENDLY =
            "Both entityType and entityId must be provided together, or both must be omitted.";

    public static final String ENTITY_REFERENCE_NOT_FOUND = "ENTITY_REFERENCE_NOT_FOUND";
    public static final String ENTITY_REFERENCE_NOT_FOUND_FRIENDLY =
            "The referenced entity could not be found.";

    public static final String INVALID_STATUS_TRANSITION = "INVALID_STATUS_TRANSITION";
    public static final String INVALID_STATUS_TRANSITION_FRIENDLY =
            "The requested status transition is not allowed.";

    public static final String AGGREGATE_SUPPORT_TICKET = "SupportTicket";
    public static final String EVENT_TICKET_SUBMITTED = "TicketSubmitted";
    public static final String EVENT_TICKET_STATUS_UPDATED = "TicketStatusUpdated";
    public static final String EVENT_TICKET_NOTE_ADDED = "TicketNoteAdded";
    public static final String EVENT_TICKET_CLOSED = "TicketClosed";

    private SupportConstants() {
    }
}
