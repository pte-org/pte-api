package com.pte.itembank.internal.generation.voice;

import com.pte.itembank.internal.generation.constant.VoiceConstants;
import com.pte.itembank.internal.generation.media.SpeechFitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Optional;
import java.util.UUID;

/** Reads and rewrites the {@code media} part of a question's generation_meta, leaving every other key as it was. */
final class GenerationMediaMeta {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private GenerationMediaMeta() { }

    /** The single voice that spoke the question; empty for a dialogue, a question without audio or unreadable meta. */
    static Optional<String> currentVoice(String generationMeta) {
        if (generationMeta == null || generationMeta.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode voice = MAPPER.readTree(generationMeta).path(VoiceConstants.META_MEDIA)
                    .path(VoiceConstants.META_VOICE);
            return voice.isString() && !voice.asString().isBlank() ? Optional.of(voice.asString()) : Optional.empty();
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    static String withVoice(String generationMeta, UUID audioRef, String voice, SpeechFitter.FittedSpeech speech) {
        ObjectNode root = (ObjectNode) MAPPER.readTree(generationMeta);
        ObjectNode media = root.has(VoiceConstants.META_MEDIA) && root.get(VoiceConstants.META_MEDIA).isObject()
                ? (ObjectNode) root.get(VoiceConstants.META_MEDIA) : root.putObject(VoiceConstants.META_MEDIA);
        media.put(VoiceConstants.META_AUDIO_PROMPT_REF, audioRef.toString());
        media.put(VoiceConstants.META_VOICE, voice);
        media.put(VoiceConstants.META_SPEED, speech.speed());
        media.put(VoiceConstants.META_DURATION_SEC,
                Math.round(speech.seconds() * VoiceConstants.SECONDS_ROUNDING) / VoiceConstants.SECONDS_ROUNDING);
        return MAPPER.writeValueAsString(root);
    }
}
