package com.pte.itembank.internal.generation.live;

import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.llm.LlmRole;
import com.pte.itembank.internal.generation.llm.TokenSink;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Where the worker reports what it is doing to the live view: the text the agents write, the stage of an item, its
 * media and the end of an item or a job. Reporting never fails the pipeline: a watcher that went away is dropped.
 */
@Component
public class GenerationLiveFeed {

    private final LiveJobRegistry registry;

    public GenerationLiveFeed(LiveJobRegistry registry) {
        this.registry = registry;
    }

    /** Where an agent's text for one item goes while the model writes it. */
    public TokenSink sink(UUID jobPublicId, int index, LlmRole agent) {
        LiveJob job = registry.open(jobPublicId);
        return new TokenSink() {
            @Override
            public void begin() {
                job.begin(index, agent, registry.clock().instant());
            }

            @Override
            public void accept(String delta) {
                job.token(index, agent, delta, registry.clock().instant());
            }
        };
    }

    public void stage(UUID jobPublicId, int index, ItemStage stage) {
        registry.open(jobPublicId).stage(index, stage.name(), registry.clock().instant());
    }

    public void media(UUID jobPublicId, int index, UUID audioRef, UUID imageRef) {
        registry.open(jobPublicId).media(index, audioRef, imageRef, registry.clock().instant());
    }

    public void itemEnded(UUID jobPublicId, int index) {
        registry.open(jobPublicId).itemEnded(index, registry.clock().instant());
    }

    public void jobEnded(UUID jobPublicId) {
        registry.open(jobPublicId).finish(registry.clock().instant());
    }
}
