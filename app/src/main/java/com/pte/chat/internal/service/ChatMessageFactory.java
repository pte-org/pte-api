package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatAiAnswer;
import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.domain.ChatMessage;
import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.shared.security.CurrentUser;

/**
 * Builds the stored row for each outcome of a send. Every row keeps the prompt version and, when a provider
 * response existed, its token counts and finish reason, so cost can be read back per status.
 */
final class ChatMessageFactory {

    private final ChatAiProperties properties;

    ChatMessageFactory(ChatAiProperties properties) {
        this.properties = properties;
    }

    /** A cache hit: free for the student and no provider call, so the token counts are zero. */
    ChatMessage cached(CurrentUser caller, ChatSendInput input, String answer) {
        ChatMessage message = base(caller, input, ChatConstants.STATUS_OK);
        message.setAnswer(answer);
        message.setOnTopic(true);
        message.setFromCache(true);
        message.setTokensIn(0);
        message.setTokensOut(0);
        message.setTokensThinking(0);
        return message;
    }

    ChatMessage answered(CurrentUser caller, ChatSendInput input, ChatAiAnswer answer) {
        ChatMessage message = withUsage(base(caller, input, ChatConstants.STATUS_OK), answer.usage());
        message.setAnswer(answer.markdown());
        message.setOnTopic(true);
        message.setModel(answer.model());
        return message;
    }

    /** The model judged the question off topic; the student sees the fixed refusal, not the model's text. */
    ChatMessage refused(CurrentUser caller, ChatSendInput input, ChatAiAnswer answer, String refusal) {
        ChatMessage message = withUsage(base(caller, input, ChatConstants.STATUS_REFUSED), answer.usage());
        message.setAnswer(refusal);
        message.setOnTopic(false);
        message.setModel(answer.model());
        return message;
    }

    /** Empty answer and no verdict: the row only records that a paid call produced nothing usable. */
    ChatMessage failed(CurrentUser caller, ChatSendInput input, ChatAiUsage usage) {
        ChatMessage message = withUsage(base(caller, input, ChatConstants.STATUS_FAILED), usage);
        message.setAnswer("");
        message.setModel(properties.getModel());
        return message;
    }

    private static ChatMessage withUsage(ChatMessage message, ChatAiUsage usage) {
        message.setTokensIn(usage.tokensIn());
        message.setTokensOut(usage.tokensOut());
        message.setTokensThinking(usage.tokensThinking());
        message.setFinishReason(usage.finishReason());
        return message;
    }

    private static ChatMessage base(CurrentUser caller, ChatSendInput input, String status) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(input.sessionId());
        message.setStudentId(caller.userId());
        message.setTenantId(caller.tenantId());
        message.setQuestion(input.message());
        message.setTaskType(input.taskType());
        message.setLanguage(input.language());
        message.setStatus(status);
        message.setPromptVersion(ChatPrompts.VERSION);
        return message;
    }
}
