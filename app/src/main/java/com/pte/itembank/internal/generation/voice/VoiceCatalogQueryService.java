package com.pte.itembank.internal.generation.voice;

import com.pte.itembank.internal.generation.constant.VoiceConstants;
import com.pte.itembank.internal.generation.media.EnabledVoices;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VoiceCatalogQueryService {

    private final EnabledVoices enabled;

    public VoiceCatalogQueryService(EnabledVoices enabled) {
        this.enabled = enabled;
    }

    public VoiceCatalogResponse listEnabled() {
        List<VoiceOptionResponse> voices = enabled.all().stream()
                .map(voice -> new VoiceOptionResponse(voice.code(), voice.accent().name(), voice.gender().name(),
                        voice.label()))
                .toList();
        return new VoiceCatalogResponse(voices, VoiceConstants.SUPPORTED_TASK_TYPES.stream().sorted().toList());
    }
}
