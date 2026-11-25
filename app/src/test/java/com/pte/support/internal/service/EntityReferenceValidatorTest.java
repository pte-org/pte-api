package com.pte.support.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.itembank.ItembankService;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import com.pte.support.domain.enums.TicketEntityType;
import com.pte.support.internal.dto.response.TicketEntityContext;
import com.pte.support.internal.exception.EntityReferenceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityReferenceValidatorTest {

    @Mock
    private ItembankService itembankService;
    @Mock
    private SessionService sessionService;
    @Mock
    private AttemptService attemptService;

    private EntityReferenceValidator validator;
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));

    @BeforeEach
    void setUp() {
        validator = new EntityReferenceValidator(itembankService, sessionService, attemptService);
    }

    private QuestionResponse question(String taskTypeKey, String section, String promptText) {
        QuestionResponse question = mock(QuestionResponse.class);
        when(question.taskTypeKey()).thenReturn(taskTypeKey);
        when(question.section()).thenReturn(section);
        when(question.promptText()).thenReturn(promptText);
        return question;
    }

    @Test
    void describe_question_returnsTaskTypeAndPromptSnippet() {
        UUID id = UUID.randomUUID();
        QuestionResponse found = question("WRITE_FROM_DICTATION", "LISTENING", "  Please   confirm\nthat you have  ");
        when(itembankService.get(id, caller)).thenReturn(found);

        TicketEntityContext context = validator.describe(TicketEntityType.QUESTION, id.toString(), caller);

        assertThat(context).isEqualTo(new TicketEntityContext("WRITE_FROM_DICTATION", "LISTENING",
                "Please confirm that you have"));
    }

    @Test
    void describe_longPrompt_isTruncatedWithEllipsis() {
        UUID id = UUID.randomUUID();
        QuestionResponse found = question("READ_ALOUD", "SPEAKING", "word ".repeat(100));
        when(itembankService.get(id, caller)).thenReturn(found);

        TicketEntityContext context = validator.describe(TicketEntityType.QUESTION, id.toString(), caller);

        assertThat(context.promptSnippet()).hasSizeLessThanOrEqualTo(201).endsWith("…");
    }

    @Test
    void describe_blankPrompt_hasNoSnippet() {
        UUID id = UUID.randomUUID();
        QuestionResponse found = question("DESCRIBE_IMAGE", "SPEAKING", "   ");
        when(itembankService.get(id, caller)).thenReturn(found);

        assertThat(validator.describe(TicketEntityType.QUESTION, id.toString(), caller).promptSnippet()).isNull();
    }

    @Test
    void describe_deletedQuestion_returnsNullInsteadOfFailing() {
        UUID id = UUID.randomUUID();
        when(itembankService.get(any(), any())).thenThrow(new EntityReferenceNotFoundException());

        assertThat(validator.describe(TicketEntityType.QUESTION, id.toString(), caller)).isNull();
    }

    @Test
    void describe_unlinkedOrOtherEntity_returnsNullWithoutLookup() {
        assertThat(validator.describe(null, null, caller)).isNull();
        assertThat(validator.describe(TicketEntityType.QUESTION, " ", caller)).isNull();
        assertThat(validator.describe(TicketEntityType.QUESTION, "not-a-uuid", caller)).isNull();
        assertThat(validator.describe(TicketEntityType.EXAM_SESSION, UUID.randomUUID().toString(), caller)).isNull();
        verifyNoInteractions(itembankService);
    }
}
