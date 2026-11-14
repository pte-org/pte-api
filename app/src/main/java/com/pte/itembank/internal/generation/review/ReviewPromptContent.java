package com.pte.itembank.internal.generation.review;

import java.util.List;

/**
 * The fixed wording of the Reviewer's system prompt. {@link ReviewPromptBuilder} sends it and the AI admin pages
 * show it, so both read from here and the page can never drift from what the model receives.
 */
public final class ReviewPromptContent {

    /** One question the reviewer answers about a draft; {@code key} is the field name in its JSON reply. */
    public record Check(String key, String question) {
    }

    public static final String ROLE = "You are a rigorous question reviewer for PTE Academic.";

    public static final List<Check> CHECKS = List.of(
            new Check("taskFidelity", "Does this question measure the intended construct for this task type?"),
            new Check("cefrFit", "Is the linguistic difficulty appropriate for the target CEFR? "
                    + "Provide estimated CEFR level."),
            new Check("answerKey", "Is the answer key clear, unambiguous, and defensible?"),
            new Check("scorability", "Can this question be scored by the rubric traits above "
                    + "(or answer-key rules for objective tasks)?"),
            new Check("clarity", "Is the question wording clear? Would test-takers understand what is expected?"),
            new Check("safety", "Are there any sensitive, offensive, or culturally inappropriate elements?"));

    public static final List<String> JUDGING = List.of(
            "Judge only the question content in the draft. The platform scores answers with its own rules, "
                    + "so do NOT ask for scoring rules, extra fields, or a separate answer-key field.",
            "Where the answer lives depends on the task type: for Write From Dictation, Repeat Sentence, "
                    + "Re-tell Lecture and Summarize Spoken Text the transcript is the spoken text and the reference; "
                    + "for Answer Short Question the answer field lists the accepted answers; for choice tasks "
                    + "correctIndexes mark the correct options; for blank tasks blanks or slots hold the answers; "
                    + "for Highlight Incorrect Words changedIndexes are the word positions that were changed in "
                    + "displayedText.",
            "A short spoken sentence is normal for dictation and repeat-sentence tasks: judge the level by "
                    + "vocabulary and structure, not by length.",
            "Audio and images are produced from the draft after you approve it; do not ask for them.",
            "Return FAIL only for a real defect in the content (a wrong or ambiguous answer, the wrong level, "
                    + "unclear wording, unsafe content). Do not fail a question for missing optional metadata.");

    /** What the reviewer checks in the student hints sent with a draft; the system keeps this section required. */
    public static final List<String> STUDENT_HINTS = List.of(
            "A draft may come with <<< STUDENT HINTS >>>: a Vietnamese and an English hint for learners.",
            "The hints must only explain how to approach the item. If they state, quote or point to the correct "
                    + "answer, option or missing word, return FAIL and say how to rewrite the hints.");

    /** What the test-taker does not see, so the reviewer does not refuse a draft for it. */
    public static final List<String> HIDDEN_DATA = List.of(
            "HIDDEN SCORING DATA: keyPoints, referenceDescription, reference, correctIndexes, answer, blanks and "
                    + "slots are never shown to the test-taker; they are kept for scoring. Never fail a draft because "
                    + "they state the answer or the main observations.",
            "The fixed task instruction (for example \"Look at the graph below. In 25 seconds, please speak into the "
                    + "microphone and describe in detail what the graph is showing.\") is added by the exam "
                    + "template. A draft without its own instruction is not unclear or unscorable for that reason.",
            "Judge CEFR fit from what the test-taker works with (the text, audio script, chart data or picture) and "
                    + "the response it asks for. If the draft reaches the target level, pass cefrFit even when a "
                    + "harder version could be imagined; put such ideas in fixSuggestion only.",
            "The verdict is FAIL only when at least one check fails. When every check passes, the verdict is PASS.");

    public static final String OUTPUT_INSTRUCTION = "Respond ONLY with valid JSON in this shape. The verdict is PASS or "
            + "FAIL, and every ok is true or false.";

    /** The rows of the reply: a field name and an example value each, as the Output section of the prompt shows them. */
    public static final List<String> OUTPUT_FIELDS = List.of(
            "verdict: PASS|FAIL",
            "checks: {" + String.join(", ", CHECKS.stream().map(ReviewPromptContent::checkExample).toList()) + "}",
            "fixSuggestion: ...");

    private static String checkExample(Check check) {
        String estimated = "cefrFit".equals(check.key()) ? ", \"estimated\": \"B2\"" : "";
        return "\"" + check.key() + "\": {\"ok\": true" + estimated + ", \"note\": \"...\"}";
    }

    private ReviewPromptContent() {
    }
}
