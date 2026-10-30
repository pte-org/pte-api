package com.pte.chat;

import com.pte.chat.internal.constant.ChatConstants;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ChatConstantsTest {

    @Test
    void zone_is_ho_chi_minh() {
        assertThat(ChatConstants.ZONE).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test
    void statuses_are_the_three_persisted_values() {
        assertThat(ChatConstants.STATUS_OK).isEqualTo("OK");
        assertThat(ChatConstants.STATUS_REFUSED).isEqualTo("REFUSED");
        assertThat(ChatConstants.STATUS_FAILED).isEqualTo("FAILED");
    }

    @Test
    void enabled_property_name_is_chat_ai_enabled() {
        assertThat(ChatConstants.PROPERTY_ENABLED).isEqualTo("chat.ai.enabled");
    }
}
