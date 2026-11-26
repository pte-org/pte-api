package com.pte.itembank.internal.generation.voice;

import java.util.List;

/** The voices that are switched on, and the task types whose voice can be changed. */
public record VoiceCatalogResponse(List<VoiceOptionResponse> voices, List<String> supportedTaskTypes) { }
