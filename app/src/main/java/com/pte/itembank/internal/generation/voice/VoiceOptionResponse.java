package com.pte.itembank.internal.generation.voice;

/** One voice a person may pick. */
public record VoiceOptionResponse(String code, String accent, String gender, String label) { }
