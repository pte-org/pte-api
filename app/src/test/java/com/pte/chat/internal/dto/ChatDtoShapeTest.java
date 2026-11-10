package com.pte.chat.internal.dto;

import com.pte.chat.internal.dto.request.ChatHistoryItem;
import com.pte.chat.internal.dto.request.ChatMessageRequest;
import com.pte.chat.internal.dto.response.ChatMessageResponse;
import com.pte.chat.internal.dto.response.ChatQuotaResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The JSON field names are the contract with the frontend; the request rules are Bean Validation, not code. */
class ChatDtoShapeTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Map<String, Class<?>> components(Class<?> record) {
        assertThat(record.isRecord()).as(record.getSimpleName() + " is a record").isTrue();
        return Arrays.stream(record.getRecordComponents())
                .collect(Collectors.toMap(RecordComponent::getName, RecordComponent::getType,
                        (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private static Set<String> violatedFields(ChatMessageRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    void theRequestHasTheAgreedFields() {
        assertThat(components(ChatMessageRequest.class)).containsExactly(
                Map.entry("message", String.class), Map.entry("taskType", String.class),
                Map.entry("language", String.class), Map.entry("exercisePrompt", String.class),
                Map.entry("history", List.class));
    }

    @Test
    void anEarlierTurnHasARoleAndText() {
        assertThat(components(ChatHistoryItem.class)).containsExactly(
                Map.entry("role", String.class), Map.entry("text", String.class));
    }

    @Test
    void theMessageResponseHasTheAgreedFieldsAndNoSessionOrCacheFlag() {
        assertThat(components(ChatMessageResponse.class)).containsExactly(
                Map.entry("markdown", String.class), Map.entry("onTopic", boolean.class),
                Map.entry("remaining", int.class), Map.entry("limit", int.class),
                Map.entry("resetAt", Instant.class));
    }

    @Test
    void theQuotaResponseHasTheAgreedFields() {
        assertThat(components(ChatQuotaResponse.class)).containsExactly(
                Map.entry("used", int.class), Map.entry("remaining", int.class),
                Map.entry("limit", int.class), Map.entry("resetAt", Instant.class));
    }

    @Test
    void aCompleteRequestIsValid() {
        assertThat(VALIDATOR.validate(
                new ChatMessageRequest("How do I use it?", "WRITE_ESSAY", "vi"))).isEmpty();
        assertThat(VALIDATOR.validate(new ChatMessageRequest("How do I use it?", null, null)))
                .isEmpty();
    }

    @Test
    void aNullEmptyOrBlankMessageIsRejected() {
        assertThat(violatedFields(new ChatMessageRequest(null, null, null))).containsExactly("message");
        assertThat(violatedFields(new ChatMessageRequest("", null, null))).containsExactly("message");
        assertThat(violatedFields(new ChatMessageRequest(" \n\t ", null, null))).containsExactly("message");
    }

    @Test
    void theLengthOfTheMessageIsLeftToTheService() {
        // the limit is configuration (chat.ai.max-input-chars), not a hard-coded annotation
        assertThat(violatedFields(new ChatMessageRequest("a".repeat(5_000), null, null))).isEmpty();
    }
}
