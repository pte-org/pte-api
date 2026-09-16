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

/**
 * Since Phase 5 (plans/score-template-exam-generation): no more
 * enablingSkills field on the wire at all (spec Out of Scope); {@code
 * communicativeSkills} carries only the skills FR-19 says were tested
 * (whatever keys {@code AttemptScoreSummary.skillScores()} has — the mapper
 * doesn't filter, {@code ScoreAggregationService} already decided that);
 * {@code overall} is {@code null} (FR-20 "not applicable"), distinct from a
 * present-but-{@code sufficientData=false} value.
 */
class ReportMapperTest {

    private AttemptReport report(UUID attemptPublicId, UUID sessionPublicId, boolean published, Instant publishedAt) {
        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setPublished(published);
        report.setPublishedAt(publishedAt);
        return report;
    }

    @Test
    void toResponse_communicativeSkills_onlyContainsWhateverKeysTheSummaryHas() {
        AttemptReport report = report(UUID.randomUUID(), UUID.randomUUID(), true, Instant.now());
        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        skillScores.put(Skill.SPEAKING, SkillScore.of(70));
        skillScores.put(Skill.READING, SkillScore.of(60));
        AttemptScoreSummary summary = new AttemptScoreSummary(null, skillScores);

        ReportResponse response = ReportMapper.toResponse(report, summary);

        assertThat(response.communicativeSkills()).hasSize(2);
        assertThat(response.communicativeSkills())
                .extracting(SkillScoreResponse::skill)
                .containsExactlyInAnyOrder("SPEAKING", "READING");
    }

    @Test
    void toResponse_overallNull_whenSummaryOverallIsNull() {
        AttemptReport report = report(UUID.randomUUID(), UUID.randomUUID(), true, Instant.now());
        AttemptScoreSummary summary = new AttemptScoreSummary(null, new EnumMap<>(Skill.class));

        ReportResponse response = ReportMapper.toResponse(report, summary);

        assertThat(response.overall()).isNull();
    }

    @Test
    void toResponse_overallPresentButInsufficientData_notConflatedWithNull() {
        AttemptReport report = report(UUID.randomUUID(), UUID.randomUUID(), true, Instant.now());
        AttemptScoreSummary summary = new AttemptScoreSummary(SkillScore.insufficientData(), new EnumMap<>(Skill.class));

        ReportResponse response = ReportMapper.toResponse(report, summary);

        assertThat(response.overall()).isNotNull();
        assertThat(response.overall().sufficientData()).isFalse();
        assertThat(response.overall().score()).isNull();
    }

    @Test
    void toResponse_overallPresentAndSufficientData_carriesScore() {
        AttemptReport report = report(UUID.randomUUID(), UUID.randomUUID(), true, Instant.now());
        AttemptScoreSummary summary = new AttemptScoreSummary(SkillScore.of(75), new EnumMap<>(Skill.class));

        ReportResponse response = ReportMapper.toResponse(report, summary);

        assertThat(response.overall().score()).isEqualTo(75);
        assertThat(response.overall().sufficientData()).isTrue();
    }

    @Test
    void toResponse_carriesPublishedAndPublishedAtFromReport() {
        Instant publishedAt = Instant.now();
        AttemptReport report = report(UUID.randomUUID(), UUID.randomUUID(), true, publishedAt);
        AttemptScoreSummary summary = new AttemptScoreSummary(null, new EnumMap<>(Skill.class));

        ReportResponse response = ReportMapper.toResponse(report, summary);

        assertThat(response.published()).isTrue();
        assertThat(response.publishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void toResponse_carriesAttemptAndSessionIds() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        AttemptReport report = report(attemptPublicId, sessionPublicId, false, null);
        AttemptScoreSummary summary = new AttemptScoreSummary(null, new EnumMap<>(Skill.class));

        ReportResponse response = ReportMapper.toResponse(report, summary);

        assertThat(response.attemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(response.sessionPublicId()).isEqualTo(sessionPublicId);
    }

    @Test
    void toResponse_mapsSkillScoreFieldsIncludingInsufficientData() {
        AttemptReport report = report(UUID.randomUUID(), UUID.randomUUID(), true, Instant.now());
        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        skillScores.put(Skill.LISTENING, SkillScore.of(50));
        skillScores.put(Skill.WRITING, SkillScore.insufficientData());
        AttemptScoreSummary summary = new AttemptScoreSummary(null, skillScores);

        ReportResponse response = ReportMapper.toResponse(report, summary);

        SkillScoreResponse listening = response.communicativeSkills().stream()
                .filter(s -> s.skill().equals("LISTENING")).findFirst().orElseThrow();
        assertThat(listening.score()).isEqualTo(50);
        assertThat(listening.sufficientData()).isTrue();

        SkillScoreResponse writing = response.communicativeSkills().stream()
                .filter(s -> s.skill().equals("WRITING")).findFirst().orElseThrow();
        assertThat(writing.score()).isNull();
        assertThat(writing.sufficientData()).isFalse();
    }
}
