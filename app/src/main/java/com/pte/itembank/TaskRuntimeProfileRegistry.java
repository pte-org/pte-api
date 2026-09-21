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
                ANSWER_SCHEMA_VERSION, scoringProfileKey, SCORING_PROFILE_VERSION, List.of(capabilities), "ACTIVE"));
    }
}
