package com.pte.itembank.internal.generation.live;

import com.pte.itembank.dto.request.CreateQuestionRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The payloads of the live stream of a job; each record is the JSON data of one event. */
public final class LiveEvent {

    /** An agent's text of the current call and how many calls it made for the item so far. */
    public record AgentText(int attempt, String text) {
    }

    /** {@code activeAgent} is the agent that started last: the writer rewrites a rejected draft while reviewing. */
    public record ItemSnapshot(int index, String stage, String activeAgent, AgentText generator, AgentText reviewer,
                               UUID audioRef, UUID imageRef, Draft draft, Review review) {
    }

    /**
     * The generator's latest draft as the question form's fields (task type, prompt, answers, options, word limits),
     * so a page can show it in the form itself. {@code attempt} rises when a rejected draft is written again.
     */
    public record Draft(int index, int attempt, CreateQuestionRequest fields) {
    }

    /** What a reviewer said: {@code kind} is CONTENT (the text) or IMAGE (a drawn chart); notes are by check. */
    public record Review(int index, String kind, boolean passed, String fixSuggestion, Map<String, String> notes) {
    }

    public record Snapshot(List<ItemSnapshot> items, boolean finished) {
    }

    public record Begin(int index, String agent, int attempt) {
    }

    public record Token(int index, String agent, String delta) {
    }

    public record Stage(int index, String stage) {
    }

    public record Media(int index, UUID audioRef, UUID imageRef) {
    }

    public record Item(int index) {
    }

    public record End(boolean finished) {
    }

    private LiveEvent() {
    }
}
