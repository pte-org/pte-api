package com.pte.scoring.internal.service;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.scoring.domain.enums.ScoringMethod;

import java.util.Map;
import java.util.Optional;

/**
 * Scoring owns executable strategy selection. It consumes the itembank's
 * data-only profile descriptor and registers only semantic profile keys and
 * versions; no database class name, formula or script is ever interpreted.
 */
final class ScoringProfileRegistry {

    private static final Map<String, SupportedProfile> PROFILES = Map.of(
            "AI_SPEECH", new SupportedProfile(ScoringMethod.AI_SPEECH, 1),
            "AI_TEXT", new SupportedProfile(ScoringMethod.AI_TEXT, 1),
            "OBJECTIVE", new SupportedProfile(ScoringMethod.OBJECTIVE, 1),
            "UNSCORED", new SupportedProfile(ScoringMethod.UNSCORED, 1));

    private ScoringProfileRegistry() {
    }

    static Optional<ScoringMethod> resolve(TaskRuntimeProfileDescriptor profile) {
        if (!isSupportedRuntimeProfile(profile)) {
            return Optional.empty();
        }
        SupportedProfile supported = PROFILES.get(profile.scoringProfileKey());
        if (supported == null || supported.version() != profile.scoringProfileVersion()) {
            return Optional.empty();
        }
        return Optional.of(supported.method());
    }

    private static boolean isSupportedRuntimeProfile(TaskRuntimeProfileDescriptor profile) {
        if (profile == null || profile.taskTypeCode() == null || profile.profileKey() == null
                || profile.profileKey().isBlank()
                || profile.scoringProfileKey() == null || profile.scoringProfileVersion() < 1
                || profile.screenKey() == null || profile.screenKey().isBlank()
                || profile.contractVersion() < 1
                || !("ACTIVE".equals(profile.status()) || "RETIRED".equals(profile.status()))) {
            return false;
        }
        if (TaskTypeCodeCompatibility.isStandard(profile.taskTypeCode())) {
            return TaskRuntimeProfileRegistry.isAllowlistedContract(profile);
        }
        // Custom keys may reuse a release-owned profile. The executable
        // strategy is still restricted to this registry's semantic key/version
        // allowlist; no database class/formula is interpreted.
        return profile.behaviorKey() != null && !profile.behaviorKey().isBlank()
                && profile.rendererKey() != null && !profile.rendererKey().isBlank()
                && profile.answerSchemaVersion() >= 1
                && ("SCORED".equals(profile.scoringMode()) || "NONE".equals(profile.scoringMode()));
    }

    private record SupportedProfile(ScoringMethod method, int version) {
    }
}
