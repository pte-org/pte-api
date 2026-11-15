package com.pte.scoring.internal.vendor.audioscoring;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.audioscoring.AudioTask.AudioSource;
import com.pte.scoring.internal.vendor.audioscoring.AudioTask.ContentRule;
import com.pte.scoring.internal.vendor.gemini.GeminiImage;
import com.pte.scoring.internal.vendor.gemini.PromptImageLoader;
import com.pte.scoring.internal.vendor.speech.AnswerKey;
import com.pte.scoring.internal.vendor.speech.TokenAlignment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The seven speaking task types practice scores from the recording, and where each one's source comes from. */
final class AudioTasks {

    private static final String DELIMITER = "\"\"\"";
    private static final String EXAMINER = "You are a PTE Academic examiner scoring one practice answer to %s. %s";
    private static final String DESCRIPTION_SOURCE = "descriptionSource";

    private AudioTasks() {
    }

    /** The examiner role each task's prompt starts with. */
    static final Map<String, String> EXAMINERS = Map.of(
            ScoringConstants.TASK_TYPE_READ_ALOUD, examiner("Read Aloud",
                    "The candidate read the passage below aloud."),
            ScoringConstants.TASK_TYPE_REPEAT_SENTENCE, examiner("Repeat Sentence",
                    "The candidate heard the sentence below once and repeated it."),
            ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION, examiner("Answer Short Question",
                    "The candidate answered a short question in a word or a few words."),
            ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE, examiner("Describe Image",
                    "The candidate described the image in their own words."),
            ScoringConstants.TASK_TYPE_RE_TELL_LECTURE, examiner("Re-tell Lecture",
                    "The candidate heard the lecture below and retold it in their own words."),
            ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION, examiner("Respond to a Situation",
                    "The candidate responded aloud to the situation below."),
            ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION, examiner("Summarize Group Discussion",
                    "The candidate heard the discussion below and summarized it aloud."));

    static List<AudioTask> all(PromptImageLoader images) {
        return List.of(
                task(ScoringConstants.TASK_TYPE_READ_ALOUD, ContentRule.READ_ALOUD,
                        request -> scripted("Passage", request.promptText())),
                task(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE, ContentRule.REPEAT_SENTENCE,
                        request -> scripted("Sentence heard", request.referenceAnswerText())),
                task(ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION, ContentRule.SHORT_ANSWER, AudioTasks::shortAnswer),
                task(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE, ContentRule.MODEL,
                        request -> describeImage(images, request)),
                task(ScoringConstants.TASK_TYPE_RE_TELL_LECTURE, ContentRule.MODEL,
                        request -> source("Lecture transcript", request.referenceAnswerText())),
                task(ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION, ContentRule.MODEL,
                        request -> source("Situation", request.promptText())),
                task(ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION, ContentRule.MODEL,
                        request -> source("Discussion transcript", request.referenceAnswerText())));
    }

    private static AudioTask task(String taskType, ContentRule rule,
                                  java.util.function.Function<AiScoringRequest, AudioSource> source) {
        return new AudioTask(taskType, EXAMINERS.get(taskType), rule, source);
    }

    /** A labelled data block; a delimiter inside the text is defused so the text cannot end its own block. */
    static String quoted(String label, String text) {
        return label + ":\n" + DELIMITER + "\n" + text.strip().replace(DELIMITER, "'''") + "\n" + DELIMITER + "\n";
    }

    private static String examiner(String task, String situation) {
        return EXAMINER.formatted(task, situation);
    }

    private static AudioSource scripted(String label, String reference) {
        if (reference == null || TokenAlignment.normalize(reference).isEmpty()) {
            throw new AiProviderException(ScoringConstants.SPEECH_REFERENCE_TEXT_EMPTY);
        }
        return new AudioSource(quoted(label, reference), List.of(), reference, Map.of(), false);
    }

    private static AudioSource source(String label, String text) {
        if (text == null || text.isBlank()) {
            throw new AiProviderException(ScoringConstants.AI_SOURCE_TEXT_EMPTY);
        }
        return new AudioSource(quoted(label, text), List.of(), null, Map.of(), false);
    }

    private static AudioSource shortAnswer(AiScoringRequest request) {
        AnswerKey key = AnswerKey.parse(request.correctAnswerText());
        if (key.acceptedAnswers().isEmpty()) {
            throw new AiProviderException(ScoringConstants.ANSWER_KEY_EMPTY);
        }
        String question = request.promptText() == null || request.promptText().isBlank()
                ? "" : quoted("Question", request.promptText()) + "\n";
        return new AudioSource(question + quoted("Accepted answers", String.join("\n", key.acceptedAnswers())),
                List.of(), request.correctAnswerText(), Map.of("acceptedAnswers", key.acceptedAnswers()), false);
    }

    /** The picture itself when it loads; otherwise its reference description, scored with low confidence. */
    private static AudioSource describeImage(PromptImageLoader images, AiScoringRequest request) {
        Optional<GeminiImage> image = Optional.empty();
        String fallbackReason = ScoringConstants.DESCRIBE_IMAGE_NO_REFERENCE;
        if (request.imagePromptRef() != null) {
            try {
                image = Optional.of(images.load(request.imagePromptRef(), request.tenantId()));
            } catch (AiProviderException ex) {
                fallbackReason = ex.getMessage();
            }
        }
        if (image.isPresent()) {
            return new AudioSource("The image is attached.\n", List.of(image.get()), null,
                    Map.of(DESCRIPTION_SOURCE, "IMAGE"), false);
        }
        String reference = request.referenceAnswerText();
        if (reference == null || reference.isBlank()) {
            throw new AiProviderException(ScoringConstants.DESCRIBE_IMAGE_SOURCE_MISSING);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put(DESCRIPTION_SOURCE, "REFERENCE_TEXT");
        detail.put("fallbackReason", fallbackReason);
        detail.put("confidence", "LOW");
        return new AudioSource(quoted("Reference description of the image", reference), List.of(), null, detail,
                true);
    }
}
