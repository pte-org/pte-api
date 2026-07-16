package com.aptis.modules.questionbank.domain;

import java.util.List;
import java.util.Map;

import com.aptis.modules.questionbank.domain.enums.PteTaskType;
import com.aptis.modules.questionbank.domain.enums.Skill;

/**
 * Static task-type -> skill-contribution mapping (config-driven by design,
 * not a DB join-table — PTE's 20 item types are a fixed taxonomy, so a
 * per-question mapping row would be pure duplication).
 *
 * Skill sets below follow Pearson's publicly documented PTE Academic Score
 * Guide contribution matrix. Weights are uniform (1.0) placeholders — Phase 7
 * (Score Aggregation) owns refining the weighting model; this mapping only
 * establishes WHICH skills each task type contributes to.
 */
public final class PteTaskTypeSkillMapping {

    public record SkillContribution(Skill skill, float weight) {}

    private static final Map<PteTaskType, List<SkillContribution>> MAPPING = buildMapping();

    private PteTaskTypeSkillMapping() {
    }

    public static List<SkillContribution> skillsFor(PteTaskType taskType) {
        return MAPPING.getOrDefault(taskType, List.of());
    }

    private static Map<PteTaskType, List<SkillContribution>> buildMapping() {
        return Map.ofEntries(
                Map.entry(PteTaskType.PERSONAL_INTRODUCTION, List.of()),

                Map.entry(PteTaskType.READ_ALOUD, contributes(
                        Skill.SPEAKING, Skill.READING, Skill.ORAL_FLUENCY, Skill.PRONUNCIATION)),
                Map.entry(PteTaskType.REPEAT_SENTENCE, contributes(
                        Skill.SPEAKING, Skill.LISTENING, Skill.ORAL_FLUENCY, Skill.PRONUNCIATION)),
                Map.entry(PteTaskType.DESCRIBE_IMAGE, contributes(
                        Skill.SPEAKING, Skill.ORAL_FLUENCY, Skill.PRONUNCIATION)),
                Map.entry(PteTaskType.RETELL_LECTURE, contributes(
                        Skill.SPEAKING, Skill.LISTENING, Skill.ORAL_FLUENCY, Skill.PRONUNCIATION)),
                Map.entry(PteTaskType.ANSWER_SHORT_QUESTION, contributes(
                        Skill.SPEAKING, Skill.LISTENING, Skill.VOCABULARY)),

                Map.entry(PteTaskType.SUMMARIZE_WRITTEN_TEXT, contributes(
                        Skill.WRITING, Skill.READING, Skill.GRAMMAR, Skill.VOCABULARY, Skill.WRITTEN_DISCOURSE)),
                Map.entry(PteTaskType.WRITE_ESSAY, contributes(
                        Skill.WRITING, Skill.GRAMMAR, Skill.VOCABULARY, Skill.WRITTEN_DISCOURSE, Skill.SPELLING)),

                Map.entry(PteTaskType.READING_FILL_IN_THE_BLANKS, contributes(
                        Skill.READING, Skill.VOCABULARY)),
                Map.entry(PteTaskType.READING_WRITING_FILL_IN_THE_BLANKS, contributes(
                        Skill.READING, Skill.WRITING, Skill.VOCABULARY, Skill.GRAMMAR)),
                Map.entry(PteTaskType.MULTIPLE_CHOICE_READING_SINGLE_ANSWER, contributes(
                        Skill.READING)),
                Map.entry(PteTaskType.MULTIPLE_CHOICE_READING_MULTIPLE_ANSWER, contributes(
                        Skill.READING)),
                Map.entry(PteTaskType.RE_ORDER_PARAGRAPHS, contributes(
                        Skill.READING)),

                Map.entry(PteTaskType.SUMMARIZE_SPOKEN_TEXT, contributes(
                        Skill.LISTENING, Skill.WRITING, Skill.GRAMMAR, Skill.VOCABULARY,
                        Skill.SPELLING, Skill.WRITTEN_DISCOURSE)),
                Map.entry(PteTaskType.MULTIPLE_CHOICE_LISTENING_SINGLE_ANSWER, contributes(
                        Skill.LISTENING)),
                Map.entry(PteTaskType.MULTIPLE_CHOICE_LISTENING_MULTIPLE_ANSWER, contributes(
                        Skill.LISTENING)),
                Map.entry(PteTaskType.LISTENING_FILL_IN_THE_BLANKS, contributes(
                        Skill.LISTENING, Skill.WRITING, Skill.SPELLING)),
                Map.entry(PteTaskType.HIGHLIGHT_CORRECT_SUMMARY, contributes(
                        Skill.LISTENING)),
                Map.entry(PteTaskType.SELECT_MISSING_WORD, contributes(
                        Skill.LISTENING)),
                Map.entry(PteTaskType.HIGHLIGHT_INCORRECT_WORDS, contributes(
                        Skill.LISTENING, Skill.READING)),
                Map.entry(PteTaskType.WRITE_FROM_DICTATION, contributes(
                        Skill.LISTENING, Skill.WRITING, Skill.SPELLING, Skill.GRAMMAR, Skill.VOCABULARY)));
    }

    private static List<SkillContribution> contributes(Skill... skills) {
        return List.of(skills).stream()
                .map(skill -> new SkillContribution(skill, 1.0f))
                .toList();
    }
}
