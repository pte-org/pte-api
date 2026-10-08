package com.pte.practice.internal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Version guard sent by the browser for begin/heartbeat mutations. */
public record PracticeSessionActionRequest(
        @NotNull @PositiveOrZero Long clientVersion) {
}
