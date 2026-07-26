package com.pte.examdelivery.dto.response;

import java.time.Instant;

/** Lightweight poll target for the client's countdown UI (client timer is UX only). */
public record TimerStateResponse(
        int currentOrderIndex, String phase, Instant prepDeadline, Instant responseDeadline, Instant serverNow) {
}
