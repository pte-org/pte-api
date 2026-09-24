package com.pte.session.internal.constant;

import java.util.Set;

/** Centralized codes/labels for session. */
public final class SessionConstants {

    public static final String REPORT_PUBLICATION_REQUIRES_CLOSED_SESSION =
            "REPORT_PUBLICATION_REQUIRES_CLOSED_SESSION";
    public static final String REPORT_PUBLICATION_REQUIRES_CLOSED_SESSION_MESSAGE =
            "Close the exam session before publishing student reports.";

    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    public static final String ALREADY_ENROLLED = "ALREADY_ENROLLED";
    public static final String ALREADY_ASSIGNED = "ALREADY_ASSIGNED";
    public static final String ENROLLMENT_NOT_FOUND = "ENROLLMENT_NOT_FOUND";
    public static final String PROCTOR_ASSIGNMENT_NOT_FOUND = "PROCTOR_ASSIGNMENT_NOT_FOUND";
    public static final String HOST_CONTEXT_REQUIRED = "HOST_CONTEXT_REQUIRED";
    public static final String INVALID_SESSION_WINDOW = "INVALID_SESSION_WINDOW";
    public static final String NOT_ENTITLED = "NOT_ENTITLED";
    public static final String PROCTOR_NOT_ASSIGNED = "PROCTOR_NOT_ASSIGNED";
    public static final String POLICY_LOCKED = "POLICY_LOCKED";
    public static final String INVALID_POLICY_PATCH = "INVALID_POLICY_PATCH";
    public static final String SESSION_CAPACITY_EXCEEDED = "SESSION_CAPACITY_EXCEEDED";
    public static final String SESSION_CAPACITY_EXCEEDED_FRIENDLY =
            "The selected audience is larger than this exam's capacity.";
    public static final String CLASS_ASSIGNMENT_NOT_ALLOWED = "CLASS_ASSIGNMENT_NOT_ALLOWED";
    public static final String CLASS_ASSIGNMENT_NOT_FOUND = "CLASS_ASSIGNMENT_NOT_FOUND";
    public static final String SESSION_SUBSCRIPTION_NOT_FOUND = "SESSION_SUBSCRIPTION_NOT_FOUND";
    public static final String SESSION_SUBSCRIPTION_NOT_FOUND_FRIENDLY =
            "The selected subscription could not be found or is no longer active.";
    public static final String SESSION_WINDOW_OUTSIDE_SUBSCRIPTION = "SESSION_WINDOW_OUTSIDE_SUBSCRIPTION";
    public static final String SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION = "SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION";
    public static final String SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION = "SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION";
    public static final String SESSION_TIME_CONFLICT = "SESSION_TIME_CONFLICT";
    public static final String SESSION_TIME_CONFLICT_FRIENDLY =
            "Some students already have another exam during this time window.";
    public static final String SESSION_NOT_READY_TO_OPEN = "SESSION_NOT_READY_TO_OPEN";
    public static final String SESSION_NOT_CANCELLABLE = "SESSION_NOT_CANCELLABLE";
    public static final String EXAM_DRAFT_NOT_EDITABLE = "EXAM_DRAFT_NOT_EDITABLE";
    public static final String EXAM_PREFLIGHT_FAILED = "EXAM_PREFLIGHT_FAILED";
    public static final String EXAM_GENERATION_NOT_READY = "EXAM_GENERATION_NOT_READY";
    public static final String EXAM_DRAFT_VERSION_CONFLICT = "EXAM_DRAFT_VERSION_CONFLICT";
    public static final String EXAM_DRAFT_CONFIGURATION_INVALID = "EXAM_DRAFT_CONFIGURATION_INVALID";
    public static final String AUDIENCE_SOURCE_NOT_FOUND = "AUDIENCE_SOURCE_NOT_FOUND";
    public static final String EXAM_AUDIENCE_LOCKED = "EXAM_AUDIENCE_LOCKED";
    public static final String LEGACY_SESSION_CREATE_REQUIRES_NEW_WORKFLOW =
            "LEGACY_SESSION_CREATE_REQUIRES_NEW_WORKFLOW";
    public static final String EXAM_DRAFT_NOT_EDITABLE_FRIENDLY =
            "This exam can no longer be edited because preparation or publishing has started.";
    public static final String SESSION_NOT_READY_TO_OPEN_FRIENDLY =
            "Publish the exam before opening it for students.";
    public static final String SESSION_NOT_CANCELLABLE_FRIENDLY =
            "This exam can no longer be cancelled in its current state.";
    public static final String SESSION_WINDOW_OUTSIDE_SUBSCRIPTION_FRIENDLY =
            "The exam window must fit within the selected subscription period.";
    public static final String SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION_FRIENDLY =
            "The requested exam capacity is higher than the selected subscription limit.";
    public static final String SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION_FRIENDLY =
            "This exam already has more enrolled students than the selected subscription allows.";
    public static final String AUDIENCE_SOURCE_NOT_FOUND_FRIENDLY =
            "The selected student, class, or program is not available in this organization.";
    public static final String EXAM_DRAFT_VERSION_CONFLICT_FRIENDLY =
            "This exam draft was changed elsewhere. Refresh it before saving your changes.";
    public static final String EXAM_TEMPLATE_ACTIVE_REQUIRED =
            "Choose an active exam template before saving the draft.";
    public static final String EXAM_GENERATION_KEY_REQUIRED =
            "A generation request key is required. Please try again.";
    public static final String EXAM_OFFICIAL_UNIQUE_FORM_REQUIRED =
            "Official and mock exams must use a separate form for each student.";
    public static final String EXAM_SERIES_REQUIRED =
            "Add an exam series before applying a student reuse policy.";
    public static final String PREFLIGHT_TEMPLATE_POOL_INSUFFICIENT = "TEMPLATE_POOL_INSUFFICIENT";
    public static final String PREFLIGHT_AUDIENCE_EMPTY = "AUDIENCE_EMPTY";
    public static final String PREFLIGHT_AUDIENCE_NO_ELIGIBLE = "AUDIENCE_NO_ELIGIBLE";
    public static final String PREFLIGHT_AUDIENCE_CONFLICT_BLOCKED = "AUDIENCE_CONFLICT_BLOCKED";
    public static final String AGGREGATE_EXAM_SESSION = "ExamSession";
    public static final String EVENT_EXAM_DRAFT_CREATED = "ExamDraftCreated";
    public static final String EVENT_EXAM_DRAFT_UPDATED = "ExamDraftUpdated";
    public static final String EVENT_EXAM_AUDIENCE_SOURCE_ADDED = "ExamAudienceSourceAdded";
    public static final String EVENT_EXAM_AUDIENCE_SOURCE_REMOVED = "ExamAudienceSourceRemoved";
    public static final String EVENT_EXAM_GENERATED = "ExamGenerated";
    public static final String EVENT_EXAM_PUBLISHED = "ExamPublished";
    public static final String EVENT_EXAM_CANCELLED = "ExamCancelled";
    public static final String TEMPLATE_REFERENCE_REQUIRED = "Template reference is required";
    public static final String AUDIENCE_SOURCE_TYPE_REQUIRED = "Audience source type is required";
    public static final String AUDIENCE_SOURCE_REFERENCE_REQUIRED = "Audience source reference is required";

    public static final String PROCTOR_REFERENCE_REQUIRED = "Proctor reference is required";
    public static final String AT_LEAST_ONE_STUDENT_REQUIRED = "At least one student is required";
    public static final String SESSION_NAME_REQUIRED = "Session name is required";
    public static final String SKILLS_REQUIRED = "At least one skill is required";
    public static final Set<String> SUPPORTED_EXAM_SKILLS =
            Set.of("SPEAKING", "WRITING", "READING", "LISTENING");
    public static final String SKILLS_SIZE_INVALID = "Between 1 and 4 skills must be selected";
    public static final String SKILLS_DUPLICATE = "Select each skill only once.";
    public static final String SKILLS_NOT_IN_TEMPLATE = "Choose only skills included in the selected exam template.";
    public static final String SKILLS_FULL_TEMPLATE_REQUIRED =
            "Mock and official exams must include every skill in the selected template.";
    public static final String TEMPLATE_SKILLS_UNAVAILABLE =
            "The selected exam template does not contain any supported skills.";
    public static final String RETRY_COUNT_INVALID = "Set the retry count to a whole number from 0 to 9.";
    public static final String SUBSCRIPTION_REFERENCE_REQUIRED = "Subscription reference is required";
    public static final String OPEN_TIME_REQUIRED = "Open time is required";
    public static final String OPEN_TIME_FUTURE = "Open time must be in the future";
    public static final String CLOSE_TIME_REQUIRED = "Close time is required";
    public static final String CAPACITY_POSITIVE = "Capacity must be positive";
    public static final String CAPACITY_REQUIRED = "Capacity is required";
    public static final String STUDENT_REFERENCE_REQUIRED = "Student reference is required";
    public static final String REPLAY_POLICY_LIMIT_POSITIVE = "Replay policy limit must be positive if provided";
    public static final String ROLE_REQUIRED = "Role is required";
    public static final String CLASS_REFERENCE_REQUIRED = "Class reference is required";
    public static final String EXAM_PREFLIGHT_FRIENDLY =
            "This exam is not ready yet. Review the highlighted template, package, schedule, or student conflicts.";
    public static final String EXAM_GENERATION_FRIENDLY =
            "The exam is still being prepared. Please wait until generation finishes before publishing it.";
    public static final String EXAM_AUDIENCE_LOCKED_FRIENDLY =
            "This exam audience is already locked for generation or publishing and can no longer be changed here.";
    public static final String LEGACY_SESSION_CREATE_FRIENDLY =
            "This exam creation form is outdated. Start the exam from the new workflow so the template, audience, and student rules can be checked before publishing.";
    public static final String PREFLIGHT_AUDIENCE_CHANGED = "AUDIENCE_CHANGED_REQUIRES_REGENERATION";

    public static final String LIMITED_REPLAY_COUNT_INVALID = "Limited replay count must be >= 1";
    public static final String EXAM_POLICY_INCOMPLETE = "ExamPolicy is incomplete — expected all fields set together";
    public static final String STRICT_LOCKDOWN_NOT_ALLOWED_FOR_PRACTICE = "LockdownMode.STRICT is not allowed for PRACTICE exams";
    public static final String SESSION_CAPACITY_EXCEEDS_SUBSCRIPTION_DETAIL =
            "Session capacity %d exceeds subscription cap %d";
    public static final String SESSION_ENROLLMENTS_EXCEED_SUBSCRIPTION_DETAIL =
            "Enrollment count %d exceeds subscription cap %d";
    public static final String SESSION_TIME_CONFLICT_DETAIL =
            "Session time conflicts with existing session %s";
    public static final String BILLING_SERVICE_REQUIRED = "BillingService is required for session creation";
    public static final String PUBLISHED_EXAM_OPTIONS_READ_FAILED = "Published exam options could not be read";

    private SessionConstants() {
    }
}
