package com.pte.scoring.internal.rubric.stability;

import com.pte.scoring.internal.constant.ScoringConstants;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The AI-generated synthetic dataset of the phase 4 stability spike, read from
 * {@code src/test/resources/scoring/rubric-spike/}. It is not candidate work and proves run-to-run stability only,
 * never scoring accuracy (see the README next to the files).
 */
public final class SyntheticDataset {

    public static final String FOLDER = "/scoring/rubric-spike/";
    public static final String WRITING_FILE = "writing-synthetic.json";
    public static final String SPEAKING_FILE = "speaking-transcripts-synthetic.json";
    public static final String TTS_MANIFEST_FILE = "tts-manifest.json";

    /** The task types of the writing samples (the rubric-scored writing tasks). */
    public static final Set<String> WRITING_TASK_TYPES = Set.of(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT,
            ScoringConstants.TASK_TYPE_WRITE_ESSAY, ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT);
    /** The task types of the speaking transcripts (the four speaking tasks whose Content an LLM scores). */
    public static final Set<String> SPEAKING_TASK_TYPES = Set.of(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE,
            ScoringConstants.TASK_TYPE_RE_TELL_LECTURE, ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION,
            ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SyntheticDataset() {
    }

    /** A prompt, passage, lecture, situation or discussion that samples answer. */
    public record Source(String taskType, String label, String text) {
    }

    public record WritingSample(String id, String taskType, String sourceId, String answer,
                                Map<String, Integer> intendedBands, String generationNotes) {
    }

    public record SpeakingSample(String id, String taskType, String sourceId, String transcript,
                                 Map<String, Integer> intendedBands, String generationNotes) {
    }

    public record Writing(int datasetVersion, String generatedBy, String purpose, Map<String, Source> sources,
                          List<WritingSample> samples) {

        public Source sourceOf(WritingSample sample) {
            return sources.get(sample.sourceId());
        }
    }

    public record Speaking(int datasetVersion, String generatedBy, String purpose, Map<String, Source> sources,
                           List<SpeakingSample> samples) {

        public Source sourceOf(SpeakingSample sample) {
            return sources.get(sample.sourceId());
        }
    }

    public record TtsEntry(String fileName, String basedOnSampleId, String variant, String text) {
    }

    public record TtsManifest(int manifestVersion, String purpose, Map<String, Integer> audioFormat,
                              String pauseMarker, List<TtsEntry> entries) {
    }

    public static Writing writing() {
        return read(WRITING_FILE, Writing.class);
    }

    public static Speaking speaking() {
        return read(SPEAKING_FILE, Speaking.class);
    }

    public static TtsManifest ttsManifest() {
        return read(TTS_MANIFEST_FILE, TtsManifest.class);
    }

    private static <T> T read(String file, Class<T> type) {
        try (InputStream in = SyntheticDataset.class.getResourceAsStream(FOLDER + file)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + FOLDER + file);
            }
            return JSON.readValue(in, type);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
