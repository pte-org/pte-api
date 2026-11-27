package com.pte.itembank.internal.generation.llm;

import com.pte.itembank.internal.generation.stop.GenerationStop;
import com.pte.itembank.internal.generation.stop.GenerationStoppedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FallbackLlmGatewayTest {

    private final LlmGateway primary = mock(LlmGateway.class);
    private final LlmGateway usual = mock(LlmGateway.class);
    private final FallbackLlmGateway gateway = new FallbackLlmGateway(primary, usual);

    @AfterEach
    void unbind() {
        GenerationStop.unbind();
    }

    @Test
    void thePrimaryAnswersWhenItCan() {
        when(primary.completeJson(LlmRole.GENERATOR, "s", "u")).thenReturn("{\"a\":1}");

        assertThat(gateway.completeJson(LlmRole.GENERATOR, "s", "u")).isEqualTo("{\"a\":1}");
        verify(usual, never()).completeJson(any(), any(), any());
    }

    @Test
    void aPrimaryOutOfQuotaHandsTheCallToTheUsualGateway() {
        when(primary.completeJson(LlmRole.REVIEWER, "s", "u"))
                .thenThrow(new LlmProviderException(LlmFailureType.UNAVAILABLE));
        when(usual.completeJson(LlmRole.REVIEWER, "s", "u")).thenReturn("{\"b\":2}");

        assertThat(gateway.completeJson(LlmRole.REVIEWER, "s", "u")).isEqualTo("{\"b\":2}");
    }

    @Test
    void aRefusedKeyAlsoFallsBack() {
        when(primary.generateImage("p")).thenThrow(new LlmProviderException(LlmFailureType.AUTH));
        when(usual.generateImage("p")).thenReturn(new byte[] {1});

        assertThat(gateway.generateImage("p")).containsExactly(1);
    }

    @Test
    void whenBothFailTheUsualGatewaysFailureIsReported() {
        when(primary.completeJson(any(), any(), any())).thenThrow(new LlmProviderException(LlmFailureType.AUTH));
        when(usual.completeJson(any(), any(), any()))
                .thenThrow(new LlmProviderException(LlmFailureType.EMPTY_RESPONSE));

        assertThatThrownBy(() -> gateway.completeJson(LlmRole.GENERATOR, "s", "u"))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        e -> assertThat(e.getType()).isEqualTo(LlmFailureType.EMPTY_RESPONSE));
    }

    @Test
    void aStoppedJobDoesNotAskTheUsualGateway() {
        GenerationStop stop = new GenerationStop();
        stop.bind();
        when(primary.completeJson(LlmRole.GENERATOR, "s", "u")).thenAnswer(call -> {
            stop.request(); // a pause broke the call, and the provider reported it as an outage
            throw new LlmProviderException(LlmFailureType.UNAVAILABLE);
        });

        assertThatThrownBy(() -> gateway.completeJson(LlmRole.GENERATOR, "s", "u"))
                .isInstanceOf(GenerationStoppedException.class);
        verify(usual, never()).completeJson(any(), any(), any());
    }
}
