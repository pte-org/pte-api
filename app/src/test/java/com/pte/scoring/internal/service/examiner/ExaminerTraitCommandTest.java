package com.pte.scoring.internal.service.examiner;

import com.pte.scoring.CriteriaScores;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.scoring.dto.request.*;
import com.pte.scoring.internal.exception.examiner.*;
import com.pte.scoring.rubric.*;
import com.pte.shared.idempotency.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExaminerTraitCommandTest {
    @Test void directSubmissionPersistsTraitsAndReturnsTheAuthoritativeTrace() {
        var f = new ExaminerTraitCommandFixture();
        var response = f.direct.submit(f.answerId, submit(f, 0, UUID.randomUUID()), f.user);
        assertThat(response.itemScore()).isZero(); assertThat(response.myScore()).isZero();
        assertThat(response.scoreTrace().gateTriggeredBy()).isEqualTo("CONTENT");
        assertThat(response.decision().traitScores()).containsEntry("PRONUNCIATION", 3);
        var saved = ArgumentCaptor.forClass(ExaminerEvaluation.class); verify(f.scores).saveAndFlush(saved.capture());
        assertThat(CriteriaScores.read(f.json, saved.getValue().getTraitScoresJson()).traits()).isEqualTo(response.decision().traitScores());
        assertThat(saved.getValue().getRubricVersionPublicId()).isEqualTo(f.version);
        verify(f.attempts).selectScoreSource(any(), eq(f.tenant), eq("EXAMINER"), eq(f.user.userId()), any(), any());
    }
    @Test void directReplayUsesStoredTraitsWhenAiChangesButChangedInputConflicts() {
        var f = new ExaminerTraitCommandFixture(); var request = submit(f, 2, UUID.randomUUID());
        var response = f.direct.submit(f.answerId, request, f.user);
        var saved = ArgumentCaptor.forClass(ExaminerEvaluation.class); verify(f.scores).saveAndFlush(saved.capture());
        when(f.scores.findByAnswerPublicIdAndTenantId(f.answerId, f.tenant)).thenReturn(Optional.of(saved.getValue()));
        f.answer.recordDetail(null, null, null, null, false, f.version);
        var replay = f.direct.submit(f.answerId, request, f.user);
        assertThat(replay.itemScore()).isEqualTo(response.itemScore()); assertThat(replay.scoreTrace()).isEqualTo(response.scoreTrace());
        var changed = new SubmitExaminerScoreRequest(null, request.decisionKind(), request.expectedReviewVersion(), request.reviewedAiVersion(), request.requestPublicId(), f.input(1));
        assertThatThrownBy(() -> f.direct.submit(f.answerId, changed, f.user)).isInstanceOf(ExaminerReceiptConflictException.class);
        verify(f.scores, times(1)).saveAndFlush(any());
    }
    @Test void confirmAiChecksFullTraitsEvenWhenTheClientOmitsTheTotal() {
        var f = new ExaminerTraitCommandFixture(); String ai = f.versions.buildAiReference(f.answer).version();
        var request = new SubmitExaminerScoreRequest(null, ExaminerDecisionKind.CONFIRM_AI, f.review(), ai, UUID.randomUUID(), f.input(3));
        var response = f.direct.submit(f.answerId, request, f.user);
        assertThat(response.itemScore()).isEqualTo(f.answer.getRawScore());
        var other = new ExaminerTraitCommandFixture();
        var bad = new SubmitExaminerScoreRequest(null, ExaminerDecisionKind.CONFIRM_AI, other.review(), other.versions.buildAiReference(other.answer).version(), UUID.randomUUID(), other.input(1));
        assertThatThrownBy(() -> other.direct.submit(other.answerId, bad, other.user)).isInstanceOf(ConfirmAiMismatchException.class);
        verify(other.scores, never()).saveAndFlush(any());
    }
    @Test void draftResponseAndFinalizeUseExactlyTheSavedTraits() {
        var f = new ExaminerTraitCommandFixture(); String review = f.review();
        var request = new SaveExaminerDraftRequest(UUID.randomUUID(), f.work.getWorkVersion(), 0, null, "note", ExaminerDecisionKind.MANUAL, review, null, f.input(0));
        var response = f.drafts.saveDraft(f.session, f.attempt, f.answerId, request, f.user);
        assertThat(response.itemScore()).isZero(); assertThat(response.scoreTrace().gateTriggeredBy()).isEqualTo("CONTENT");
        assertThat(response.draft().traitScores()).containsEntry("CONTENT", 0).containsEntry("ORAL_FLUENCY", 3);
        var saved = ArgumentCaptor.forClass(ExaminerEvaluation.class); verify(f.scores).save(saved.capture());
        when(f.scores.findByTenantIdAndAssignmentIdAndDeletedFalse(f.tenant, 7L)).thenReturn(List.of(saved.getValue()));
        var finalize = new FinalizeExaminerWorkRequest(UUID.randomUUID(), f.work.getWorkVersion(), List.of(new FinalizeExaminerWorkRequest.FinalizeAnswerItemRequest(f.answerId, 1, review, null)));
        f.finalize.submitAll(f.user, f.session, f.attempt, f.work, finalize, List.of(f.answer), com.pte.TestClocks.fixed().instant());
        assertThat(saved.getValue().isSubmitted()).isTrue(); assertThat(saved.getValue().getScore()).isZero();
        assertThat(f.traits.stored(f.answer, saved.getValue()).scoreTrace()).isEqualTo(response.scoreTrace());
    }
    @Test void staleReviewDetectsTraitChangeWithoutAnyChangeToTheAiTotal() {
        var f = new ExaminerTraitCommandFixture(); String old = f.review(); String aiVersion = f.versions.buildAiReference(f.answer).version();
        f.answer.recordDetail(CriteriaScores.pearson(f.version, Map.of("CONTENT", 4, "PRONUNCIATION", 3, "ORAL_FLUENCY", 3)).toJson(f.json), null, null, null, false, f.version);
        assertThat(f.versions.buildAiReference(f.answer).version()).isNotEqualTo(aiVersion);
        var request = new SaveExaminerDraftRequest(UUID.randomUUID(), f.work.getWorkVersion(), 0, null, null, ExaminerDecisionKind.MANUAL, old, null, f.input(2));
        assertThatThrownBy(() -> f.drafts.saveDraft(f.session, f.attempt, f.answerId, request, f.user)).isInstanceOf(StaleReviewVersionException.class);
        verify(f.scores, never()).save(any());
    }
    @Test void draftReplayDoesNotReapplyAzureFallbackAndKeepsTheSameTrace() {
        var f = new ExaminerTraitCommandFixture();
        var request = new SaveExaminerDraftRequest(UUID.randomUUID(), f.work.getWorkVersion(), 0, null, null, ExaminerDecisionKind.MANUAL, f.review(), null, f.input(2));
        var original = f.drafts.saveDraft(f.session, f.attempt, f.answerId, request, f.user);
        var saved = ArgumentCaptor.forClass(ExaminerEvaluation.class); verify(f.scores).save(saved.capture());
        when(f.scores.findByTenantIdAndAssignmentIdAndAnswerPublicIdAndDeletedFalse(f.tenant, 7L, f.answerId)).thenReturn(Optional.of(saved.getValue()));
        when(f.idempotency.begin(any(), any(), any(), any(), any())).thenReturn(new IdempotencyDecision(IdempotencyOutcome.REPLAY, f.json.writeValueAsString(original.receipt())));
        f.answer.recordDetail(null, null, null, null, false, f.version);
        var replay = f.drafts.saveDraft(f.session, f.attempt, f.answerId, request, f.user);
        assertThat(replay.itemScore()).isEqualTo(original.itemScore()); assertThat(replay.scoreTrace()).isEqualTo(original.scoreTrace());
        verify(f.scores, times(1)).save(any());
    }
    @Test void fingerprintBindsTraitsNullsAndRubricVersionAndCanonicalizesOrder() {
        var f = new ExaminerTraitCommandFixture(); var input = f.input(2);
        String original = f.versions.computeRequestFingerprint(f.answerId, null, ExaminerDecisionKind.MANUAL, "rv", "ai", input, f.version);
        var reverse = new LinkedHashMap<String, java.math.BigDecimal>(); input.entrySet().stream().sorted(Map.Entry.<String, java.math.BigDecimal>comparingByKey().reversed()).forEach(e -> reverse.put(e.getKey(), e.getValue()));
        assertThat(f.versions.computeRequestFingerprint(f.answerId, null, ExaminerDecisionKind.MANUAL, "rv", "ai", reverse, f.version)).isEqualTo(original);
        assertThat(f.versions.computeRequestFingerprint(f.answerId, null, ExaminerDecisionKind.MANUAL, "rv", "ai", f.input(1), f.version)).isNotEqualTo(original);
        assertThat(f.versions.computeRequestFingerprint(f.answerId, null, ExaminerDecisionKind.MANUAL, "rv", "ai", input, UUID.randomUUID())).isNotEqualTo(original);
    }
    private SubmitExaminerScoreRequest submit(ExaminerTraitCommandFixture f, int content, UUID request) {
        return new SubmitExaminerScoreRequest(null, ExaminerDecisionKind.MANUAL, f.review(), null, request, f.input(content));
    }
}
