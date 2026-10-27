package com.pte.session.internal.service.enrollment;

import com.pte.session.domain.ExamSession;
import com.pte.session.internal.exception.lifecycle.SessionClosedException;
import com.pte.session.internal.exception.lifecycle.SessionNotStartedException;

import java.time.Instant;

/** Tells a student why a session can't be entered: not started yet, or already over. */
public final class SessionEntryGate {

    private SessionEntryGate() {
    }

    public static void requireOpen(ExamSession session) {
        switch (session.getStatus()) {
            case OPEN -> {
            }
            case CLOSED, CANCELLED -> throw new SessionClosedException();
            default -> throw new SessionNotStartedException();
        }
    }

    /**
     * The scheduled window still bounds new attempts when the host opens early
     * or forgets to close; {@code closesAt} is exclusive.
     */
    public static void requireWithinWindow(ExamSession session, Instant now) {
        if (now.isBefore(session.getOpensAt())) {
            throw new SessionNotStartedException();
        }
        if (!now.isBefore(session.getClosesAt())) {
            throw new SessionClosedException();
        }
    }
}
