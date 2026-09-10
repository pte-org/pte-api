package com.pte.proctor.domain.enums;

/**
 * Attempt-affecting commands proctor can issue; travels to exam-delivery as a {@code ProctorCommand} event.
 *
 * <p>{@code EXTEND_TIME} removed (client-side-exam-timer Phase 5): it worked by pushing a new
 * {@code TimerState.responseDeadline} that the student's app would notice on its next {@code /timer}
 * poll — both the deadline it mutated and the poll that delivered it are gone as of this refactor,
 * with no replacement live-push channel. Accepted capability loss, decided explicitly by the user
 * rather than silently dropped (see {@code research/refactor_polling.md} and this plan's Phase 5
 * Session Notes).
 */
public enum ProctorCommandType {
    FORCE_SUBMIT
}
