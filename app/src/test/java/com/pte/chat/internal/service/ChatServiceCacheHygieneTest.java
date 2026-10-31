package com.pte.chat.internal.service;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Questions that may carry personal text are answered but neither looked up in nor stored to the shared cache. */
class ChatServiceCacheHygieneTest extends ChatServiceTestSupport {

    @Test
    void aShortPlainQuestionIsStoredForOthers() {
        service.send(caller, command("How do I use the present perfect?"));

        verify(cacheService).storeQuietly(anyString(), anyString(), anyString());
    }

    @Test
    void aQuestionOverTheConfiguredLengthIsAnsweredButNeverCached() {
        properties.setCacheMaxQuestionChars(40);

        service.send(caller, command("Please check this whole paragraph that I wrote about my school"));

        verify(client).generate(any(), any(), any());
        verify(cacheService, never()).find(any());
        verify(cacheService, never()).storeQuietly(any(), any(), any());
    }

    @Test
    void aQuestionWithAnEmailIsAnsweredButNeverCached() {
        service.send(caller, command("Fix this: contact me at nguyen.van.an@gmail.com"));

        verify(client).generate(any(), any(), any());
        verify(cacheService, never()).find(any());
        verify(cacheService, never()).storeQuietly(any(), any(), any());
    }

    @Test
    void aQuestionWithAPhoneNumberIsAnsweredButNeverCached() {
        service.send(caller, command("My number is 0912 345 678, can you check my sentence?"));

        verify(cacheService, never()).find(any());
        verify(cacheService, never()).storeQuietly(any(), any(), any());
    }
}
