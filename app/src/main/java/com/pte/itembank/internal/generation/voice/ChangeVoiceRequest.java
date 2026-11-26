package com.pte.itembank.internal.generation.voice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The voice to speak with, and the version of the question the person was looking at. */
public record ChangeVoiceRequest(@NotBlank String voice, @NotNull Long version) { }
