package com.pte.practice.internal.dto.request;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.enums.PracticeTimingMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * {@code timingMode} and {@code questionCount} are optional so older clients keep working; read them through
 * the {@code effective*} accessors, which apply the defaults (UNTIMED, 10 questions per task type).
 */
public record PracticeSessionStartRequest(
        @NotBlank(message = PracticeConstants.PRACTICE_PRODUCT_NOT_FOUND)
        String productCode,
        @NotNull(message = PracticeConstants.PRACTICE_NOT_ENTITLED)
        UUID organizationId,
        @Size(max = 64) Set<@Size(max = 64) String> taskTypeCodes,
        @Valid PracticeCapabilityManifest capabilities,
        PracticeTimingMode timingMode,
        @Min(value = 1, message = PracticeConstants.PRACTICE_QUESTION_COUNT_INVALID)
        @Max(value = PracticeConstants.PRACTICE_MAX_QUESTION_COUNT,
                message = PracticeConstants.PRACTICE_QUESTION_COUNT_INVALID)
        Integer questionCount,
        @Pattern(regexp = "SPEAKING|WRITING|READING|LISTENING", message = PracticeConstants.PRACTICE_SESSION_REQUEST_INVALID)
        String skillCode,
        UUID mockTestId,
        Boolean restart) {

    /** Compatibility constructor for callers that predate the timing mode. */
    public PracticeSessionStartRequest(String productCode, UUID organizationId, Set<String> taskTypeCodes,
            PracticeCapabilityManifest capabilities) {
        this(productCode, organizationId, taskTypeCodes, capabilities, null, null, null, null, null);
    }

    /** Compatibility constructor for callers that predate skill and mock-test sessions. */
    public PracticeSessionStartRequest(String productCode, UUID organizationId, Set<String> taskTypeCodes,
            PracticeCapabilityManifest capabilities, PracticeTimingMode timingMode, Integer questionCount) {
        this(productCode, organizationId, taskTypeCodes, capabilities, timingMode, questionCount, null, null, null);
    }

    public Set<String> safeTaskTypeCodes() {
        return taskTypeCodes == null ? Set.of() : Set.copyOf(taskTypeCodes);
    }

    public PracticeTimingMode effectiveTimingMode() {
        return timingMode == null ? PracticeTimingMode.UNTIMED : timingMode;
    }

    public boolean hasSkill() {
        return skillCode != null && !skillCode.isBlank();
    }

    public boolean hasMockTest() {
        return mockTestId != null;
    }

    public boolean effectiveRestart() {
        return Boolean.TRUE.equals(restart);
    }

    public int effectiveQuestionCount() {
        return questionCount == null ? PracticeConstants.PRACTICE_DEFAULT_QUESTION_COUNT : questionCount;
    }
}
