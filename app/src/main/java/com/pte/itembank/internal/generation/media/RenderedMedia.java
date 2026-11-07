package com.pte.itembank.internal.generation.media;

import java.util.Map;
import java.util.UUID;

/** The stored media of a draft: the references to put on the question and what to record in generation_meta. */
public record RenderedMedia(UUID audioPromptRef, UUID imagePromptRef, Map<String, Object> meta) {
}
