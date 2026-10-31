package com.pte.chat.internal.dto;

import com.pte.chat.internal.dto.request.ChatMessageRequest;
import com.pte.chat.internal.dto.response.ChatMessageItem;
import com.pte.chat.internal.dto.response.ChatMessageResponse;
import com.pte.chat.internal.dto.response.ChatQuotaResponse;
import com.pte.chat.internal.dto.response.ChatSessionResponse;
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
import java.util.UUID;
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
                Map.entry("sessionId", UUID.class), Map.entry("message", String.class),
                Map.entry("taskType", String.class), Map.entry("language", String.class),
                Map.entry("exercisePrompt", String.class));
    }

    @Test
    void theMessageResponseHasTheAgreedFields() {
        assertThat(components(ChatMessageResponse.class)).containsExactly(
                Map.entry("markdown", String.class), Map.entry("onTopic", boolean.class),
                Map.entry("fromCache", boolean.class), Map.entry("remaining", int.class),
                Map.entry("limit", int.class), Map.entry("resetAt", Instant.class),
                Map.entry("sessionId", UUID.class));
    }

    @Test
    void theQuotaResponseHasTheAgreedFields() {
        assertThat(components(ChatQuotaResponse.class)).containsExactly(
                Map.entry("used", int.class), Map.entry("remaining", int.class),
                Map.entry("limit", int.class), Map.entry("resetAt", Instant.class));
    }

    @Test
    void theMessageItemHasTheAgreedFields() {
        assertThat(components(ChatMessageItem.class)).containsExactly(
                Map.entry("question", String.class), Map.entry("answer", String.class),
                Map.entry("status", String.class), Map.entry("createdAt", Instant.class));
    }

    @Test
    void theSessionResponseHasTheAgreedFields() {
        assertThat(components(ChatSessionResponse.class)).containsExactly(
                Map.entry("sessionId", UUID.class), Map.entry("messages", List.class));
    }

    @Test
    void aCompleteRequestIsValid() {
        assertThat(VALIDATOR.validate(
                new ChatMessageRequest(UUID.randomUUID(), "How do I use it?", "WRITE_ESSAY", "vi"))).isEmpty();
        assertThat(VALIDATOR.validate(new ChatMessageRequest(UUID.randomUUID(), "How do I use it?", null, null)))
                .isEmpty();
    }

    @Test
    void aNullSessionIdIsRejected() {
        assertThat(violatedFields(new ChatMessageRequest(null, "Question", null, null))).containsExactly("sessionId");
    }

    @Test
    void aNullEmptyOrBlankMessageIsRejected() {
        UUID id = UUID.randomUUID();

        assertThat(violatedFields(new ChatMessageRequest(id, null, null, null))).containsExactly("message");
        assertThat(violatedFields(new ChatMessageRequest(id, "", null, null))).containsExactly("message");
        assertThat(violatedFields(new ChatMessageRequest(id, " \n\t ", null, null))).containsExactly("message");
    }

    @Test
    void bothMissingFieldsAreReported() {
        assertThat(violatedFields(new ChatMessageRequest(null, "  ", null, null)))
                .containsExactlyInAnyOrder("sessionId", "message");
    }

    @Test
    void theLengthOfTheMessageIsLeftToTheService() {
        // the limit is configuration (chat.ai.max-input-chars), not a hard-coded annotation
        assertThat(violatedFields(new ChatMessageRequest(UUID.randomUUID(), "a".repeat(5_000), null, null))).isEmpty();
    }
}
