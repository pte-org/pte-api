package com.pte.itembank.internal.generation.draft.audio;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.dto.request.OptionRequest;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores each listening draft the way the question bank, the exam client and the scorers expect it (checked against
 * the task-type rules and the existing questions):
 * <ul>
 *   <li>{@code referenceAnswerText} holds the transcript. It is never sent to the candidate, and the scorers read it
 *       as the source text (Re-tell Lecture, Summarize Spoken Text, Repeat Sentence).</li>
 *   <li>{@code promptText} is only what the candidate sees: an instruction, a question, or a passage with
 *       {{n}} gaps.</li>
 *   <li>{@code correctAnswerText} holds the answer where the task is scored against text.</li>
 * </ul>
 */
@Component
public class AudioDraftMapper {

    /** What a Summarize Spoken Text candidate is asked to do; the task type requires a prompt text. */
    static final String SUMMARIZE_SPOKEN_TEXT_INSTRUCTION =
            "You will hear a lecture. Write a summary for a fellow student who was not present. "
                    + "Write 50-70 words.";
    private static final int SUMMARY_MIN_COUNT = 50;
    private static final int SUMMARY_MAX_COUNT = 70;
    private static final String GAP_FORMAT = "{{%d}}";

    public CreateQuestionRequest mapWriteFromDictation(ItemDraft.ListeningDraft draft, CefrLevel cefr,
                                                       QuestionPool pool) {
        return request("WRITE_FROM_DICTATION", null, draft.transcript(), draft.transcript(), null, null, null, cefr,
                pool);
    }

    public CreateQuestionRequest mapRepeatSentence(ItemDraft.ListeningDraft draft, CefrLevel cefr, QuestionPool pool) {
        return request("REPEAT_SENTENCE", null, draft.transcript(), null, null, null, null, cefr, pool);
    }

    /** The question is only heard; the accepted answers ("a / b") are what the answer is checked against. */
    public CreateQuestionRequest mapAnswerShortQuestion(ItemDraft.ListeningDraft draft, CefrLevel cefr,
                                                        QuestionPool pool) {
        return request("ANSWER_SHORT_QUESTION", null, draft.transcript(), draft.answer(), null, null, null, cefr,
                pool);
    }

    public CreateQuestionRequest mapSummarizeSpokenText(ItemDraft.ListeningDraft draft, CefrLevel cefr,
                                                        QuestionPool pool) {
        return request("SUMMARIZE_SPOKEN_TEXT", SUMMARIZE_SPOKEN_TEXT_INSTRUCTION, draft.transcript(), null, null,
                SUMMARY_MIN_COUNT, SUMMARY_MAX_COUNT, cefr, pool);
    }

    public CreateQuestionRequest mapMcListeningSingle(ItemDraft.ListeningDraft draft, CefrLevel cefr,
                                                      QuestionPool pool) {
        return choice("MC_LISTENING_SINGLE", draft, cefr, pool);
    }

    public CreateQuestionRequest mapMcListeningMultiple(ItemDraft.ListeningDraft draft, CefrLevel cefr,
                                                        QuestionPool pool) {
        return choice("MC_LISTENING_MULTIPLE", draft, cefr, pool);
    }

    public CreateQuestionRequest mapHighlightCorrectSummary(ItemDraft.ListeningDraft draft, CefrLevel cefr,
                                                            QuestionPool pool) {
        return choice("HIGHLIGHT_CORRECT_SUMMARY", draft, cefr, pool);
    }

    public CreateQuestionRequest mapSelectMissingWord(ItemDraft.ListeningDraft draft, CefrLevel cefr,
                                                      QuestionPool pool) {
        return choice("SELECT_MISSING_WORD", draft, cefr, pool);
    }

    /**
     * The candidate types the missing words: the passage shown has {{0}}, {{1}}, ... where the answers were, and the
     * answers are stored as a JSON list in order.
     */
    public CreateQuestionRequest mapListeningFillBlanks(ItemDraft.ListeningDraft draft, CefrLevel cefr,
                                                        QuestionPool pool) {
        List<String> answers = new ArrayList<>();
        if (draft.blanks() != null) {
            draft.blanks().forEach(blank -> answers.add(blank.answer()));
        }
        return request("FILL_IN_THE_BLANKS_TYPE_IN", withGaps(draft.transcript(), answers), draft.transcript(),
                jsonList(answers), null, null, null, cefr, pool);
    }

    public CreateQuestionRequest mapReTellLecture(ItemDraft.ListeningDraft draft, CefrLevel cefr, QuestionPool pool) {
        return request("RE_TELL_LECTURE", null, draft.transcript(), null, null, null, null, cefr, pool);
    }

    private CreateQuestionRequest choice(String taskType, ItemDraft.ListeningDraft draft, CefrLevel cefr,
                                         QuestionPool pool) {
        List<OptionRequest> options = new ArrayList<>();
        if (draft.options() != null) {
            for (int i = 0; i < draft.options().size(); i++) {
                boolean correct = draft.correctIndexes() != null && draft.correctIndexes().contains(i);
                options.add(new OptionRequest(draft.options().get(i), correct, i));
            }
        }
        String question = draft.question() == null || draft.question().isBlank() ? null : draft.question();
        return request(taskType, question, draft.transcript(), null, options, null, null, cefr, pool);
    }

    private static CreateQuestionRequest request(String taskType, String promptText, String reference,
                                                 String correctAnswer, List<OptionRequest> options, Integer minCount,
                                                 Integer maxCount, CefrLevel cefr, QuestionPool pool) {
        return new CreateQuestionRequest(taskType, taskType, promptText, null, null, reference, correctAnswer,
                minCount, maxCount, options, pool.name(), null, null, cefr.name());
    }

    /** The transcript with each answer replaced, in order, by its numbered gap; an answer not found is skipped. */
    static String withGaps(String transcript, List<String> answers) {
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        int gap = 0;
        for (String answer : answers) {
            if (answer == null || answer.isBlank()) {
                continue;
            }
            Matcher found = Pattern.compile("\\b" + Pattern.quote(answer.trim()) + "\\b",
                    Pattern.CASE_INSENSITIVE).matcher(transcript);
            if (found.find(cursor)) {
                out.append(transcript, cursor, found.start()).append(String.format(Locale.ROOT, GAP_FORMAT, gap));
                cursor = found.end();
                gap++;
            }
        }
        return out.append(transcript.substring(cursor)).toString();
    }

    /** A JSON array of strings, as {@code ReferenceAnswerScorer} reads the typed answers. */
    static String jsonList(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"').append(values.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return out.append(']').toString();
    }
}
