package com.pte.chat;

import com.pte.chat.internal.constant.ChatConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 3 additions to {@link ChatConstants}: the HTTP contract the frontend codes against. */
class ChatEndpointConstantsTest {

    @Test
    void endpointPathsAreTheAgreedContract() {
        assertThat(ChatConstants.CHAT_PATH).isEqualTo("/api/v1/student/chat");
        assertThat(ChatConstants.MESSAGES_PATH).isEqualTo("/messages");
        assertThat(ChatConstants.QUOTA_PATH).isEqualTo("/quota");
    }

    @Test
    void errorCodesAreStableStrings() {
        assertThat(ChatConstants.ERR_QUOTA_EXCEEDED).isEqualTo("CHAT_QUOTA_EXCEEDED");
        assertThat(ChatConstants.ERR_REQUEST_IN_FLIGHT).isEqualTo("CHAT_REQUEST_IN_FLIGHT");
        assertThat(ChatConstants.ERR_BUSY).isEqualTo("CHAT_BUSY");
        assertThat(ChatConstants.ERR_INVALID_REQUEST).isEqualTo("CHAT_INVALID_REQUEST");
    }
}
