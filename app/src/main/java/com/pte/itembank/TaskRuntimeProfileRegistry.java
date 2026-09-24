package com.pte.itembank;

import com.pte.itembank.domain.enums.PteTaskType;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Backend allowlist for the runtime contract of every standard PTE task type.
 * No database value can introduce a renderer or scorer outside this map.
 */
public final class TaskRuntimeProfileRegistry {

    private static final int PROFILE_VERSION = 1;
    private static final int ANSWER_SCHEMA_VERSION = 1;
    private static final int SCORING_PROFILE_VERSION = 1;
    private static final Map<PteTaskType, TaskRuntimeProfileDescriptor> PROFILES = buildProfiles();

    private TaskRuntimeProfileRegistry() {
    }

    public static TaskRuntimeProfileDescriptor descriptorFor(String taskTypeCode) {
        PteTaskType taskType = TaskTypeCodeCompatibility.parse(taskTypeCode);
        return PROFILES.get(taskType);
    }

    public static List<TaskRuntimeProfileDescriptor> all() {
        return Arrays.stream(PteTaskType.values()).map(PROFILES::get).toList();
    }

    /** Resolves a standard profile by its semantic screen key and version. */
    public static TaskRuntimeProfileDescriptor descriptorForScreen(String screenKey, int profileVersion) {
        return all().stream()
                .filter(profile -> profile.rendererKey().equals(screenKey)
                        && profile.profileVersion() == profileVersion)
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks the immutable, code-owned part of a persisted/profile response.
     * The lifecycle status is deliberately excluded from this comparison so a
     * retired profile can continue to serve historical templates and
     * snapshots. Callers that resolve a new profile must still require ACTIVE.
     */
    public static boolean isAllowlistedContract(TaskRuntimeProfileDescriptor actual) {
        if (actual == null || !("ACTIVE".equals(actual.status()) || "RETIRED".equals(actual.status()))) {
            return false;
        }
        try {
            TaskRuntimeProfileDescriptor expected = descriptorFor(actual.taskTypeCode());
            return expected != null
                    && expected.taskTypeCode().equals(actual.taskTypeCode())
                    && expected.profileKey().equals(actual.profileKey())
                    && expected.profileVersion() == actual.profileVersion()
                    && expected.behaviorKey().equals(actual.behaviorKey())
                    && expected.rendererKey().equals(actual.rendererKey())
                    && expected.answerSchemaVersion() == actual.answerSchemaVersion()
                    && expected.scoringProfileKey().equals(actual.scoringProfileKey())
                    && expected.scoringProfileVersion() == actual.scoringProfileVersion()
                    && sameCapabilities(expected.requiredClientCapabilities(), actual.requiredClientCapabilities())
                    && expected.screenKey().equals(actual.screenKey())
                    && expected.contractVersion() == actual.contractVersion()
                    && expected.scoringMode().equals(actual.scoringMode())
                    && expected.authoringContractKey().equals(actual.authoringContractKey())
                    && expected.authoringContractVersion().equals(actual.authoringContractVersion());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Client capabilities are a set; their persisted CSV order is not contractual. */
    static boolean sameCapabilities(List<String> left, List<String> right) {
        return left != null && right != null && left.size() == right.size()
                && left.stream().sorted().toList().equals(right.stream().sorted().toList());
    }

    /**
     * Validates the canonical screen-contract projection independently of a
     * logical task key. Custom task keys may reuse a standard screen, so the
     * task-key-specific check above is not sufficient for the capability
     * registry boundary.
     */
    public static boolean isAllowlistedScreenContract(TaskRuntimeContractDescriptor actual) {
        if (actual == null || !("ACTIVE".equals(actual.status()) || "RETIRED".equals(actual.status()))) {
            return false;
        }
        return all().stream()
                .filter(profile -> profile.screenKey().equals(actual.screenKey())
                        && profile.contractVersion() == actual.contractVersion())
                .anyMatch(profile -> profileKeyMatches(profile, actual)
                        && "1.0.0".equals(actual.minSupportedAppVersion())
                        && ("PTE." + profile.taskTypeCode() + "_AUTHORING")
                                .equals(actual.authoringContractKey())
                        && Integer.valueOf(1).equals(actual.authoringContractVersion())
                        && authoringRequirementsFor(profile.taskTypeCode()).equals(actual.authoringRequirements()));
    }

    /** Returns the code-owned authoring contract for a standard runtime key. */
    public static TaskAuthoringRequirements authoringRequirementsFor(String taskTypeCode) {
        PteTaskType taskType = TaskTypeCodeCompatibility.parse(taskTypeCode);
        return new TaskAuthoringRequirements(
                taskType.requiresAudioPrompt(),
                taskType.requiresImagePrompt(),
                taskType.requiresPromptText(),
                taskType.requiresOptions(),
                taskType.requiresCorrectAnswer(),
                taskType.requiresWordCount(),
                taskType == PteTaskType.MC_READING_SINGLE || taskType == PteTaskType.MC_LISTENING_SINGLE,
                taskType == PteTaskType.RE_ORDER_PARAGRAPHS);
    }

    private static boolean profileKeyMatches(TaskRuntimeProfileDescriptor profile,
            TaskRuntimeContractDescriptor actual) {
        return profile.profileKey().equals(actual.profileKey())
                && profile.profileVersion() == actual.profileVersion()
                && profile.behaviorKey().equals(actual.behaviorKey())
                && profile.rendererKey().equals(actual.rendererKey())
                && profile.answerSchemaVersion() == actual.answerSchemaVersion()
                && profile.scoringProfileKey().equals(actual.scoringProfileKey())
                && profile.scoringProfileVersion() == actual.scoringProfileVersion()
                && sameCapabilities(profile.requiredClientCapabilities(), actual.requiredClientCapabilities())
                && profile.scoringMode().equals(actual.scoringMode());
    }

    private static Map<PteTaskType, TaskRuntimeProfileDescriptor> buildProfiles() {
        EnumMap<PteTaskType, TaskRuntimeProfileDescriptor> profiles = new EnumMap<>(PteTaskType.class);
        add(profiles, PteTaskType.PERSONAL_INTRODUCTION, "PERSONAL_INTRODUCTION", "PERSONAL_INTRODUCTION_V1",
                "UNSCORED", "AUDIO_RECORDING");
        add(profiles, PteTaskType.READ_ALOUD, "RECORD_RESPONSE", "READ_ALOUD_V1", "AI_SPEECH",
                "AUDIO_RECORDING");
        add(profiles, PteTaskType.REPEAT_SENTENCE, "RECORD_RESPONSE", "REPEAT_SENTENCE_V1", "AI_SPEECH",
                "AUDIO_PLAYBACK", "AUDIO_RECORDING");
        add(profiles, PteTaskType.DESCRIBE_IMAGE, "RECORD_RESPONSE", "DESCRIBE_IMAGE_V1", "AI_SPEECH",
                "IMAGE_DISPLAY", "AUDIO_RECORDING");
        add(profiles, PteTaskType.RE_TELL_LECTURE, "RECORD_RESPONSE", "RE_TELL_LECTURE_V1", "AI_SPEECH",
                "AUDIO_PLAYBACK", "AUDIO_RECORDING");
        add(profiles, PteTaskType.ANSWER_SHORT_QUESTION, "RECORD_RESPONSE", "ANSWER_SHORT_QUESTION_V1",
                "AI_SPEECH", "AUDIO_PLAYBACK", "AUDIO_RECORDING");
        add(profiles, PteTaskType.RESPOND_TO_A_SITUATION, "RECORD_RESPONSE", "RESPOND_TO_A_SITUATION_V1",
                "AI_SPEECH", "AUDIO_PLAYBACK", "AUDIO_RECORDING");
        add(profiles, PteTaskType.SUMMARIZE_GROUP_DISCUSSION, "RECORD_RESPONSE",
                "SUMMARIZE_GROUP_DISCUSSION_V1", "AI_SPEECH", "AUDIO_PLAYBACK", "AUDIO_RECORDING");
        add(profiles, PteTaskType.SUMMARIZE_WRITTEN_TEXT, "TEXT_RESPONSE", "SUMMARIZE_WRITTEN_TEXT_V1",
                "AI_TEXT", "TEXT_INPUT");
        add(profiles, PteTaskType.WRITE_ESSAY, "TEXT_RESPONSE", "WRITE_ESSAY_V1", "AI_TEXT", "TEXT_INPUT");
        add(profiles, PteTaskType.MC_READING_SINGLE, "SELECT_OPTION", "MC_READING_SINGLE_V1", "OBJECTIVE",
                "OPTION_SELECTION");
        add(profiles, PteTaskType.MC_READING_MULTIPLE, "SELECT_OPTIONS", "MC_READING_MULTIPLE_V1", "OBJECTIVE",
                "OPTION_SELECTION");
        add(profiles, PteTaskType.RE_ORDER_PARAGRAPHS, "ORDER_OPTIONS", "RE_ORDER_PARAGRAPHS_V1", "OBJECTIVE",
                "DRAG_AND_DROP");
        add(profiles, PteTaskType.FILL_IN_THE_BLANKS_DRAG_AND_DROP, "FILL_BLANKS",
                "FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1", "OBJECTIVE", "DRAG_AND_DROP");
        add(profiles, PteTaskType.FILL_IN_THE_BLANKS_DROPDOWN, "FILL_BLANKS",
                "FILL_IN_THE_BLANKS_DROPDOWN_V1", "OBJECTIVE", "DROPDOWN_SELECTION");
        add(profiles, PteTaskType.SUMMARIZE_SPOKEN_TEXT, "TEXT_RESPONSE", "SUMMARIZE_SPOKEN_TEXT_V1", "AI_TEXT",
                "AUDIO_PLAYBACK", "TEXT_INPUT");
        add(profiles, PteTaskType.MC_LISTENING_SINGLE, "SELECT_OPTION", "MC_LISTENING_SINGLE_V1", "OBJECTIVE",
                "AUDIO_PLAYBACK", "OPTION_SELECTION");
        add(profiles, PteTaskType.MC_LISTENING_MULTIPLE, "SELECT_OPTIONS", "MC_LISTENING_MULTIPLE_V1", "OBJECTIVE",
                "AUDIO_PLAYBACK", "OPTION_SELECTION");
        add(profiles, PteTaskType.FILL_IN_THE_BLANKS_TYPE_IN, "TEXT_RESPONSE", "FILL_IN_THE_BLANKS_TYPE_IN_V1",
                "OBJECTIVE", "AUDIO_PLAYBACK", "TEXT_INPUT");
        add(profiles, PteTaskType.HIGHLIGHT_CORRECT_SUMMARY, "HIGHLIGHT_OPTIONS",
                "HIGHLIGHT_CORRECT_SUMMARY_V1", "OBJECTIVE", "AUDIO_PLAYBACK", "HIGHLIGHT_SELECTION");
        add(profiles, PteTaskType.SELECT_MISSING_WORD, "SELECT_OPTION", "SELECT_MISSING_WORD_V1", "OBJECTIVE",
                "AUDIO_PLAYBACK", "OPTION_SELECTION");
        add(profiles, PteTaskType.HIGHLIGHT_INCORRECT_WORDS, "HIGHLIGHT_TEXT",
                "HIGHLIGHT_INCORRECT_WORDS_V1", "OBJECTIVE", "AUDIO_PLAYBACK", "HIGHLIGHT_SELECTION");
        add(profiles, PteTaskType.WRITE_FROM_DICTATION, "TEXT_RESPONSE", "WRITE_FROM_DICTATION_V1", "OBJECTIVE",
                "AUDIO_PLAYBACK", "TEXT_INPUT");
        return Map.copyOf(profiles);
    }

    private static void add(Map<PteTaskType, TaskRuntimeProfileDescriptor> profiles, PteTaskType taskType,
            String behaviorKey, String rendererKey, String scoringProfileKey, String... capabilities) {
        profiles.put(taskType, new TaskRuntimeProfileDescriptor(
                taskType.name(), "PTE." + taskType.name(), PROFILE_VERSION, behaviorKey, rendererKey,
                ANSWER_SCHEMA_VERSION, scoringProfileKey, SCORING_PROFILE_VERSION,
                Arrays.stream(capabilities).sorted().toList(), "ACTIVE"));
    }
}
