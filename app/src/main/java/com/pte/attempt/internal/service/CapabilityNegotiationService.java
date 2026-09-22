package com.pte.attempt.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.internal.config.CapabilityProperties;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.dto.request.AttemptPreflightRequest;
import com.pte.attempt.internal.dto.request.ClientCapabilityManifest;
import com.pte.attempt.internal.dto.response.AttemptPreflightResponse;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.exception.CapabilityManifestInvalidException;
import com.pte.attempt.internal.exception.ExamCapabilityException;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskRuntimeContractConstants;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.session.SessionService;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Owns capability negotiation at the attempt boundary. It only consumes the
 * immutable runtime descriptors exposed by an exam summary or pinned item;
 * it never joins the mutable question-type/profile catalog during delivery.
 */
@Service
public class CapabilityNegotiationService {

    private static final Pattern CAPABILITY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*(?:@[1-9][0-9]*)?");
    private static final int MAX_FINGERPRINT_LENGTH = 2048;

    private final SessionService sessionService;
    private final AssessmentService assessmentService;
    private final CapabilityProperties properties;
    private final AuditLogService auditLogService;
    private final Set<String> allowlistedCapabilities;

    public CapabilityNegotiationService(SessionService sessionService, AssessmentService assessmentService,
            CapabilityProperties properties, AuditLogService auditLogService) {
        this.sessionService = sessionService;
        this.assessmentService = assessmentService;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.allowlistedCapabilities = TaskRuntimeProfileRegistry.all().stream()
                .map(TaskRuntimeProfileDescriptor::requiredClientCapabilities)
                .flatMap(Collection::stream)
                .map(this::canonicalCapability)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Compatibility constructor for focused negotiation tests. */
    public CapabilityNegotiationService(SessionService sessionService, AssessmentService assessmentService,
            CapabilityProperties properties) {
        this(sessionService, assessmentService, properties, null);
    }

    /** Answer-free and side-effect-free preflight. */
    public AttemptPreflightResponse preflight(AttemptPreflightRequest request, CurrentUser caller) {
        var entitlement = sessionService.checkEntitlement(request.sessionPublicId(), caller.userId());
        requireCallerTenant(entitlement.tenantId(), caller);
        SnapshotResponse summary = assessmentService.getSummary(entitlement.snapshotPublicId());
        return evaluateSummary(summary.items(), request.capabilityManifest()).toResponse();
    }

    /** Authoritative new-attempt check. Returns the normalized value persisted on the attempt. */
    public String authorizeStart(java.util.UUID sessionPublicId, java.util.UUID studentPublicId,
            ClientCapabilityManifest manifest, CurrentUser caller) {
        if (studentPublicId == null || caller == null || !studentPublicId.equals(caller.userId())) {
            throw new AttemptNotFoundException();
        }
        var entitlement = sessionService.checkEntitlement(sessionPublicId, studentPublicId);
        requireCallerTenant(entitlement.tenantId(), caller);
        SnapshotResponse summary = assessmentService.getSummary(entitlement.snapshotPublicId());
        CapabilityCheck check = evaluateSummary(summary.items(), manifest);
        try {
            requireCompatible(check);
        } catch (ExamCapabilityException ex) {
            recordFailure(caller, summary.publicId(), ex.getCode());
            throw ex;
        }
        return check.fingerprint();
    }

    /**
     * Existing-attempt path: the ownership lookup has already completed before
     * this method is called. A supplied manifest is checked as well as the
     * stored decision; an omitted manifest reuses the stored decision.
     */
    public void authorizeExisting(ExamAttempt attempt, ClientCapabilityManifest manifest, CurrentUser caller) {
        if (attempt.getPinnedSnapshot() == null) {
            recordFailure(caller, "EXAM_ATTEMPT", attempt.getPublicId(),
                    AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE);
            throw configurationException();
        }
        CapabilityCheck stored = evaluatePinned(attempt.getPinnedSnapshot().getItems(),
                attempt.getCapabilityFingerprint());
        try {
            requireCompatible(stored);
        } catch (ExamCapabilityException ex) {
            recordFailure(caller, attempt.getPinnedSnapshot().getSourceSnapshotPublicId(), ex.getCode());
            throw ex;
        }
        if (manifest != null) {
            CapabilityCheck supplied = evaluatePinned(attempt.getPinnedSnapshot().getItems(), manifest);
            try {
                requireCompatible(supplied);
            } catch (ExamCapabilityException ex) {
                recordFailure(caller, attempt.getPinnedSnapshot().getSourceSnapshotPublicId(), ex.getCode());
                throw ex;
            }
            if (isBlank(attempt.getCapabilityFingerprint())) {
                attempt.setCapabilityFingerprint(supplied.fingerprint());
            }
        }
    }

    /** Called after pinning to defend the preflight decision against a bad source snapshot. */
    public void verifyPinnedForStart(List<PinnedItem> items, String fingerprint) {
        requireCompatible(evaluatePinned(items, fingerprint));
    }

    /** Same verification with audit context for the authoritative start path. */
    public void verifyPinnedForStart(PinnedExamSnapshot pinned, String fingerprint, CurrentUser caller) {
        try {
            requireCompatible(evaluatePinned(pinned.getItems(), fingerprint));
        } catch (ExamCapabilityException ex) {
            recordFailure(caller, pinned.getSourceSnapshotPublicId(), ex.getCode());
            throw ex;
        }
    }

    /** Re-used before every task is returned, including resume and next-task. */
    public void assertStoredCapabilities(ExamAttempt attempt) {
        assertStoredCapabilities(attempt, null);
    }

    /** Re-used before every task is returned and records a blocked delivery. */
    public void assertStoredCapabilities(ExamAttempt attempt, CurrentUser caller) {
        if (attempt.getPinnedSnapshot() == null) {
            ExamCapabilityException ex = configurationException();
            recordFailure(caller, "EXAM_ATTEMPT", attempt.getPublicId(), ex.getCode());
            throw ex;
        }
        try {
            requireCompatible(evaluatePinned(attempt.getPinnedSnapshot().getItems(),
                    attempt.getCapabilityFingerprint()));
        } catch (ExamCapabilityException ex) {
            recordFailure(caller, attempt.getPinnedSnapshot().getSourceSnapshotPublicId(), ex.getCode());
            throw ex;
        }
    }

    /** Records a pin-time runtime rejection when the source snapshot fails before a pinned graph exists. */
    void recordBlockedDelivery(CurrentUser caller, java.util.UUID attemptPublicId, String code) {
        recordFailure(caller, "EXAM_ATTEMPT", attemptPublicId, code);
    }

    private CapabilityCheck evaluateSummary(List<SnapshotResponse.Item> items, ClientCapabilityManifest manifest) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(this::hasInvalidMapping)) {
            return CapabilityCheck.configuration();
        }
        boolean anyRuntime = items.stream().anyMatch(item -> item.runtime() != null);
        boolean allRuntime = items.stream().allMatch(item -> item.runtime() != null);
        if (anyRuntime != allRuntime) {
            return CapabilityCheck.configuration();
        }
        if (!allRuntime) {
            NormalizedCapabilities normalized = normalize(manifest);
            if (manifest == null && !properties.isAllowLegacyMissingManifest()) {
                return CapabilityCheck.configuration();
            }
            return CapabilityCheck.compatible(normalized.fingerprint());
        }
        List<TaskRuntimeProfileDescriptor> profiles = items.stream().map(SnapshotResponse.Item::runtime).toList();
        return evaluateProfiles(profiles, normalize(manifest));
    }

    private CapabilityCheck evaluatePinned(List<PinnedItem> items, String fingerprint) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(this::hasInvalidMapping)) {
            return CapabilityCheck.configuration();
        }
        boolean anyRuntime = items.stream().anyMatch(item -> item.runtimeProfile() != null);
        boolean allRuntime = items.stream().allMatch(item -> item.runtimeProfile() != null);
        if (anyRuntime != allRuntime) {
            return CapabilityCheck.configuration();
        }
        if (!allRuntime) {
            if (!properties.isAllowLegacyMissingManifest() && isBlank(fingerprint)) {
                return CapabilityCheck.configuration();
            }
            if (isBlank(fingerprint)) {
                return CapabilityCheck.compatible("");
            }
            try {
                return CapabilityCheck.compatible(normalizeFingerprint(fingerprint).fingerprint());
            } catch (CapabilityManifestInvalidException ex) {
                return CapabilityCheck.configuration();
            }
        }
        try {
            NormalizedCapabilities normalized = normalizeFingerprint(fingerprint);
            return evaluateProfiles(items.stream().map(PinnedItem::runtimeProfile).toList(), normalized);
        } catch (CapabilityManifestInvalidException ex) {
            return CapabilityCheck.configuration();
        }
    }

    private CapabilityCheck evaluatePinned(List<PinnedItem> items, ClientCapabilityManifest manifest) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(this::hasInvalidMapping)) {
            return CapabilityCheck.configuration();
        }
        boolean anyRuntime = items.stream().anyMatch(item -> item.runtimeProfile() != null);
        boolean allRuntime = items.stream().allMatch(item -> item.runtimeProfile() != null);
        if (anyRuntime != allRuntime) {
            return CapabilityCheck.configuration();
        }
        if (!allRuntime) {
            return CapabilityCheck.compatible(normalize(manifest).fingerprint());
        }
        return evaluateProfiles(items.stream().map(PinnedItem::runtimeProfile).toList(), normalize(manifest));
    }

    private CapabilityCheck evaluateProfiles(List<TaskRuntimeProfileDescriptor> profiles,
            NormalizedCapabilities provided) {
        Set<String> required = new TreeSet<>();
        for (TaskRuntimeProfileDescriptor profile : profiles) {
            if (!isAllowlistedProfile(profile)) {
                return CapabilityCheck.configuration();
            }
            profile.requiredClientCapabilities().stream().map(this::canonicalCapability).forEach(required::add);
        }
        Set<String> missing = new TreeSet<>(required);
        missing.removeAll(provided.values());
        if (!missing.isEmpty()) {
            return CapabilityCheck.missing(missing.stream().map(this::displayCapability).toList());
        }
        return CapabilityCheck.compatible(provided.fingerprint());
    }

    private boolean isAllowlistedProfile(TaskRuntimeProfileDescriptor profile) {
        try {
            return TaskRuntimeProfileRegistry.descriptorFor(profile.taskTypeCode()).equals(profile);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean hasInvalidMapping(SnapshotResponse.Item item) {
        if (!hasConsistentTaskTypeMapping(item.taskType(), item.taskTypeCode(), item.section(),
                item.runtime() == null ? null : item.runtime().taskTypeCode())) {
            return true;
        }
        if (item.runtime() != null) {
            return !TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_CANONICAL
                    .equals(item.runtimeMappingStatus())
                    || !TaskRuntimeContractConstants.MAPPING_VERSION_CANONICAL
                            .equals(item.runtimeMappingVersion());
        }
        return item.runtimeMappingStatus() != null || item.runtimeMappingVersion() != null
                ? !TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_LEGACY.equals(item.runtimeMappingStatus())
                        || !TaskRuntimeContractConstants.MAPPING_VERSION_LEGACY
                                .equals(item.runtimeMappingVersion())
                : false;
    }

    private boolean hasInvalidMapping(PinnedItem item) {
        if (item.hasPartialRuntimeProfile()) {
            return true;
        }
        if (!hasConsistentTaskTypeMapping(item.getTaskType(), item.getTaskTypeCode(), item.getSection(),
                item.runtimeProfile() == null ? null : item.runtimeProfile().taskTypeCode())) {
            return true;
        }
        if (item.runtimeProfile() != null) {
            return !TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_CANONICAL
                    .equals(item.getRuntimeMappingStatus())
                    || !TaskRuntimeContractConstants.MAPPING_VERSION_CANONICAL
                            .equals(item.getRuntimeMappingVersion());
        }
        return item.getRuntimeMappingStatus() != null || item.getRuntimeMappingVersion() != null
                ? !TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_LEGACY
                        .equals(item.getRuntimeMappingStatus())
                        || !TaskRuntimeContractConstants.MAPPING_VERSION_LEGACY
                                .equals(item.getRuntimeMappingVersion())
                : false;
    }

    private boolean hasConsistentTaskTypeMapping(String taskType, String taskTypeCode, String section,
            String runtimeTaskTypeCode) {
        try {
            String normalizedTaskType = TaskTypeCodeCompatibility.normalizeForLookup(taskType);
            String normalizedCode = taskTypeCode == null
                    ? normalizedTaskType
                    : TaskTypeCodeCompatibility.normalizeForLookup(taskTypeCode);
            if (!normalizedTaskType.equals(normalizedCode)
                    || !TaskTypeCodeCompatibility.parse(normalizedCode).getSection().name().equals(section)) {
                return false;
            }
            return runtimeTaskTypeCode == null || normalizedCode.equals(runtimeTaskTypeCode);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private NormalizedCapabilities normalize(ClientCapabilityManifest manifest) {
        if (manifest == null || manifest.capabilities() == null) {
            return new NormalizedCapabilities(Set.of(), "");
        }
        Set<String> normalized = new TreeSet<>();
        for (String raw : manifest.capabilities()) {
            if (raw == null) {
                throw new CapabilityManifestInvalidException();
            }
            String value = raw.trim().toUpperCase(Locale.ROOT);
            if (!CAPABILITY_PATTERN.matcher(value).matches()) {
                throw new CapabilityManifestInvalidException();
            }
            String canonical = canonicalCapability(value);
            // Unknown client capabilities are ignored, never persisted or
            // interpreted as executable server configuration.
            if (allowlistedCapabilities.contains(canonical)) {
                normalized.add(canonical);
            }
        }
        return new NormalizedCapabilities(Set.copyOf(normalized), fingerprint(normalized));
    }

    private NormalizedCapabilities normalizeFingerprint(String fingerprint) {
        if (isBlank(fingerprint)) {
            return new NormalizedCapabilities(Set.of(), "");
        }
        if (fingerprint.length() > MAX_FINGERPRINT_LENGTH) {
            throw new CapabilityManifestInvalidException();
        }
        Set<String> normalized = new TreeSet<>();
        for (String raw : fingerprint.split(",")) {
            if (!CAPABILITY_PATTERN.matcher(raw).matches() || !allowlistedCapabilities.contains(raw)) {
                throw new CapabilityManifestInvalidException();
            }
            normalized.add(raw);
        }
        return new NormalizedCapabilities(Set.copyOf(normalized), fingerprint(normalized));
    }

    private String canonicalCapability(String raw) {
        return raw.contains("@") ? raw : raw + "@1";
    }

    private String displayCapability(String canonical) {
        return canonical.endsWith("@1") ? canonical.substring(0, canonical.length() - 2) : canonical;
    }

    private String fingerprint(Set<String> values) {
        String fingerprint = String.join(",", values.stream().sorted(Comparator.naturalOrder()).toList());
        if (fingerprint.length() > MAX_FINGERPRINT_LENGTH) {
            throw new CapabilityManifestInvalidException();
        }
        return fingerprint;
    }

    private void requireCompatible(CapabilityCheck check) {
        if (!check.canStart()) {
            throw new ExamCapabilityException(check.code(), check.missingCapabilities());
        }
    }

    private void recordFailure(CurrentUser caller, java.util.UUID snapshotPublicId, String code) {
        recordFailure(caller, "EXAM_SNAPSHOT", snapshotPublicId, code);
    }

    private void recordFailure(CurrentUser caller, String aggregateType, java.util.UUID aggregateId, String code) {
        if (auditLogService != null && caller != null && aggregateId != null) {
            auditLogService.recordFailure(caller, aggregateType, aggregateId.toString(),
                    AttemptConstants.RUNTIME_CONTRACT_FAILURE_AUDIT_ACTION,
                    AttemptConstants.RUNTIME_CONTRACT_FAILURE_AUDIT_SUMMARY + ": " + code);
        }
    }

    private ExamCapabilityException configurationException() {
        return new ExamCapabilityException(AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE, List.of());
    }

    private void requireCallerTenant(java.util.UUID entitlementTenantId, CurrentUser caller) {
        if (caller == null || caller.tenantId() == null || entitlementTenantId == null
                || !caller.tenantId().equals(entitlementTenantId)) {
            throw new AttemptNotFoundException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record NormalizedCapabilities(Set<String> values, String fingerprint) {
    }

    private record CapabilityCheck(boolean canStart, List<String> missingCapabilities, String code,
            String userMessage, String fingerprint) {

        private static CapabilityCheck compatible(String fingerprint) {
            return new CapabilityCheck(true, List.of(), null, null, fingerprint);
        }

        private static CapabilityCheck missing(List<String> missingCapabilities) {
            return new CapabilityCheck(false, List.copyOf(missingCapabilities),
                    AttemptConstants.EXAM_REQUIRES_APP_UPDATE,
                    AttemptConstants.EXAM_REQUIRES_APP_UPDATE_MESSAGE, "");
        }

        private static CapabilityCheck configuration() {
            return new CapabilityCheck(false, List.of(),
                    AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE,
                    AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE_MESSAGE, "");
        }

        private AttemptPreflightResponse toResponse() {
            return new AttemptPreflightResponse(canStart, missingCapabilities, code, userMessage);
        }
    }
}
