package com.pte.scoring.internal.messaging.job;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiScoringJobTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void jobRoundTripsThroughJson() {
        AiScoringJob job = new AiScoringJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "AI_SPEECH", 3L);

        AiScoringJob read = jsonMapper.readValue(jsonMapper.writeValueAsString(job), AiScoringJob.class);

        assertThat(read).isEqualTo(job);
    }

    @Test
    void messageQueuedBeforeTheUpgradeReadsWithoutAToken() {
        UUID answer = UUID.randomUUID();
        String legacy = """
                {"answerPublicId":"%s","attemptPublicId":"%s","sessionPublicId":"%s","tenantId":"%s",
                 "taskType":"READ_ALOUD","payload":"https://media/answer.wav","referenceText":"reference",
                 "scoringMethod":"AI_SPEECH"}
                """.formatted(answer, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        AiScoringJob read = jsonMapper.readValue(legacy, AiScoringJob.class);

        assertThat(read.answerPublicId()).isEqualTo(answer);
        assertThat(read.scoringMethod()).isEqualTo("AI_SPEECH");
        assertThat(read.dispatchToken()).isNull();
    }
}
