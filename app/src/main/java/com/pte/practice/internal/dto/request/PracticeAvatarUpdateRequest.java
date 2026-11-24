package com.pte.practice.internal.dto.request;

import com.pte.practice.internal.domain.enums.PracticeAvatarGender;
import jakarta.validation.constraints.NotNull;

/** The avatar a student picks. Kept apart from the exam-date request so changing one can never change the other. */
public record PracticeAvatarUpdateRequest(@NotNull PracticeAvatarGender gender) {
}
