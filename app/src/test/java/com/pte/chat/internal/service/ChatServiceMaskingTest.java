package com.pte.chat.internal.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** Contact details a student types never reach Google. */
class ChatServiceMaskingTest extends ChatServiceTestSupport {

    private static final String TYPED = "Check my sentence, I am an.nguyen@gmail.com or 0912 345 678";
    private static final String MASKED = "Check my sentence, I am [email] or [phone]";

    @Test
    void theProviderReceivesTheMaskedQuestion() {
        service.send(caller, command(TYPED));

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(client).generate(any(), any(), sent.capture());
        assertThat(sent.getValue()).isEqualTo(MASKED).doesNotContain("@").doesNotContain("0912");
    }

    @Test
    void aQuestionWithoutContactDetailsIsSentAsTyped() {
        service.send(caller, command("How do I use the present perfect?"));

        verify(client).generate(any(), any(), eq("How do I use the present perfect?"));
    }

    @Test
    void theLengthLimitStillCountsWhatTheStudentTyped() {
        properties.setMaxInputChars(20);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(caller, command(TYPED)))
                .isInstanceOf(com.pte.chat.internal.exception.ChatInvalidRequestException.class);
    }
}
