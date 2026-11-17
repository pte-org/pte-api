package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.draft.DraftParser;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.exception.AiPromptException;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import com.pte.itembank.internal.generation.llm.LlmFailureType;
import com.pte.itembank.internal.generation.llm.LlmGateway;
import com.pte.itembank.internal.generation.llm.LlmProviderException;
import com.pte.itembank.internal.generation.llm.LlmRole;
import com.pte.itembank.internal.generation.prompt.PromptTestRegistry;
import com.pte.itembank.internal.generation.prompt.version.dto.PromptTestResponse;
import com.pte.itembank.GenerationRubricTraits;
import com.pte.itembank.internal.generation.review.ReviewPromptBuilder;
import com.pte.itembank.internal.generation.review.ReviewVerdictParser;
import com.pte.itembank.internal.generation.validate.DraftValidator;
import com.pte.itembank.internal.generation.validate.ValidationIssue;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.exception.GenerationLimitException;
import com.pte.itembank.internal.generation.usage.PromptTestQuota;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PromptTestServiceTest {

    private static final String TASK = "READ_ALOUD";

    private final PromptVersionTestKit kit = new PromptVersionTestKit();
    private final LlmGateway gateway = mock(LlmGateway.class);
    private final DraftParser parser = mock(DraftParser.class);
    private final DraftValidator validator = mock(DraftValidator.class);
    private final CurrentUser author = PromptVersionTestKit.staff();
    private final ItemDraft draft = new ItemDraft.ChoiceDraft("t", "p", "q", List.of("a", "b"), List.of(0));

    private final PromptTestQuota quota = mock(PromptTestQuota.class);

    @SuppressWarnings("unchecked")
    private PromptTestService service(LlmGateway available) {
        ObjectProvider<LlmGateway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(available);
        return new PromptTestService(kit.repository, PromptVersionTestKit.CODEC, PromptTestRegistry.create(),
                provider, parser, validator, new ReviewPromptBuilder(mock(GenerationRubricTraits.class),
                PromptTestRegistry.create()), new ReviewVerdictParser(), quota, PromptVersionTestKit.CLOCK);
    }

    @Test
    void aReachedDailyCapStopsTheRunBeforeTheModelAndRecordsNothing() {
        AiPromptVersion version = newDraft(TASK);
        clearInvocations(kit.repository); // forget the saving done while preparing the draft
        doThrow(new GenerationLimitException(PromptVersionConstants.TEST_DAILY_LIMIT_REACHED,
                PromptVersionConstants.TEST_DAILY_LIMIT_REACHED_MESSAGE)).when(quota).consume(author.userId());

        assertThatThrownBy(() -> service(gateway).run(version.getPublicId(), author))
                .isInstanceOfSatisfying(GenerationLimitException.class, failure -> {
                    assertThat(failure.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(failure.getCode()).isEqualTo(PromptVersionConstants.TEST_DAILY_LIMIT_REACHED);
                });

        verifyNoInteractions(gateway);
        verify(kit.repository, never()).saveAndFlush(any(AiPromptVersion.class));
        assertThat(version.getTestedAt()).isNull();
        assertThat(version.hasPassingTest()).isFalse();
    }

    @Test
    void anUnavailableProviderDoesNotUseUpTheQuota() {
        AiPromptVersion version = newDraft(TASK);

        assertThatThrownBy(() -> service(null).run(version.getPublicId(), author))
                .isInstanceOf(AiPromptException.class);

        verifyNoInteractions(quota);
    }

    @Test
    void aVersionThatCannotBeTestedNowDoesNotUseUpTheQuota() {
        AiPromptVersion live = kit.live(AiPromptKind.GENERATOR, TASK);

        assertThatThrownBy(() -> service(gateway).run(live.getPublicId(), PromptVersionTestKit.admin()))
                .isInstanceOfSatisfying(AiPromptException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo(PromptVersionConstants.NOT_EDITABLE));

        verifyNoInteractions(quota, gateway);
    }

    @Test
    void anUnknownVersionDoesNotUseUpTheQuota() {
        assertThatThrownBy(() -> service(gateway).run(UUID.randomUUID(), author))
                .isInstanceOf(AiPromptException.class);

        verifyNoInteractions(quota, gateway);
    }

    @Test
    void aPermittedRunCountsOnceForTheCallerAndBeforeTheModelCall() {
        AiPromptVersion version = newDraft(TASK);
        when(gateway.completeJson(any(), any(), any())).thenReturn("{}");
        when(parser.parse(any(), any())).thenReturn(draft);
        when(validator.validate(any(), any())).thenReturn(List.of());

        service(gateway).run(version.getPublicId(), author);

        InOrder order = inOrder(quota, gateway);
        order.verify(quota).consume(author.userId());
        order.verify(gateway).completeJson(any(), any(), any());
        verify(quota, times(1)).consume(any());
        verify(gateway, times(1)).completeJson(any(), any(), any());
    }

    @Test
    void aProviderFailureAfterTheRunWasCountedStaysCountedAndIsReportedAsAFailedTest() {
        AiPromptVersion version = newDraft(TASK);
        when(gateway.completeJson(any(), any(), any())).thenThrow(new LlmProviderException(LlmFailureType.UNAVAILABLE));

        PromptTestResponse result = service(gateway).run(version.getPublicId(), author);

        assertThat(result.passed()).isFalse();
        verify(quota, times(1)).consume(author.userId());
        verifyNoMoreInteractions(quota);
    }

    private AiPromptVersion newDraft(String key) {
        return kit.byId(kit.propose("COMMON_GENERATOR".equals(key) ? "SHARED" : "GENERATION", key, "Extra.", author).publicId()).orElseThrow();
    }

    @Test
    void aGoodAnswerPassesAndTheResultIsTiedToTheContentTested() {
        AiPromptVersion version = newDraft(TASK);
        when(gateway.completeJson(eq(LlmRole.GENERATOR), any(), any())).thenReturn("{\"topic\":\"t\"}");
        when(parser.parse(any(), eq(TASK))).thenReturn(draft);
        when(validator.validate(draft, TASK)).thenReturn(List.of());

        PromptTestResponse result = service(gateway).run(version.getPublicId(), author);

        assertThat(result.passed()).isTrue();
        assertThat(result.sampleOutput()).contains("topic");
        assertThat(version.hasPassingTest()).isTrue();
        assertThat(version.getTestedByUserPublicId()).isEqualTo(author.userId());
    }

    @Test
    void theModelReceivesTheDraftTextAsTheTaskPromptAndTheFilledSharedPromptAsTheSystemPrompt() {
        AiPromptVersion version = newDraft(TASK);
        when(gateway.completeJson(any(), any(), any())).thenReturn("{}");
        when(parser.parse(any(), any())).thenReturn(draft);
        when(validator.validate(any(), any())).thenReturn(List.of());

        service(gateway).run(version.getPublicId(), author);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(gateway).completeJson(eq(LlmRole.GENERATOR), system.capture(), user.capture());
        assertThat(system.getValue()).contains("CEFR Level: B2").doesNotContain("{cefr}");
        assertThat(user.getValue()).startsWith("# Read Aloud");
    }

    @Test
    void anAnswerThatBreaksTheStructureChecksFailsAndSaysWhich() {
        AiPromptVersion version = newDraft(TASK);
        when(gateway.completeJson(any(), any(), any())).thenReturn("{}");
        when(parser.parse(any(), any())).thenReturn(draft);
        when(validator.validate(any(), any())).thenReturn(List.of(new ValidationIssue("TOO_SHORT", "short")));

        PromptTestResponse result = service(gateway).run(version.getPublicId(), author);

        assertThat(result.passed()).isFalse();
        assertThat(result.note()).contains("TOO_SHORT");
        assertThat(version.hasPassingTest()).isFalse();
    }

    @Test
    void anAnswerThatIsNotJsonFails() {
        AiPromptVersion version = newDraft(TASK);
        when(gateway.completeJson(any(), any(), any())).thenReturn("not json");
        when(parser.parse(any(), any())).thenThrow(new GenerationRequestInvalidException("GENERATION_SCHEMA_INVALID", "x"));

        PromptTestResponse result = service(gateway).run(version.getPublicId(), author);

        assertThat(result.passed()).isFalse();
        assertThat(result.note()).contains("GENERATION_SCHEMA_INVALID");
    }

    @Test
    void aProviderFailureIsReportedNotThrown() {
        AiPromptVersion version = newDraft(TASK);
        when(gateway.completeJson(any(), any(), any())).thenThrow(new LlmProviderException(LlmFailureType.UNAVAILABLE));

        PromptTestResponse result = service(gateway).run(version.getPublicId(), author);

        assertThat(result.passed()).isFalse();
        assertThat(result.note()).contains(LlmFailureType.UNAVAILABLE.code());
    }

    @Test
    void withoutAProviderTheTestIsUnavailable() {
        AiPromptVersion version = newDraft(TASK);

        assertThatThrownBy(() -> service(null).run(version.getPublicId(), author)).isInstanceOfSatisfying(
                AiPromptException.class,
                failure -> assertThat(failure.getCode()).isEqualTo(PromptVersionConstants.TEST_UNAVAILABLE));
    }

    @Test
    void aChangedSharedBlockIsTriedOnTheProbeTaskType() {
        AiPromptVersion version = newDraft("COMMON_GENERATOR");
        when(gateway.completeJson(any(), any(), any())).thenReturn("{}");
        when(parser.parse(any(), eq(PromptVersionConstants.SHARED_TEST_TASK_TYPE))).thenReturn(draft);
        when(validator.validate(any(), eq(PromptVersionConstants.SHARED_TEST_TASK_TYPE))).thenReturn(List.of());

        assertThat(service(gateway).run(version.getPublicId(), author).passed()).isTrue();
    }

    @Test
    void aReviewerPromptPassesWhenTheModelAnswersWithAllSixChecks() {
        AiPromptVersion version = kit.byId(kit.propose("REVIEW", "REVIEWER", "Be strict.", author).publicId())
                .orElseThrow();
        when(gateway.completeJson(eq(LlmRole.REVIEWER), any(), any())).thenReturn("""
                {"verdict":"PASS","checks":{"taskFidelity":{"ok":true},"cefrFit":{"ok":true,"estimated":"B2"},
                "answerKey":{"ok":true},"scorability":{"ok":true},"clarity":{"ok":true},"safety":{"ok":true}},
                "fixSuggestion":""}""");

        PromptTestResponse result = service(gateway).run(version.getPublicId(), author);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void aReviewerAnswerThatLeavesOutChecksFails() {
        AiPromptVersion version = kit.byId(kit.propose("REVIEW", "REVIEWER", "Be strict.", author).publicId())
                .orElseThrow();
        when(gateway.completeJson(eq(LlmRole.REVIEWER), any(), any()))
                .thenReturn("{\"verdict\":\"PASS\",\"checks\":{\"clarity\":{\"ok\":true}}}");

        PromptTestResponse result = service(gateway).run(version.getPublicId(), author);

        assertThat(result.passed()).isFalse();
        assertThat(result.note()).contains("taskFidelity");
    }
}
