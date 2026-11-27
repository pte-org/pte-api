package com.pte.practice.internal.dto.response.profile;

import com.pte.practice.internal.domain.enums.PracticeAvatarGender;

import java.time.LocalDate;

/** Student practice profile: the learning target (exam date) and the avatar they picked, null until they pick. */
public record PracticeProfileResponse(LocalDate examDate, PracticeAvatarGender gender) {
}
