package com.pte.reporting.internal.service;

import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.internal.config.TaskSkillMappingConfig;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ScoredAnswerView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreAggregationServiceTest {

    @Mock
    private ScoringService scoringService;

    @Mock
    private TaskSkillMappingConfig taskSkillMappingConfig;

    private ScoreAggregationService service;

    @BeforeEach
    void setUp() {
        service = new ScoreAggregationService(scoringService, taskSkillMappingConfig);
    }

    @Test
    void aggregate_singleAnswerPerSkill_scaledScore10For0Percent() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoredAnswerView answer = new ScoredAnswerView("READING_TASK", 0);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(answer));
        when(taskSkillMappingConfig.skillsFor("READING_TASK"))
                .thenReturn(Set.of(Skill.READING));

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        SkillScore readingScore = summary.skillScores().get(Skill.READING);
        assertThat(readingScore.score()).isEqualTo(10);
        assertThat(readingScore.sufficientData()).isTrue();
    }

    @Test
    void aggregate_singleAnswerPerSkill_scaledScore90For100Percent() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoredAnswerView answer = new ScoredAnswerView("READING_TASK", 100);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(answer));
        when(taskSkillMappingConfig.skillsFor("READING_TASK"))
                .thenReturn(Set.of(Skill.READING));

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        SkillScore readingScore = summary.skillScores().get(Skill.READING);
        assertThat(readingScore.score()).isEqualTo(90);
        assertThat(readingScore.sufficientData()).isTrue();
    }

    @Test
    void aggregate_singleAnswerPerSkill_scaledScore50For50Percent() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoredAnswerView answer = new ScoredAnswerView("READING_TASK", 50);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(answer));
        when(taskSkillMappingConfig.skillsFor("READING_TASK"))
                .thenReturn(Set.of(Skill.READING));

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        SkillScore readingScore = summary.skillScores().get(Skill.READING);
        assertThat(readingScore.score()).isEqualTo(50);
        assertThat(readingScore.sufficientData()).isTrue();
    }

    @Test
    void aggregate_skillWithNoContributingAnswers_insufficientData() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoredAnswerView answer = new ScoredAnswerView("READING_TASK", 50);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(answer));
        when(taskSkillMappingConfig.skillsFor("READING_TASK"))
                .thenReturn(Set.of(Skill.READING));

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        // LISTENING has no contributing answers
        SkillScore listeningScore = summary.skillScores().get(Skill.LISTENING);
        assertThat(listeningScore.sufficientData()).isFalse();
        assertThat(listeningScore.score()).isNull();
    }

    @Test
    void aggregate_multipleAnswersPerSkill_averagesCorrectly() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoredAnswerView answer1 = new ScoredAnswerView("READING_TASK", 60);
        ScoredAnswerView answer2 = new ScoredAnswerView("READING_TASK", 80);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(answer1, answer2));
        when(taskSkillMappingConfig.skillsFor("READING_TASK"))
                .thenReturn(Set.of(Skill.READING));

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        // Average: (60 + 80) / 2 = 70, scaled: 10 + 70 * 0.8 = 10 + 56 = 66
        SkillScore readingScore = summary.skillScores().get(Skill.READING);
        assertThat(readingScore.score()).isEqualTo(66);
    }

    @Test
    void aggregate_roundingBehavior_mathRoundUsed() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // 33 => 10 + 33 * 0.8 = 10 + 26.4 = 36.4, rounds to 36
        ScoredAnswerView answer1 = new ScoredAnswerView("READING_TASK", 33);
        ScoredAnswerView answer2 = new ScoredAnswerView("READING_TASK", 33);
        ScoredAnswerView answer3 = new ScoredAnswerView("READING_TASK", 33);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(answer1, answer2, answer3));
        when(taskSkillMappingConfig.skillsFor("READING_TASK"))
                .thenReturn(Set.of(Skill.READING));

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        // Average: 33, scaled: 10 + 33 * 0.8 = 10 + 26.4 = 36.4, rounds to 36
        SkillScore readingScore = summary.skillScores().get(Skill.READING);
        assertThat(readingScore.score()).isEqualTo(36);
    }

    @Test
    void aggregate_answerContributingToMultipleSkills_countsForEach() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // READ_ALOUD contributes to READING and SPEAKING
        ScoredAnswerView answer = new ScoredAnswerView("READ_ALOUD", 70);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(answer));
        when(taskSkillMappingConfig.skillsFor("READ_ALOUD"))
                .thenReturn(Set.of(Skill.READING, Skill.SPEAKING));

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        // Both READING and SPEAKING should have the score
        SkillScore readingScore = summary.skillScores().get(Skill.READING);
        SkillScore speakingScore = summary.skillScores().get(Skill.SPEAKING);

        assertThat(readingScore.score()).isEqualTo(66); // 10 + 70 * 0.8 = 66
        assertThat(speakingScore.score()).isEqualTo(66);
    }

    @Test
    void aggregate_enablingSkillsDoNotAffectOverall() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Only enable an enabling task (contributes to GRAMMAR only, which is enabling)
        // Mock skillsFor to return only an enabling skill
        ScoredAnswerView answer = new ScoredAnswerView("SOME_TASK", 80);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(answer));
        when(taskSkillMappingConfig.skillsFor("SOME_TASK"))
                .thenReturn(Set.of(Skill.GRAMMAR)); // Only enabling skill

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        // GRAMMAR is enabling, should not affect overall
        assertThat(summary.overall().sufficientData()).isFalse();
    }

    @Test
    void aggregate_overallAveragesCommunicativeSkillsOnly() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Reading: 80 => scaled 74
        // Listening: 60 => scaled 58
        // Grammar: 40 => scaled 42 (enabling only, should not affect overall)
        ScoredAnswerView readingAnswer = new ScoredAnswerView("MC_READING_SINGLE", 80);
        ScoredAnswerView listeningAnswer = new ScoredAnswerView("MC_LISTENING_SINGLE", 60);
        ScoredAnswerView grammarAnswer = new ScoredAnswerView("GRAMMAR_TASK", 40);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(readingAnswer, listeningAnswer, grammarAnswer));
        when(taskSkillMappingConfig.skillsFor("MC_READING_SINGLE"))
                .thenReturn(Set.of(Skill.READING));
        when(taskSkillMappingConfig.skillsFor("MC_LISTENING_SINGLE"))
                .thenReturn(Set.of(Skill.LISTENING));
        when(taskSkillMappingConfig.skillsFor("GRAMMAR_TASK"))
                .thenReturn(Set.of(Skill.GRAMMAR)); // Only enabling skill

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        // Overall should be average of READING (74) and LISTENING (58) only
        // (74 + 58) / 2 = 66
        assertThat(summary.overall().score()).isEqualTo(66);
    }

    @Test
    void aggregate_allCommunicativeSkillsHaveInsufficientData_overallInsufficientData() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Only enabling skills - no communicative skills
        ScoredAnswerView answer = new ScoredAnswerView("TASK_WITH_ONLY_ENABLING", 80);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(answer));
        when(taskSkillMappingConfig.skillsFor("TASK_WITH_ONLY_ENABLING"))
                .thenReturn(Set.of(Skill.GRAMMAR, Skill.VOCABULARY, Skill.SPELLING, Skill.WRITTEN_DISCOURSE, Skill.ORAL_FLUENCY, Skill.PRONUNCIATION));

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        // Overall should be insufficient data because no communicative skills have data
        assertThat(summary.overall().sufficientData()).isFalse();
    }

    @Test
    void aggregate_emptyAnswerList_allSkillsInsufficientData() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of());

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        // All skills should have insufficient data
        for (Skill skill : Skill.values()) {
            assertThat(summary.skillScores().get(skill).sufficientData()).isFalse();
        }
        assertThat(summary.overall().sufficientData()).isFalse();
    }

    @Test
    void aggregate_fourCommunicativeSkillsWithData_overallAveragesFour() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Listening, Reading, Speaking, Writing with scores 60, 70, 80, 90
        ScoredAnswerView listening = new ScoredAnswerView("MC_LISTENING_SINGLE", 60);
        ScoredAnswerView reading = new ScoredAnswerView("MC_READING_SINGLE", 70);
        ScoredAnswerView speaking = new ScoredAnswerView("DESCRIBE_IMAGE", 80);
        ScoredAnswerView writing = new ScoredAnswerView("WRITE_ESSAY", 90);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(listening, reading, speaking, writing));
        when(taskSkillMappingConfig.skillsFor("MC_LISTENING_SINGLE"))
                .thenReturn(Set.of(Skill.LISTENING));
        when(taskSkillMappingConfig.skillsFor("MC_READING_SINGLE"))
                .thenReturn(Set.of(Skill.READING));
        when(taskSkillMappingConfig.skillsFor("DESCRIBE_IMAGE"))
                .thenReturn(Set.of(Skill.SPEAKING, Skill.ORAL_FLUENCY, Skill.PRONUNCIATION));
        when(taskSkillMappingConfig.skillsFor("WRITE_ESSAY"))
                .thenReturn(Set.of(Skill.WRITING, Skill.GRAMMAR, Skill.VOCABULARY, Skill.SPELLING, Skill.WRITTEN_DISCOURSE));

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        // Listening: 10 + 60 * 0.8 = 58
        // Reading: 10 + 70 * 0.8 = 66
        // Speaking: 10 + 80 * 0.8 = 74
        // Writing: 10 + 90 * 0.8 = 82
        // Overall: (58 + 66 + 74 + 82) / 4 = 280 / 4 = 70
        assertThat(summary.overall().score()).isEqualTo(70);
    }

    @Test
    void aggregate_communicativeAndEnablingSkillsMixed() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Only provide LISTENING communicative and GRAMMAR enabling
        ScoredAnswerView answer = new ScoredAnswerView("SUMMARIZE_SPOKEN_TEXT", 80);

        when(scoringService.getScoredAnswersForAttempt(attemptPublicId, tenantId))
                .thenReturn(List.of(answer));
        when(taskSkillMappingConfig.skillsFor("SUMMARIZE_SPOKEN_TEXT"))
                .thenReturn(Set.of(Skill.LISTENING, Skill.WRITING, Skill.GRAMMAR, Skill.VOCABULARY, Skill.SPELLING, Skill.WRITTEN_DISCOURSE));

        AttemptScoreSummary summary = service.aggregate(attemptPublicId, tenantId);

        // LISTENING is communicative and has data => score 74
        SkillScore listeningScore = summary.skillScores().get(Skill.LISTENING);
        assertThat(listeningScore.sufficientData()).isTrue();
        assertThat(listeningScore.score()).isEqualTo(74);

        // GRAMMAR is enabling but should not affect overall
        // WRITING is communicative and has data => score 74
        SkillScore writingScore = summary.skillScores().get(Skill.WRITING);
        assertThat(writingScore.sufficientData()).isTrue();

        // Overall should be average of LISTENING and WRITING (both 74) = 74
        assertThat(summary.overall().score()).isEqualTo(74);
    }
}
