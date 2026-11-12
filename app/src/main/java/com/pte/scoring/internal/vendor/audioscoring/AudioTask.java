package com.pte.scoring.internal.vendor.audioscoring;

import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiImage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * How one speaking task type is scored from its recording: the examiner role the model takes, how the item's
 * source is read (before any audio is downloaded) and which rule gives the traits the model does not judge.
 */
record AudioTask(String taskType, String examiner, ContentRule contentRule,
                 Function<AiScoringRequest, AudioSource> source) {

    /** Where the traits the model does not judge come from. */
    enum ContentRule {
        /** Every trait is judged by the model. */
        MODEL,
        /** Content = passage words minus replaced, omitted and inserted words, counted from the transcript. */
        READ_ALOUD,
        /** Content 0-3 from the words repeated in sequence, counted from the transcript. */
        REPEAT_SENTENCE,
        /** Vocabulary 1 when the whole answer is an accepted answer, else 0. */
        SHORT_ANSWER
    }

    /**
     * What the model is given besides the recording.
     *
     * @param blocks        the quoted source texts of the user message
     * @param images        the pictures (Describe Image), sent before the recording
     * @param reference     the text the counted traits are measured against (passage, sentence or answer key)
     * @param detail        what is known about the source, kept in the stored detail
     * @param lowConfidence the item is scored without its full source (a picture that could not be loaded)
     */
    record AudioSource(String blocks, List<GeminiImage> images, String reference, Map<String, Object> detail,
                       boolean lowConfidence) {

        AudioSource {
            images = images == null ? List.of() : List.copyOf(images);
            detail = detail == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
        }
    }
}
