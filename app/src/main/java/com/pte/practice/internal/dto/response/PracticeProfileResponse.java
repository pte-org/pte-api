package com.pte.practice.internal.dto.response;

import java.time.LocalDate;

/** Student practice profile containing learning targets such as exam date. */
public record PracticeProfileResponse(LocalDate examDate) {
}
