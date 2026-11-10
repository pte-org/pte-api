package com.pte.itembank.internal.generation.media;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Selects voice identifiers for audio generation, alternating accents within a job.
 * Provides a stable seed-based picker for test reproducibility.
 */
@Component
public class VoicePicker {

    private final List<String> usVoices;
    private final List<String> ukVoices;

    public VoicePicker(QuestionGenerationProperties properties) {
        String usVoicesCsv = properties.getKokoro().getVoicesUs();
        String ukVoicesCsv = properties.getKokoro().getVoicesUk();
        this.usVoices = parseVoices(usVoicesCsv);
        this.ukVoices = parseVoices(ukVoicesCsv);
    }

    /**
     * Pick a voice for the given item index within a job.
     * Alternates between US and UK accents; within each accent, rotates through available voices.
     *
     * @param itemIndex 0-based index of item in the job
     * @param seed job seed for reproducibility
     * @return voice identifier (e.g., "af_heart")
     */
    public String pick(int itemIndex, long seed) {
        Random random = new Random(seed + itemIndex);

        // Alternate US/UK by item index (even = US, odd = UK)
        List<String> voices = itemIndex % 2 == 0 ? usVoices : ukVoices;
        if (voices.isEmpty()) {
            // Fallback if config is empty
            return usVoices.isEmpty() ? "af_heart" : usVoices.get(0);
        }

        int voiceIndex = random.nextInt(voices.size());
        return voices.get(voiceIndex);
    }

    /**
     * Parse comma-separated voice list from config.
     */
    private List<String> parseVoices(String csv) {
        List<String> result = new ArrayList<>();
        if (csv != null && !csv.isBlank()) {
            for (String voice : csv.split(",")) {
                String trimmed = voice.trim();
                if (!trimmed.isBlank()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }
}
