package com.pte.itembank.internal.generation.draft;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.internal.generation.draft.audio.AudioDraftMapper;
import com.pte.itembank.internal.generation.draft.audio.DialogueDraftMapper;
import com.pte.itembank.internal.generation.draft.audio.HiwDraftMapper;
import com.pte.itembank.internal.generation.draft.image.ChartDraftMapper;
import com.pte.itembank.internal.generation.draft.text.TextDraftMapper;
import org.springframework.stereotype.Component;

/**
 * Facade mapper that routes ItemDraft to CreateQuestionRequest based on task type.
 * Delegates to specialized mappers: TextDraftMapper, AudioDraftMapper, HiwDraftMapper.
 */
@Component
public class DraftToQuestionMapper {

    private final TextDraftMapper textMapper;
    private final AudioDraftMapper audioMapper;
    private final HiwDraftMapper hiwMapper;
    private final DialogueDraftMapper dialogueMapper;
    private final ChartDraftMapper chartMapper;

    public DraftToQuestionMapper(TextDraftMapper textMapper, AudioDraftMapper audioMapper,
            HiwDraftMapper hiwMapper, DialogueDraftMapper dialogueMapper, ChartDraftMapper chartMapper) {
        this.textMapper = textMapper;
        this.audioMapper = audioMapper;
        this.hiwMapper = hiwMapper;
        this.dialogueMapper = dialogueMapper;
        this.chartMapper = chartMapper;
    }

    /**
     * Convert a draft to a CreateQuestionRequest. Caller provides CEFR, pool, and task type.
     */
    public CreateQuestionRequest map(ItemDraft draft, CefrLevel cefr, QuestionPool pool, String taskTypeKey) {
        return switch (taskTypeKey.toUpperCase()) {
            // Text types
            case "MC_READING_SINGLE" -> textMapper.mapChoiceSingle((ItemDraft.ChoiceDraft) draft, cefr, pool);
            case "MC_READING_MULTIPLE" -> textMapper.mapChoiceMultiple((ItemDraft.ChoiceDraft) draft, cefr, pool);
            case "FILL_IN_THE_BLANKS_DROPDOWN" -> textMapper.mapBlankFillDropdown((ItemDraft.BlankFillDraft) draft, cefr, pool);
            case "FILL_IN_THE_BLANKS_DRAG_AND_DROP" -> textMapper.mapBlankFillDragDrop((ItemDraft.BlankFillDraft) draft, cefr, pool);
            case "RE_ORDER_PARAGRAPHS" -> textMapper.mapReorder((ItemDraft.ReorderDraft) draft, cefr, pool);
            case "SUMMARIZE_WRITTEN_TEXT" -> textMapper.mapSummary((ItemDraft.PassageDraft) draft, cefr, pool);
            case "WRITE_ESSAY" -> textMapper.mapEssay((ItemDraft.PassageDraft) draft, cefr, pool);
            case "READ_ALOUD" -> textMapper.mapReadAloud((ItemDraft.PassageDraft) draft, cefr, pool);
            // Audio listening types
            case "WRITE_FROM_DICTATION" -> audioMapper.mapWriteFromDictation((ItemDraft.ListeningDraft) draft, cefr, pool);
            case "REPEAT_SENTENCE" -> audioMapper.mapRepeatSentence((ItemDraft.ListeningDraft) draft, cefr, pool);
            case "ANSWER_SHORT_QUESTION" -> audioMapper.mapAnswerShortQuestion((ItemDraft.ListeningDraft) draft, cefr, pool);
            case "SUMMARIZE_SPOKEN_TEXT" -> audioMapper.mapSummarizeSpokenText((ItemDraft.ListeningDraft) draft, cefr, pool);
            case "MC_LISTENING_SINGLE" -> audioMapper.mapMcListeningSingle((ItemDraft.ListeningDraft) draft, cefr, pool);
            case "MC_LISTENING_MULTIPLE" -> audioMapper.mapMcListeningMultiple((ItemDraft.ListeningDraft) draft, cefr, pool);
            case "HIGHLIGHT_CORRECT_SUMMARY" -> audioMapper.mapHighlightCorrectSummary((ItemDraft.ListeningDraft) draft, cefr, pool);
            case "SELECT_MISSING_WORD" -> audioMapper.mapSelectMissingWord((ItemDraft.ListeningDraft) draft, cefr, pool);
            case "FILL_IN_THE_BLANKS_TYPE_IN" -> audioMapper.mapListeningFillBlanks((ItemDraft.ListeningDraft) draft, cefr, pool);
            case "RE_TELL_LECTURE" -> audioMapper.mapReTellLecture((ItemDraft.ListeningDraft) draft, cefr, pool);
            // Audio HIW type
            case "HIGHLIGHT_INCORRECT_WORDS" -> hiwMapper.mapHighlightIncorrectWords((ItemDraft.HiwDraft) draft, cefr, pool);
            // Dialogue types (phase 6)
            case "SUMMARIZE_GROUP_DISCUSSION" -> dialogueMapper.mapSummarizeGroupDiscussion((ItemDraft.DialogueDraft) draft, cefr, pool);
            case "RESPOND_TO_A_SITUATION" -> dialogueMapper.mapRespondToASituation((ItemDraft.DialogueDraft) draft, cefr, pool);
            // Chart types (phase 7)
            case "DESCRIBE_IMAGE" -> chartMapper.mapDescribeImage((ItemDraft.ChartDraft) draft, cefr, pool);
            default -> throw new IllegalArgumentException("Unsupported task type: " + taskTypeKey);
        };
    }
}
