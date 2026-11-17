package com.pte.itembank.internal.generation.live;

import com.pte.itembank.internal.generation.constant.GenerationLiveConstants;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LiveItemCheckTest {

    private static LiveEvent.Check check(String step, String status) {
        return new LiveEvent.Check(0, step, status, Map.of(), java.util.List.of());
    }

    @Test
    void keepsTheLatestStatusOfEachCheckInTheOrderTheyStarted() {
        LiveItem item = new LiveItem(0);
        item.check(check(GenerationLiveConstants.CHECK_PROMPT, GenerationLiveConstants.CHECK_PASSED));
        item.check(check(GenerationLiveConstants.CHECK_WRITE, GenerationLiveConstants.CHECK_RUNNING));
        item.check(check(GenerationLiveConstants.CHECK_JSON, GenerationLiveConstants.CHECK_PASSED));
        item.check(check(GenerationLiveConstants.CHECK_WRITE, GenerationLiveConstants.CHECK_PASSED));

        assertThat(item.snapshot().checks()).extracting(LiveEvent.Check::step).containsExactly(
                GenerationLiveConstants.CHECK_PROMPT, GenerationLiveConstants.CHECK_WRITE,
                GenerationLiveConstants.CHECK_JSON);
        assertThat(item.snapshot().checks().get(1).status()).isEqualTo(GenerationLiveConstants.CHECK_PASSED);
    }

    @Test
    void aNewPromptStartsANewRound() {
        LiveItem item = new LiveItem(0);
        item.check(check(GenerationLiveConstants.CHECK_PROMPT, GenerationLiveConstants.CHECK_PASSED));
        item.check(check(GenerationLiveConstants.CHECK_RULES, GenerationLiveConstants.CHECK_FAILED));
        item.check(check(GenerationLiveConstants.CHECK_PROMPT, GenerationLiveConstants.CHECK_PASSED));

        assertThat(item.snapshot().checks()).extracting(LiveEvent.Check::step)
                .containsExactly(GenerationLiveConstants.CHECK_PROMPT);
    }

    @Test
    void aWatcherHearsEveryCheck() {
        LiveJob job = new LiveJob(java.time.Instant.EPOCH);
        LiveJobTest.RecordingEmitter watcher = new LiveJobTest.RecordingEmitter();
        job.attach(watcher);

        job.check(new LiveEvent.Check(0, GenerationLiveConstants.CHECK_RULES, GenerationLiveConstants.CHECK_FAILED,
                Map.of(GenerationLiveConstants.FACT_ISSUES, "Passage too short"), java.util.List.of()), java.time.Instant.EPOCH);

        assertThat(watcher.events.get(1)).contains("event:check").contains("Passage too short");
    }

    @Test
    void aNewRoundDropsTheMediaOfTheRoundBefore() {
        LiveItem item = new LiveItem(0);
        item.media(null, java.util.UUID.randomUUID());
        item.check(check(GenerationLiveConstants.CHECK_PROMPT, GenerationLiveConstants.CHECK_PASSED));

        assertThat(item.snapshot().imageRef()).isNull();
    }
}
