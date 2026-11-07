package com.pte.itembank.internal.generation.media;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.exception.GenerationMediaException;
import com.pte.media.MediaService;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final double SPEED_STEP = 0.1;
    private static final String NARRATOR = "N";
    private static final int VOICE_PICK_ATTEMPTS = 12;

    private final KokoroSpeechClient speech;
    private final VoicePicker voices;
    private final AudioDurationProbe probe;
    private final DialogueSynthesizer dialogue;
    private final ChartRenderer charts;
    private final MediaService media;

    public MediaRenderer(KokoroSpeechClient speech, VoicePicker voices, AudioDurationProbe probe,
                         DialogueSynthesizer dialogue, ChartRenderer charts, MediaService media) {
        this.speech = speech;
        this.voices = voices;
        this.probe = probe;
        this.dialogue = dialogue;
        this.charts = charts;
        this.media = media;
    }

    /** The media for {@code draft}; empty (and no call made) for a text-only draft. */
    public Optional<RenderedMedia> render(ItemDraft draft, String taskTypeKey, int itemIndex, long seed,
                                          CurrentUser owner) {
        try {
            return switch (draft) {
                case ItemDraft.ListeningDraft d -> Optional.of(speak(d.transcript(), taskTypeKey, itemIndex, seed, owner));
                case ItemDraft.HiwDraft d -> Optional.of(speak(d.transcript(), taskTypeKey, itemIndex, seed, owner));
                case ItemDraft.DialogueDraft d -> Optional.of(converse(d, taskTypeKey, itemIndex, seed, owner));
                case ItemDraft.ChartDraft d -> Optional.of(draw(d, owner));
                default -> Optional.empty();
            };
        } catch (GenerationMediaException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            // Speech, drawing and storage all end here: one media code, and only the failure class is logged.
            LOG.warn("Media step failed: errorType={}", failure.getClass().getSimpleName());
            throw new GenerationMediaException(QuestionGenerationConstants.GENERATION_MEDIA_FAILED,
                    failure.getClass().getSimpleName());
        }
    }

    private RenderedMedia speak(String text, String taskTypeKey, int itemIndex, long seed, CurrentUser owner) {
        String voice = voices.pick(itemIndex, seed);
        double speed = AudioDurationRules.baseSpeed(taskTypeKey);
        byte[] audio = speech.synthesize(text, voice, speed);
        Optional<AudioDurationRules.Range> rule = AudioDurationRules.forTask(taskTypeKey);
        double seconds = probe.durationSeconds(audio);
        if (rule.isPresent() && !rule.get().contains(seconds)) {
            // One more attempt at a speed that moves the length toward the range.
            speed = rule.get().isTooShort(seconds) ? speed - SPEED_STEP : speed + SPEED_STEP;
            audio = speech.synthesize(text, voice, speed);
            seconds = probe.durationSeconds(audio);
            if (!rule.get().contains(seconds)) {
                throw durationOutOfRange(seconds, rule.get());
            }
        }
        UUID ref = media.storeGenerated(audio, AUDIO_WAV, AUDIO_PROMPT, owner);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("audioPromptRef", ref.toString());
        meta.put("voice", voice);
        meta.put("speed", speed);
        meta.put("durationSec", Math.round(seconds * 10.0) / 10.0);
        return new RenderedMedia(ref, null, meta);
    }

    private RenderedMedia converse(ItemDraft.DialogueDraft draft, String taskTypeKey, int itemIndex, long seed,
                                   CurrentUser owner) {
        Map<String, String> voiceBySpeaker = distinctVoices(draft, itemIndex, seed);
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
            throw durationOutOfRange(seconds, rule.get());
        }
        UUID ref = media.storeGenerated(audio, AUDIO_WAV, AUDIO_PROMPT, owner);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("audioPromptRef", ref.toString());
        meta.put("voices", voiceBySpeaker);
        meta.put("durationSec", Math.round(seconds * 10.0) / 10.0);
        return new RenderedMedia(ref, null, meta);
    }

    private static GenerationMediaException durationOutOfRange(double seconds, AudioDurationRules.Range range) {
        return new GenerationMediaException(QuestionGenerationConstants.GENERATION_AUDIO_DURATION_OUT_OF_RANGE,
                "audio " + Math.round(seconds * 10.0) / 10.0 + "s, expected " + range.min() + "-" + range.max() + "s");
    }

    private static boolean hasSituation(ItemDraft.DialogueDraft draft) {
        return draft.situation() != null && !draft.situation().isBlank();
    }

    /** A different voice per speaker (and for the narrator), so the listener can tell them apart. */
    private Map<String, String> distinctVoices(ItemDraft.DialogueDraft draft, int itemIndex, long seed) {
        Set<String> speakers = new LinkedHashSet<>();
        if (hasSituation(draft)) {
            speakers.add(NARRATOR);
        }
        draft.turns().forEach(turn -> speakers.add(turn.speaker()));
        Map<String, String> assigned = new LinkedHashMap<>();
        int offset = 0;
        for (String speaker : speakers) {
            String voice = voices.pick(itemIndex + offset, seed + offset);
            for (int tries = 0; assigned.containsValue(voice) && tries < VOICE_PICK_ATTEMPTS; tries++) {
                offset++;
                voice = voices.pick(itemIndex + offset, seed + offset);
            }
            assigned.put(speaker, voice);
            offset++;
        }
        return assigned;
    }

    private RenderedMedia draw(ItemDraft.ChartDraft draft, CurrentUser owner) {
        byte[] png = charts.render(draft);
        UUID ref = media.storeGenerated(png, IMAGE_PNG, IMAGE_PROMPT, owner);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("imagePromptRef", ref.toString());
        meta.put("chartType", draft.chartType());
        return new RenderedMedia(null, ref, meta);
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
