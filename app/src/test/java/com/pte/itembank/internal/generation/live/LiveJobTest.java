package com.pte.itembank.internal.generation.live;

import com.pte.itembank.internal.generation.llm.LlmRole;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LiveJobTest {

    private static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");

    /** Keeps the text of every event it was sent, instead of writing to a response. */
    static final class RecordingEmitter extends SseEmitter {
        final List<String> events = new ArrayList<>();
        boolean completed;
        boolean broken;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (broken) {
                throw new IOException("client went away");
            }
            events.add(builder.build().stream().map(part -> String.valueOf(part.getData()))
                    .collect(Collectors.joining()));
        }

        @Override
        public void complete() {
            completed = true;
        }

        boolean saw(String text) {
            return events.stream().anyMatch(event -> event.contains(text));
        }
    }

    @Test
    void aWatcherGetsTheSnapshotFirstAndThenEveryLaterEvent() {
        LiveJob job = new LiveJob(T0);
        RecordingEmitter watcher = new RecordingEmitter();

        job.attach(watcher);
        job.begin(0, LlmRole.GENERATOR, T0);
        job.token(0, LlmRole.GENERATOR, "Bees", T0);

        assertThat(watcher.events).hasSize(3);
        assertThat(watcher.events.get(0)).contains("event:snapshot");
        assertThat(watcher.events.get(1)).contains("event:begin").contains("attempt=1");
        assertThat(watcher.events.get(2)).contains("event:token").contains("Bees");
    }

    @Test
    void aLateWatcherCatchesUpWithTheTextWrittenSoFar() {
        LiveJob job = new LiveJob(T0);
        job.stage(0, "GENERATING", T0);
        job.begin(0, LlmRole.GENERATOR, T0);
        job.token(0, LlmRole.GENERATOR, "The city ", T0);
        job.token(0, LlmRole.GENERATOR, "council", T0);
        RecordingEmitter late = new RecordingEmitter();

        job.attach(late);

        assertThat(late.events).hasSize(1);
        assertThat(late.events.get(0)).contains("The city council").contains("GENERATING");
    }

    @Test
    void aNewCallStartsTheAgentTextAgainAndCountsTheAttempt() {
        LiveJob job = new LiveJob(T0);
        job.begin(0, LlmRole.REVIEWER, T0);
        job.token(0, LlmRole.REVIEWER, "FAIL", T0);
        job.begin(0, LlmRole.REVIEWER, T0);
        job.token(0, LlmRole.REVIEWER, "PASS", T0);
        RecordingEmitter late = new RecordingEmitter();

        job.attach(late);

        assertThat(late.events.get(0)).contains("attempt=2, text=PASS").doesNotContain("FAIL");
    }

    @Test
    void theEndClosesEveryStreamAndALaterWatcherIsToldAtOnce() {
        LiveJob job = new LiveJob(T0);
        RecordingEmitter watcher = new RecordingEmitter();
        job.attach(watcher);

        job.finish(T0);
        RecordingEmitter late = new RecordingEmitter();
        job.attach(late);

        assertThat(watcher.saw("event:end")).isTrue();
        assertThat(watcher.completed).isTrue();
        assertThat(late.saw("event:snapshot")).isTrue();
        assertThat(late.saw("event:end")).isTrue();
        assertThat(late.completed).isTrue();
    }

    @Test
    void aJobThatResumesAfterAnAbortedRunIsWatchedAgain() {
        LiveJob job = new LiveJob(T0);
        job.finish(T0);

        job.stage(1, "GENERATING", T0);
        RecordingEmitter watcher = new RecordingEmitter();
        job.attach(watcher);

        assertThat(watcher.completed).isFalse();
        assertThat(watcher.saw("event:end")).isFalse();
    }

    @Test
    void aWatcherThatWentAwayIsDroppedWithoutStoppingTheOthers() {
        LiveJob job = new LiveJob(T0);
        RecordingEmitter gone = new RecordingEmitter();
        RecordingEmitter staying = new RecordingEmitter();
        job.attach(gone);
        job.attach(staying);
        gone.broken = true;

        job.token(0, LlmRole.GENERATOR, "a", T0);
        gone.broken = false;
        job.token(0, LlmRole.GENERATOR, "b", T0);

        assertThat(staying.events).hasSize(3);
        assertThat(gone.events).hasSize(1);
    }

    @Test
    void aFinishedJobIsKeptForLateWatchersOnlyForAWhile() {
        LiveJob job = new LiveJob(T0);
        job.finish(T0);

        assertThat(job.isExpired(T0.plus(Duration.ofMinutes(5)))).isFalse();
        assertThat(job.isExpired(T0.plus(Duration.ofMinutes(11)))).isTrue();
    }

    @Test
    void aWatchedJobIsNeverDroppedWhileItRuns() {
        LiveJob job = new LiveJob(T0);
        job.attach(new RecordingEmitter());

        assertThat(job.isExpired(T0.plus(Duration.ofHours(2)))).isFalse();
    }
}
