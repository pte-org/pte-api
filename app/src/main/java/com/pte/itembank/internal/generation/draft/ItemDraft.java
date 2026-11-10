package com.pte.itembank.internal.generation.draft;

import java.util.List;

/**
 * Sealed interface for AI-generated question drafts, organized by interaction shape.
 * Each record represents one task type's JSON structure, normalized for mapper and validator.
 */
public sealed interface ItemDraft {

    String topic();

    /**
     * Choice-based: MC_READING_SINGLE, MC_READING_MULTIPLE. Includes passage and question
     * with multiple-choice options and one or more correct answer indices.
     */
    record ChoiceDraft(
            String topic,
            String passage,
            String question,
            List<String> options,
            List<Integer> correctIndexes) implements ItemDraft {}

    /**
     * Blank-fill: FIB_DROPDOWN, FIB_DRAG_AND_DROP. Passage contains slots;
     * each slot has a correct answer and distractors.
     */
    record BlankFillDraft(
            String topic,
            String passageWithSlots,
            List<Slot> slots) implements ItemDraft {

        public record Slot(String answer, List<String> distractors) {}
    }

    /**
     * Reorder paragraphs: RE_ORDER_PARAGRAPHS. Paragraphs list is in correct order;
     * storage layer will shuffle using a seed.
     */
    record ReorderDraft(
            String topic,
            List<String> paragraphs) implements ItemDraft {}

    /**
     * Passage-based: SUMMARIZE_WRITTEN_TEXT, WRITE_ESSAY, READ_ALOUD.
     * May include key points (for summaries) or essay prompt.
     */
    record PassageDraft(
            String topic,
            String passage,
            List<String> keyPoints,
            String essayPrompt) implements ItemDraft {

        /** The text the question is about: the passage, or the essay prompt for an essay (which has no passage). */
        public String mainText() {
            return passage != null && !passage.isBlank() ? passage : essayPrompt;
        }
    }

    /**
     * Listening-based audio: WFD, RS, ASQ, SST, MC_LISTENING_SINGLE/MULTIPLE, HCS, SMW, L-FIB.
     * Transcript is stored separately; choice/blank fill prompts use the existing structure.
     */
    record ListeningDraft(
            String topic,
            String transcript,
            String question,
            List<String> options,
            List<Integer> correctIndexes,
            List<Slot> blanks,
            String answer) implements ItemDraft {

        public record Slot(String answer, List<String> distractors) {}
    }

    /**
     * Highlight Incorrect Words (HIW): audio with displayed text containing changed words.
     */
    record HiwDraft(
            String topic,
            String transcript,
            String displayedText,
            int[] changedIndexes) implements ItemDraft {}

    /**
     * Dialogue-based audio: SUMMARIZE_GROUP_DISCUSSION (3 speakers, multiple turns),
     * RESPOND_TO_A_SITUATION (situation + 1–2 turns + key points).
     * Each turn has speaker (A, B, C) and text; key points are items to address.
     */
    record DialogueDraft(
            String topic,
            String situation,
            List<Turn> turns,
            List<String> keyPoints) implements ItemDraft {

        public record Turn(String speaker, String text) {}
    }

    /**
     * Chart-based: DESCRIBE_IMAGE with numeric visualization (BAR, LINE, PIE, TABLE).
     * Chart type determines rendering; series contains categories and values.
     * Key points capture insights (MAX, MIN, TREND_UP, TREND_DOWN, COMPARE, TOTAL).
     */
    record ChartDraft(
            String topic,
            String chartType,
            String title,
            String xLabel,
            String yLabel,
            String unit,
            List<String> categories,
            List<Series> series,
            List<KeyPoint> keyPoints) implements ItemDraft {

        public record Series(String name, List<Double> values) {}
        public record KeyPoint(String kind, String ref, String text) {}
    }
}
