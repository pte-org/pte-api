package com.pte.itembank.internal.generation.live;

import com.pte.itembank.internal.generation.llm.LlmRole;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** What one item of a job looks like right now: its stage, the text each agent is writing and its media. */
final class LiveItem {

    private final int index;
    private final Map<LlmRole, StringBuilder> texts = new EnumMap<>(LlmRole.class);
    private final Map<LlmRole, Integer> attempts = new EnumMap<>(LlmRole.class);
    private String stage;
    private LlmRole activeAgent;
    private UUID audioRef;
    private UUID imageRef;
    private LiveEvent.Draft draft;
    private LiveEvent.Review review;

    LiveItem(int index) {
        this.index = index;
    }

    /** @return the number of the call that starts */
    int begin(LlmRole agent) {
        activeAgent = agent;
        texts.put(agent, new StringBuilder());
        return attempts.merge(agent, 1, Integer::sum);
    }

    void append(LlmRole agent, String delta) {
        texts.computeIfAbsent(agent, ignored -> new StringBuilder()).append(delta);
    }

    void stage(String newStage) {
        stage = newStage;
    }

    void media(UUID audio, UUID image) {
        audioRef = audio;
        imageRef = image;
    }

    void draft(LiveEvent.Draft latest) {
        draft = latest;
    }

    void review(LiveEvent.Review latest) {
        review = latest;
    }

    LiveEvent.ItemSnapshot snapshot() {
        return new LiveEvent.ItemSnapshot(index, stage, activeAgent == null ? null : activeAgent.name(),
                text(LlmRole.GENERATOR), text(LlmRole.REVIEWER), audioRef, imageRef, draft, review);
    }

    private LiveEvent.AgentText text(LlmRole agent) {
        StringBuilder text = texts.get(agent);
        return text == null ? null : new LiveEvent.AgentText(attempts.getOrDefault(agent, 0), text.toString());
    }
}
