package com.pte.practice.internal.dto.request;

import java.time.LocalDate;

/** Request to update student practice profile preferences or goals. */
public record PracticeProfileUpdateRequest(LocalDate examDate) {
}
