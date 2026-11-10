package com.pte.itembank.internal.generation.validate.audio;

import com.pte.itembank.internal.generation.validate.DraftRuleValidator;
import com.pte.itembank.internal.generation.validate.ValidationIssue;

import com.pte.itembank.internal.generation.draft.ItemDraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-specific validators for audio tasks.
 * Validates constraints specific to each task type.
 */
public class AudioTypeValidators {

    /**
     * Validates ASQ (Answer Short Question): answer must be 1-3 words.
     */
    public static class AsqValidator implements DraftRuleValidator {
        @Override
        public List<ValidationIssue> validate(ItemDraft draft) {
            if (!(draft instanceof ItemDraft.ListeningDraft listeningDraft)) {
                return List.of();
            }
            List<ValidationIssue> issues = new ArrayList<>();
            issues.addAll(AudioCommonValidator.validateAudioCommonRules(
                    listeningDraft.transcript(), "transcript"));
            if (listeningDraft.answer() == null || listeningDraft.answer().isBlank()) {
                issues.add(new ValidationIssue("ANSWER_REQUIRED",
                        "Provide the accepted answer(s) in the answer field."));
            } else {
                for (String accepted : listeningDraft.answer().split("/")) {
                    int words = accepted.trim().isEmpty() ? 0 : accepted.trim().split("\s+").length;
                    if (words < 1 || words > 3) {
                        issues.add(new ValidationIssue("ANSWER_LENGTH", "Each accepted answer must be one to three words."));
                        break;
                    }
                }
            }
            return issues;
        }
    }

    /**
     * Validates WFD (Write From Dictation): common audio rules.
     * Word range validation deferred to rubric-aware scoring phase.
     */
    public static class WfdValidator implements DraftRuleValidator {
        @Override
        public List<ValidationIssue> validate(ItemDraft draft) {
            if (!(draft instanceof ItemDraft.ListeningDraft listeningDraft)) {
                return List.of();
            }
            return AudioCommonValidator.validateAudioCommonRules(
                    listeningDraft.transcript(), "transcript");
        }
    }

    /**
     * Validates RS (Repeat Sentence): common audio rules.
     * Word range validation deferred to rubric-aware scoring phase.
     */
    public static class RsValidator implements DraftRuleValidator {
        @Override
        public List<ValidationIssue> validate(ItemDraft draft) {
            if (!(draft instanceof ItemDraft.ListeningDraft listeningDraft)) {
                return List.of();
            }
            return AudioCommonValidator.validateAudioCommonRules(
                    listeningDraft.transcript(), "transcript");
        }
    }

    /**
     * Validates SST (Summarize Spoken Text): common audio rules.
     * Word range validation deferred to rubric-aware scoring phase.
     */
    public static class SstValidator implements DraftRuleValidator {
        @Override
        public List<ValidationIssue> validate(ItemDraft draft) {
            if (!(draft instanceof ItemDraft.ListeningDraft listeningDraft)) {
                return List.of();
            }
            return AudioCommonValidator.validateAudioCommonRules(
                    listeningDraft.transcript(), "transcript");
        }
    }

    /**
     * Validates SMW (Select Missing Word): common audio rules.
     * Word range validation deferred to rubric-aware scoring phase.
     */
    public static class SmwValidator implements DraftRuleValidator {
        @Override
        public List<ValidationIssue> validate(ItemDraft draft) {
            if (!(draft instanceof ItemDraft.ListeningDraft listeningDraft)) {
                return List.of();
            }
            return AudioCommonValidator.validateAudioCommonRules(
                    listeningDraft.transcript(), "transcript");
        }
    }

    /**
     * Validates RL (Re-tell Lecture): common audio rules.
     * Word range validation deferred to rubric-aware scoring phase.
     */
    public static class RlValidator implements DraftRuleValidator {
        @Override
        public List<ValidationIssue> validate(ItemDraft draft) {
            if (!(draft instanceof ItemDraft.ListeningDraft listeningDraft)) {
                return List.of();
            }
            return AudioCommonValidator.validateAudioCommonRules(
                    listeningDraft.transcript(), "transcript");
        }
    }

    /**
     * Validates HCS (Highlight Correct Summary): common audio rules.
     * Word range validation deferred to rubric-aware scoring phase.
     */
    public static class HcsValidator implements DraftRuleValidator {
        @Override
        public List<ValidationIssue> validate(ItemDraft draft) {
            if (!(draft instanceof ItemDraft.ListeningDraft listeningDraft)) {
                return List.of();
            }
            return AudioCommonValidator.validateAudioCommonRules(
                    listeningDraft.transcript(), "transcript");
        }
    }
}
