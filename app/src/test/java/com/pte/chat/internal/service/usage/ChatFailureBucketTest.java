package com.pte.chat.internal.service.usage;

import com.pte.chat.internal.client.ChatAiFailureReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChatFailureBucketTest {

    @Test
    void theReasonsAnAdminActsOnGetTheirOwnGroup() {
        assertThat(ChatFailureBucket.of(ChatAiFailureReason.TIMEOUT)).isEqualTo(ChatFailureBucket.TIMEOUT);
        assertThat(ChatFailureBucket.of(ChatAiFailureReason.RATE_LIMITED)).isEqualTo(ChatFailureBucket.RATE_LIMITED);
        assertThat(ChatFailureBucket.of(ChatAiFailureReason.AUTH_FAILED)).isEqualTo(ChatFailureBucket.AUTH);
        assertThat(ChatFailureBucket.of(ChatAiFailureReason.TRUNCATED)).isEqualTo(ChatFailureBucket.TRUNCATED);
    }

    @Test
    void anOutageAndAnUnreachableProviderShareAGroup() {
        assertThat(ChatFailureBucket.of(ChatAiFailureReason.UNAVAILABLE)).isEqualTo(ChatFailureBucket.UNAVAILABLE);
        assertThat(ChatFailureBucket.of(ChatAiFailureReason.NETWORK_ERROR)).isEqualTo(ChatFailureBucket.UNAVAILABLE);
    }

    @Test
    void anUnknownReasonIsOther() {
        assertThat(ChatFailureBucket.of(null)).isEqualTo(ChatFailureBucket.OTHER);
    }

    @ParameterizedTest
    @EnumSource(ChatAiFailureReason.class)
    void everyProviderReasonLandsInSomeGroup(ChatAiFailureReason reason) {
        assertThat(ChatFailureBucket.of(reason)).isNotNull();
    }
}
