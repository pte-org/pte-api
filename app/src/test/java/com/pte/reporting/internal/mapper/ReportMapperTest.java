package com.pte.reporting.internal.mapper;

import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.internal.dto.response.ReportResponse;
import com.pte.reporting.internal.dto.response.SkillScoreResponse;
import com.pte.reporting.internal.service.AttemptScoreSummary;
import com.pte.reporting.internal.service.SkillScore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMapperTest {

    @Test
    void toResponse_splitsCommunicativeAndEnablingSkills() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        Instant publishedAt = Instant.now();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setPublished(true);
        report.setPublishedAt(publishedAt);

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        skillScores.put(Skill.LISTENING, SkillScore.of(50));
        skillScores.put(Skill.READING, SkillScore.of(60));
        skillScores.put(Skill.SPEAKING, SkillScore.of(70));
        skillScores.put(Skill.WRITING, SkillScore.of(80));
        skillScores.put(Skill.GRAMMAR, SkillScore.of(55));
        skillScores.put(Skill.ORAL_FLUENCY, SkillScore.of(65));
        skillScores.put(Skill.PRONUNCIATION, SkillScore.of(75));
        skillScores.put(Skill.SPELLING, SkillScore.insufficientData());
        skillScores.put(Skill.VOCABULARY, SkillScore.of(85));
        skillScores.put(Skill.WRITTEN_DISCOURSE, SkillScore.of(45));

        SkillScore overall = SkillScore.of(65);
        AttemptScoreSummary summary = new AttemptScoreSummary(overall, skillScores);

        ReportResponse response = ReportMapper.toResponse(report, summary);

        // Verify communicative skills (4 total)
        assertThat(response.communicativeSkills()).hasSize(4);
        assertThat(response.communicativeSkills())
                .extracting(SkillScoreResponse::skill)
                .containsExactlyInAnyOrder("LISTENING", "READING", "SPEAKING", "WRITING");

        // Verify enabling skills (6 total)
        assertThat(response.enablingSkills()).hasSize(6);
        assertThat(response.enablingSkills())
                .extracting(SkillScoreResponse::skill)
                .containsExactlyInAnyOrder("GRAMMAR", "ORAL_FLUENCY", "PRONUNCIATION", "SPELLING", "VOCABULARY", "WRITTEN_DISCOURSE");
    }

    @Test
    void toResponse_carriesOverallScore() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setPublished(true);

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            skillScores.put(skill, SkillScore.insufficientData());
        }

        SkillScore overall = SkillScore.of(75);
        AttemptScoreSummary summary = new AttemptScoreSummary(overall, skillScores);

        ReportResponse response = ReportMapper.toResponse(report, summary);

        assertThat(response.overall().score()).isEqualTo(75);
        assertThat(response.overall().sufficientData()).isTrue();
    }

    @Test
    void toResponse_carriesInsufficientDataForOverall() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setPublished(false);

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            skillScores.put(skill, SkillScore.insufficientData());
        }

        SkillScore overall = SkillScore.insufficientData();
        AttemptScoreSummary summary = new AttemptScoreSummary(overall, skillScores);

        ReportResponse response = ReportMapper.toResponse(report, summary);

        assertThat(response.overall().score()).isNull();
        assertThat(response.overall().sufficientData()).isFalse();
    }

    @Test
    void toResponse_carriesPublishedAndPublishedAtFromReport() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        Instant publishedAt = Instant.now();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setPublished(true);
        report.setPublishedAt(publishedAt);

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            skillScores.put(skill, SkillScore.insufficientData());
        }

        AttemptScoreSummary summary = new AttemptScoreSummary(SkillScore.insufficientData(), skillScores);

        ReportResponse response = ReportMapper.toResponse(report, summary);

        assertThat(response.published()).isTrue();
        assertThat(response.publishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void toResponse_carriesAttemptAndSessionIds() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setPublished(false);

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            skillScores.put(skill, SkillScore.insufficientData());
        }

        AttemptScoreSummary summary = new AttemptScoreSummary(SkillScore.insufficientData(), skillScores);

        ReportResponse response = ReportMapper.toResponse(report, summary);

        assertThat(response.attemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(response.sessionPublicId()).isEqualTo(sessionPublicId);
    }

    @Test
    void toResponse_mapsSkillScoreFields() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setPublished(true);

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        skillScores.put(Skill.LISTENING, SkillScore.of(50));
        skillScores.put(Skill.READING, SkillScore.of(60));
        skillScores.put(Skill.SPEAKING, SkillScore.of(70));
        skillScores.put(Skill.WRITING, SkillScore.of(80));
        for (Skill skill : Skill.values()) {
            if (!skillScores.containsKey(skill)) {
                skillScores.put(skill, SkillScore.insufficientData());
            }
        }

        AttemptScoreSummary summary = new AttemptScoreSummary(SkillScore.of(65), skillScores);

        ReportResponse response = ReportMapper.toResponse(report, summary);

        // Check one communicative skill
        SkillScoreResponse listeningResponse = response.communicativeSkills().stream()
                .filter(s -> s.skill().equals("LISTENING"))
                .findFirst()
                .orElseThrow();
        assertThat(listeningResponse.score()).isEqualTo(50);
        assertThat(listeningResponse.sufficientData()).isTrue();

        // Check an enabling skill
        SkillScoreResponse spellingResponse = response.enablingSkills().stream()
                .filter(s -> s.skill().equals("SPELLING"))
                .findFirst()
                .orElseThrow();
        assertThat(spellingResponse.score()).isNull();
        assertThat(spellingResponse.sufficientData()).isFalse();
    }
}
