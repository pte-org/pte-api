package com.pte.itembank.internal.generation.live;

import com.pte.itembank.internal.generation.llm.LlmRole;

import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** What one item of a job looks like right now: its stage, the text each agent is writing and its media. */
final class LiveItem {

    private final int index;
    private final Map<LlmRole, StringBuilder> texts = new EnumMap<>(LlmRole.class);
    private final Map<LlmRole, Integer> attempts = new EnumMap<>(LlmRole.class);
    /** The end of what each agent thought in its current call, and how much it thought in all. */
    private final Map<LlmRole, StringBuilder> thoughts = new EnumMap<>(LlmRole.class);
    private final Map<LlmRole, Integer> thoughtChars = new EnumMap<>(LlmRole.class);
    private String stage;
    private LlmRole activeAgent;
    private UUID audioRef;
    private UUID imageRef;
    private LiveEvent.Draft draft;
    private LiveEvent.Review review;
    /** The checks of the current round, by step, in the order they started. */
    private final Map<String, LiveEvent.Check> checks = new LinkedHashMap<>();

    LiveItem(int index) {
        this.index = index;
    }

    /** @return the number of the call that starts */
    int begin(LlmRole agent) {
        activeAgent = agent;
        texts.put(agent, new StringBuilder());
        thoughts.put(agent, new StringBuilder());
        thoughtChars.put(agent, 0);
        return attempts.merge(agent, 1, Integer::sum);
    }

    void append(LlmRole agent, String delta) {
        texts.computeIfAbsent(agent, ignored -> new StringBuilder()).append(delta);
    }

    void think(LlmRole agent, String delta) {
        StringBuilder thought = thoughts.computeIfAbsent(agent, ignored -> new StringBuilder()).append(delta);
        thoughtChars.merge(agent, delta.length(), Integer::sum);
        // Only the end is ever shown; trimmed in batches so a long train of thought stays cheap to keep.
        if (thought.length() > 2 * GenerationLiveConstants.THINKING_TAIL_CHARS) {
            thought.delete(0, thought.length() - GenerationLiveConstants.THINKING_TAIL_CHARS);
        }
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

    void check(LiveEvent.Check latest) {
        if (GenerationLiveConstants.CHECK_PROMPT.equals(latest.step())) {
            // A new round: the media of the last one was refused and removed, so it is no longer shown.
            checks.clear();
            audioRef = null;
            imageRef = null;
        }
        checks.put(latest.step(), latest);
    }

    LiveEvent.ItemSnapshot snapshot() {
        return new LiveEvent.ItemSnapshot(index, stage, activeAgent == null ? null : activeAgent.name(),
                text(LlmRole.GENERATOR), text(LlmRole.REVIEWER), audioRef, imageRef, draft, review,
                new ArrayList<>(checks.values()));
    }

    private LiveEvent.AgentText text(LlmRole agent) {
        StringBuilder text = texts.get(agent);
        if (text == null) {
            return null;
        }
        StringBuilder thought = thoughts.get(agent);
        String tail = thought == null ? "" : thought.substring(
                Math.max(0, thought.length() - GenerationLiveConstants.THINKING_TAIL_CHARS));
        return new LiveEvent.AgentText(attempts.getOrDefault(agent, 0), text.toString(), tail,
                thoughtChars.getOrDefault(agent, 0));
    }
}
