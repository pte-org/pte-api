package com.pte.session.internal.dto.request;

import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.internal.constant.SessionConstants;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateExamDraftRequest(
        @NotBlank(message = SessionConstants.SESSION_NAME_REQUIRED) String name,
        @NotNull(message = SessionConstants.TEMPLATE_REFERENCE_REQUIRED) UUID examDefinitionPublicId,
        @NotNull(message = SessionConstants.SUBSCRIPTION_REFERENCE_REQUIRED) UUID subscriptionPublicId,
        @NotNull(message = SessionConstants.OPEN_TIME_REQUIRED) @Future(message = SessionConstants.OPEN_TIME_FUTURE) Instant opensAt,
        @NotNull(message = SessionConstants.CLOSE_TIME_REQUIRED) Instant closesAt,
        ExamMode examMode,
        ReusePolicy reusePolicy,
        String seriesKey,
        @NotNull(message = SessionConstants.CAPACITY_REQUIRED) @Positive(message = SessionConstants.CAPACITY_POSITIVE) Integer capacity,
        @Size(min = 1, max = 4, message = SessionConstants.SKILLS_SIZE_INVALID) List<String> selectedSkills,
        @Min(value = 0, message = SessionConstants.RETRY_COUNT_INVALID)
        @Max(value = 9, message = SessionConstants.RETRY_COUNT_INVALID) Integer maxRetriesPerStudent,
        LockdownMode lockdownMode,
        @jakarta.validation.Valid List<CefrTargetRequest> cefrTargets,
        /** Prefer questions the centre has not used in its earlier exams; null means no preference. */
        Boolean avoidRepeatedQuestions) {

    /** Constructor for callers that predate the repeat preference. */
    public CreateExamDraftRequest(String name, UUID examDefinitionPublicId, UUID subscriptionPublicId,
            Instant opensAt, Instant closesAt, ExamMode examMode, ReusePolicy reusePolicy, String seriesKey,
            Integer capacity, List<String> selectedSkills, Integer maxRetriesPerStudent,
            LockdownMode lockdownMode, List<CefrTargetRequest> cefrTargets) {
        this(name, examDefinitionPublicId, subscriptionPublicId, opensAt, closesAt, examMode, reusePolicy,
                seriesKey, capacity, selectedSkills, maxRetriesPerStudent, lockdownMode, cefrTargets, null);
    }

    /** One level of the CEFR mix a centre picks for an exam; shared with {@link PatchExamDraftRequest}. */
    public record CefrTargetRequest(@jakarta.validation.constraints.NotNull String level,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Integer percent) {
    }

    /** Constructor for callers that predate the CEFR mix; the exam then follows its template. */
    public CreateExamDraftRequest(String name, UUID examDefinitionPublicId, UUID subscriptionPublicId,
            Instant opensAt, Instant closesAt, ExamMode examMode, ReusePolicy reusePolicy, String seriesKey,
            Integer capacity, List<String> selectedSkills, Integer maxRetriesPerStudent,
            LockdownMode lockdownMode) {
        this(name, examDefinitionPublicId, subscriptionPublicId, opensAt, closesAt, examMode, reusePolicy,
                seriesKey, capacity, selectedSkills, maxRetriesPerStudent, lockdownMode, null);
    }

    /** Source-compatible constructor for callers predating explicit skill/retry configuration. */
    public CreateExamDraftRequest(String name, UUID examDefinitionPublicId, UUID subscriptionPublicId,
            Instant opensAt, Instant closesAt, ExamMode examMode,
            ReusePolicy reusePolicy, String seriesKey, Integer capacity) {
        this(name, examDefinitionPublicId, subscriptionPublicId, opensAt, closesAt, examMode, reusePolicy,
                seriesKey, capacity, null, null, null);
    }

    /** Source-compatible constructor for callers using the previous full request shape. */
    public CreateExamDraftRequest(String name, UUID examDefinitionPublicId, UUID subscriptionPublicId,
            Instant opensAt, Instant closesAt, ExamMode examMode,
            ReusePolicy reusePolicy, String seriesKey, Integer capacity,
            List<String> selectedSkills, Integer maxRetriesPerStudent) {
        this(name, examDefinitionPublicId, subscriptionPublicId, opensAt, closesAt, examMode, reusePolicy,
                seriesKey, capacity, selectedSkills, maxRetriesPerStudent, null);
    }
}
