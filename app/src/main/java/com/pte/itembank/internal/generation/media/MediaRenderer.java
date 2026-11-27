package com.pte.itembank.internal.generation.media;

import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.exception.GenerationMediaException;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.llm.LlmGateway;
import com.pte.itembank.internal.generation.llm.LlmProviderException;
import com.pte.itembank.internal.generation.prompt.PictureSettings;
import com.pte.itembank.internal.generation.prompt.PromptRegistry;
import com.pte.itembank.internal.generation.stop.GenerationStoppedException;
import com.pte.media.MediaService;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Turns an accepted draft into stored media: speech for the listening and speaking types, a drawn chart for Describe
 * Image. Runs only after the reviewer passed the text, so nothing is spoken or drawn for a draft that is rejected.
 */
@Component
public class MediaRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(MediaRenderer.class);
    private static final String AUDIO_WAV = "audio/wav";
    private static final String IMAGE_PNG = "image/png";
    private static final String AUDIO_PROMPT = "AUDIO_PROMPT";
    private static final String IMAGE_PROMPT = "IMAGE_PROMPT";
    private static final String NARRATOR = "N";
    private static final int VOICE_PICK_ATTEMPTS = 12;

    private final KokoroSpeechClient speech;
    private final VoicePicker voices;
    private final AudioDurationProbe probe;
    private final SpeechFitter fitter;
    private final DialogueSynthesizer dialogue;
    private final ChartRenderer charts;
    private final MediaService media;
    private final ObjectProvider<LlmGateway> painter;
    private final QuestionGenerationProperties properties;
    private final PromptRegistry prompts;

    public MediaRenderer(KokoroSpeechClient speech, VoicePicker voices, AudioDurationProbe probe,
                         DialogueSynthesizer dialogue, ChartRenderer charts, MediaService media,
                         ObjectProvider<LlmGateway> painter, QuestionGenerationProperties properties,
                         PromptRegistry prompts, SpeechFitter fitter) {
        this.fitter = fitter;
        this.prompts = prompts;
        this.painter = painter;
        this.properties = properties;
        this.speech = speech;
        this.voices = voices;
        this.probe = probe;
        this.dialogue = dialogue;
        this.charts = charts;
        this.media = media;
    }

    /**
     * The media for {@code draft}; empty (and no call made) for a text-only draft. {@code pool} is the pool of the
     * question being made, which picks the folder the files are stored in.
     */
    public Optional<RenderedMedia> render(ItemDraft draft, String taskTypeKey, int itemIndex, long seed,
                                          CurrentUser owner, QuestionPool pool, BatchPosition position) {
        String poolName = pool == null ? null : pool.name();
        try {
            return switch (draft) {
                case ItemDraft.ListeningDraft d -> Optional.of(speak(d.transcript(), taskTypeKey, itemIndex, seed, position,
                        owner, poolName));
                case ItemDraft.HiwDraft d -> Optional.of(speak(d.transcript(), taskTypeKey, itemIndex, seed, position,
                        owner, poolName));
                case ItemDraft.DialogueDraft d -> Optional.of(converse(d, taskTypeKey, itemIndex, seed, position,
                        owner, poolName));
                case ItemDraft.ChartDraft d -> Optional.of(draw(d, owner, poolName));
                case ItemDraft.PictureDraft d -> Optional.of(paint(d, owner, poolName));
                default -> Optional.empty();
            };
        } catch (GenerationMediaException | GenerationStoppedException passOn) {
            throw passOn;
        } catch (RuntimeException failure) {
            // Speech, drawing and storage all end here: one media code, and only the failure class is logged.
            LOG.warn("Media step failed: errorType={}", failure.getClass().getSimpleName());
            throw new GenerationMediaException(QuestionGenerationConstants.GENERATION_MEDIA_FAILED,
                    failure.getClass().getSimpleName());
        }
    }

    private RenderedMedia speak(String text, String taskTypeKey, int itemIndex, long seed, BatchPosition position,
                                CurrentUser owner, String pool) {
        String voice = voices.pick(itemIndex, seed, position);
        SpeechFitter.FittedSpeech fitted = fitter.fit(text, voice, taskTypeKey);
        byte[] audio = fitted.audio();
        double speed = fitted.speed();
        double seconds = fitted.seconds();
        UUID ref = media.storeGenerated(audio, AUDIO_WAV, AUDIO_PROMPT, pool, owner);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("audioPromptRef", ref.toString());
        meta.put("voice", voice);
        meta.put("speed", speed);
        meta.put("durationSec", Math.round(seconds * 10.0) / 10.0);
        return new RenderedMedia(ref, null, meta);
    }

    private RenderedMedia converse(ItemDraft.DialogueDraft draft, String taskTypeKey, int itemIndex, long seed,
                                   BatchPosition position, CurrentUser owner, String pool) {
        Map<String, String> voiceBySpeaker = distinctVoices(draft, itemIndex, seed, position);
        List<DialogueSynthesizer.DialogueTurn> clips = new ArrayList<>();
        if (hasSituation(draft)) {
            clips.add(new DialogueSynthesizer.DialogueTurn(NARRATOR,
                    speech.synthesize(draft.situation(), voiceBySpeaker.get(NARRATOR), 1.0)));
        }
        for (ItemDraft.DialogueDraft.Turn turn : draft.turns()) {
            clips.add(new DialogueSynthesizer.DialogueTurn(turn.speaker(),
                    speech.synthesize(turn.text(), voiceBySpeaker.get(turn.speaker()), 1.0)));
        }
        byte[] audio = dialogue.synthesize(clips);
        double seconds = probe.durationSeconds(audio);
        Optional<AudioDurationRules.Range> rule = AudioDurationRules.forTask(taskTypeKey);
        if (rule.isPresent() && !rule.get().contains(seconds)) {
            throw SpeechFitter.durationOutOfRange(seconds, rule.get());
        }
        UUID ref = media.storeGenerated(audio, AUDIO_WAV, AUDIO_PROMPT, pool, owner);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("audioPromptRef", ref.toString());
        meta.put("voices", voiceBySpeaker);
        meta.put("durationSec", Math.round(seconds * 10.0) / 10.0);
        return new RenderedMedia(ref, null, meta);
    }

    private static boolean hasSituation(ItemDraft.DialogueDraft draft) {
        return draft.situation() != null && !draft.situation().isBlank();
    }

    /** A different voice per speaker (and for the narrator), so the listener can tell them apart. */
    private Map<String, String> distinctVoices(ItemDraft.DialogueDraft draft, int itemIndex, long seed,
                                               BatchPosition position) {
        Set<String> speakers = new LinkedHashSet<>();
        if (hasSituation(draft)) {
            speakers.add(NARRATOR);
        }
        draft.turns().forEach(turn -> speakers.add(turn.speaker()));
        Map<String, String> assigned = new LinkedHashMap<>();
        int offset = 0;
        for (String speaker : speakers) {
            // Each speaker takes the next place of the batch's sequence, so the next one differs in accent and gender.
            String voice = voices.pick(itemIndex + offset, seed + offset, position);
            for (int tries = 0; assigned.containsValue(voice) && tries < VOICE_PICK_ATTEMPTS; tries++) {
                offset++;
                voice = voices.pick(itemIndex + offset, seed + offset, position);
            }
            assigned.put(speaker, voice);
            offset++;
        }
        return assigned;
    }

    private RenderedMedia draw(ItemDraft.ChartDraft draft, CurrentUser owner, String pool) {
        byte[] png = charts.render(draft);
        UUID ref = media.storeGenerated(png, IMAGE_PNG, IMAGE_PROMPT, pool, owner);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("imagePromptRef", ref.toString());
        meta.put("chartType", draft.chartType());
        // The bytes go along so the image reviewer can look at the same picture without reading it back.
        return new RenderedMedia(null, ref, meta, png);
    }

    /**
     * Paints the picture with the image model, trying again while attempts are left. When every attempt fails the
     * caller gets {@code GENERATION_IMAGE_FAILED}, which turns the question into a chart.
     */
    private RenderedMedia paint(ItemDraft.PictureDraft draft, CurrentUser owner, String pool) {
        LlmGateway gateway = painter.getIfAvailable();
        String lastFailure = "no image model";
        int attempts = pictureSettings().maxAttempts();
        for (int attempt = 1; gateway != null && attempt <= attempts; attempt++) {
            try {
                byte[] png = gateway.generateImage(draft.imagePrompt());
                UUID ref = media.storeGenerated(png, IMAGE_PNG, IMAGE_PROMPT, pool, owner);
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("imagePromptRef", ref.toString());
                meta.put("imageKind", "PICTURE");
                meta.put("imageModel", properties.getLlm().getImageModel());
                return new RenderedMedia(null, ref, meta, png);
            } catch (LlmProviderException failure) {
                lastFailure = failure.getType().code();
                LOG.info("Generation picture attempt {} failed: type={}", attempt, lastFailure);
            }
        }
        throw new GenerationMediaException(QuestionGenerationConstants.GENERATION_IMAGE_FAILED, lastFailure);
    }

    /** The picture settings in use: those on the picture prompt page, else the configured defaults. */
    public PictureSettings pictureSettings() {
        return prompts.pictureSettings(new PictureSettings(properties.getLlm().getImageMaxAttempts(), false));
    }

    /** Removes the stored media of a draft that will not be used; nothing when null. Never throws. */
    public void discard(RenderedMedia rendered, CurrentUser owner) {
        if (rendered != null) {
            discard(rendered.audioPromptRef(), rendered.imagePromptRef(), owner);
        }
    }

    /** Removes media that was stored for a question that then could not be created. Never throws. */
    public void discard(UUID audioPromptRef, UUID imagePromptRef, CurrentUser owner) {
        for (UUID ref : java.util.Arrays.asList(audioPromptRef, imagePromptRef)) {
            if (ref == null) {
                continue;
            }
            try {
                media.deleteGenerated(ref, owner);
            } catch (RuntimeException failure) {
                LOG.warn("Unused generated media was not removed: errorType={}", failure.getClass().getSimpleName());
            }
        }
    }
}
