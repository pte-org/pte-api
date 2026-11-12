package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.media.RenderedMedia;

import java.util.Map;

/**
 * A draft ready for the question bank. {@code stimulusText} is the text the duplicate check hashes (passage,
 * transcript, ...); {@code topic} feeds the "avoid recent topics" prompt hint; {@code source} names the generator;
 * {@code draft} is the ItemDraft for review and metrics extraction; {@code mediaMeta} records the stored audio or
 * image (voice, speed, duration) once the media step has run.
 */
public record GeneratedItem(CreateQuestionRequest question, String stimulusText, String topic, String source,
                            ItemDraft draft, Map<String, Object> mediaMeta) {

    public GeneratedItem(CreateQuestionRequest question, String stimulusText, String topic, String source,
                         ItemDraft draft) {
        this(question, stimulusText, topic, source, draft, null);
    }

    public GeneratedItem(CreateQuestionRequest question, String stimulusText, String topic, String source) {
        this(question, stimulusText, topic, source, null, null);
    }

    /** The same item with the stored media attached to the question to be created. */
    public GeneratedItem withMedia(RenderedMedia media) {
        CreateQuestionRequest q = question;
        CreateQuestionRequest withRefs = new CreateQuestionRequest(q.pteTaskType(), q.taskTypeKey(), q.promptText(),
                media.audioPromptRef() != null ? media.audioPromptRef() : q.audioPromptRef(),
                media.imagePromptRef() != null ? media.imagePromptRef() : q.imagePromptRef(),
                q.referenceAnswerText(), q.correctAnswerText(), q.minWordCount(), q.maxWordCount(), q.options(),
                q.pool(), q.availableForPractice(), q.availableForExam(), q.cefrLevel(),
                q.answerHintVi(), q.answerHintEn());
        return new GeneratedItem(withRefs, stimulusText, topic, source, draft, media.meta());
    }
}
