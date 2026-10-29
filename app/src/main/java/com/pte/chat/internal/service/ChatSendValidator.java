package com.pte.chat.internal.service;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.constant.ChatTaskTypes;
import com.pte.chat.internal.exception.ChatInvalidRequestException;

import java.util.Locale;

/**
 * Checks everything a student controls before any lock, charge or provider call. The details in the
 * exceptions name the field, never the value: the value may be a student's private text.
 */
final class ChatSendValidator {

    private final ChatAiProperties properties;

    ChatSendValidator(ChatAiProperties properties) {
        this.properties = properties;
    }

    ChatSendInput validate(ChatSendCommand command) {
        if (command.sessionId() == null || command.sessionId().version() != 4) {
            throw new ChatInvalidRequestException(ChatConstants.DETAIL_SESSION_ID_INVALID);
        }
        return new ChatSendInput(command.sessionId(), message(command.message()), taskType(command.taskType()),
                language(command.language()));
    }

    private String message(String raw) {
        String message = raw == null ? "" : raw.strip();
        if (message.isEmpty()) {
            throw new ChatInvalidRequestException(ChatConstants.DETAIL_MESSAGE_EMPTY);
        }
        if (message.length() > properties.getMaxInputChars()) {
            throw new ChatInvalidRequestException(ChatConstants.DETAIL_MESSAGE_TOO_LONG);
        }
        // A NUL cannot be stored in Postgres text: it would pass here, spend a provider call, then fail the insert.
        if (message.chars().anyMatch(ChatSendValidator::isUnsupportedControl)) {
            throw new ChatInvalidRequestException(ChatConstants.DETAIL_MESSAGE_UNSUPPORTED_CHARS);
        }
        return message;
    }

    private static boolean isUnsupportedControl(int character) {
        return Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t';
    }

    private static String taskType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!ChatTaskTypes.isAllowed(raw)) {
            throw new ChatInvalidRequestException(ChatConstants.DETAIL_TASK_TYPE_UNSUPPORTED);
        }
        return raw;
    }

    private static String language(String raw) {
        String language = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (language.isEmpty()) {
            return ChatConstants.LANGUAGE_EN;
        }
        if (!ChatConstants.LANGUAGE_VI.equals(language) && !ChatConstants.LANGUAGE_EN.equals(language)) {
            throw new ChatInvalidRequestException(ChatConstants.DETAIL_LANGUAGE_UNSUPPORTED);
        }
        return language;
    }
}
