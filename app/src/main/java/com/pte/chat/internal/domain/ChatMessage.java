package com.pte.chat.internal.domain;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One question and its outcome. {@code status} is OK, REFUSED or FAILED; FAILED keeps an empty
 * {@code answer} plus finish reason and token counts so provider cost stays visible after a refund.
 */
@Entity
@Table(name = "chat_message")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = ChatConstants.MAX_QUESTION_CHARS)
    private String question;

    @Column(nullable = false, length = ChatConstants.MAX_ANSWER_CHARS)
    private String answer = "";

    @Column(length = 64)
    private String taskType;

    @Column(length = 16)
    private String language;

    private Integer tokensIn;

    private Integer tokensOut;

    private Integer tokensThinking;

    @Column(length = 32)
    private String finishReason;

    @Column(nullable = false)
    private boolean fromCache;

    /** Null when no verdict exists, e.g. the provider failed before answering. */
    private Boolean onTopic;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 64)
    private String model;

    @Column(length = 32)
    private String promptVersion;
}
