package com.pte.itembank.internal.generation.validate;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.draft.image.PictureDrafts;
import com.pte.itembank.internal.generation.validate.audio.*;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates validation of question drafts against hardcoded rules. Splits by task-type group
 * using sub-delegates. Returns a list of issues (empty = valid).
 */
@Component
public class DraftValidator {

    private final ChoiceValidator choiceValidator;
    private final BlankFillValidator blankFillValidator;
    private final ReorderValidator reorderValidator;
    private final PassageValidator passageValidator;
    private final HiwValidator hiwValidator;
    private final ListenFibValidator listenFibValidator;
    private final DialogueValidator dialogueValidator;
    private final ChartDraftValidator chartValidator;

    public DraftValidator(
            ChoiceValidator choiceValidator,
            BlankFillValidator blankFillValidator,
            ReorderValidator reorderValidator,
            PassageValidator passageValidator,
            HiwValidator hiwValidator,
            ListenFibValidator listenFibValidator,
            DialogueValidator dialogueValidator,
            ChartDraftValidator chartValidator) {
        this.choiceValidator = choiceValidator;
        this.blankFillValidator = blankFillValidator;
        this.reorderValidator = reorderValidator;
        this.passageValidator = passageValidator;
        this.hiwValidator = hiwValidator;
        this.listenFibValidator = listenFibValidator;
        this.dialogueValidator = dialogueValidator;
        this.chartValidator = chartValidator;
    }

    /**
     * Validate a draft. Returns list of ValidationIssues (empty list = no issues).
     */
    public List<ValidationIssue> validate(ItemDraft draft, String taskTypeKey) {
        // Common validation for all drafts
        var issues = CommonDraftValidator.validateCommon(draft);
        if (!issues.isEmpty()) {
            return issues;
        }

        // Task-type-specific validation
        return switch (taskTypeKey.toUpperCase()) {
            // Text types
            case "MC_READING_SINGLE" -> choiceValidator.validateSingle((ItemDraft.ChoiceDraft) draft);
            case "MC_READING_MULTIPLE" -> choiceValidator.validateMultiple((ItemDraft.ChoiceDraft) draft);
            case "FILL_IN_THE_BLANKS_DROPDOWN" -> blankFillValidator.validateDropdown((ItemDraft.BlankFillDraft) draft);
            case "FILL_IN_THE_BLANKS_DRAG_AND_DROP" -> blankFillValidator.validateDragDrop((ItemDraft.BlankFillDraft) draft);
            case "RE_ORDER_PARAGRAPHS" -> reorderValidator.validate((ItemDraft.ReorderDraft) draft);
            case "SUMMARIZE_WRITTEN_TEXT" -> passageValidator.validateSummary((ItemDraft.PassageDraft) draft);
            case "WRITE_ESSAY" -> passageValidator.validateEssay((ItemDraft.PassageDraft) draft);
            case "READ_ALOUD" -> passageValidator.validateReadAloud((ItemDraft.PassageDraft) draft);
            // Audio types
            case "WRITE_FROM_DICTATION" -> new AudioTypeValidators.WfdValidator().validate(draft);
            case "REPEAT_SENTENCE" -> new AudioTypeValidators.RsValidator().validate(draft);
            case "ANSWER_SHORT_QUESTION" -> new AudioTypeValidators.AsqValidator().validate(draft);
            case "SUMMARIZE_SPOKEN_TEXT" -> new AudioTypeValidators.SstValidator().validate(draft);
            case "MC_LISTENING_SINGLE" -> new AudioTypeValidators.HcsValidator().validate(draft); // reuse for MC_L
            case "MC_LISTENING_MULTIPLE" -> new AudioTypeValidators.HcsValidator().validate(draft);
            case "HIGHLIGHT_CORRECT_SUMMARY" -> new AudioTypeValidators.HcsValidator().validate(draft);
            case "SELECT_MISSING_WORD" -> new AudioTypeValidators.SmwValidator().validate(draft);
            case "HIGHLIGHT_INCORRECT_WORDS" -> hiwValidator.validate(draft);
            case "FILL_IN_THE_BLANKS_TYPE_IN" -> listenFibValidator.validate(draft);
            case "RE_TELL_LECTURE" -> new AudioTypeValidators.RlValidator().validate(draft);
            case "SUMMARIZE_GROUP_DISCUSSION" -> dialogueValidator.validateSgd((ItemDraft.DialogueDraft) draft);
            case "RESPOND_TO_A_SITUATION" -> dialogueValidator.validateRts((ItemDraft.DialogueDraft) draft);
            case "DESCRIBE_IMAGE" -> chartValidator.validate((ItemDraft.ChartDraft) draft);
            case QuestionGenerationConstants.PROMPT_KEY_DESCRIBE_IMAGE_PICTURE ->
                    PictureDrafts.validate((ItemDraft.PictureDraft) draft);
            default -> List.of();
        };
    }
}
