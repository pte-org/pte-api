package com.pte.chat.internal.domain;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A stored on-topic answer, shared by every student that asks the same normalised question. */
@Entity
@Table(name = "chat_answer_cache")
@Getter
@Setter
@NoArgsConstructor
public class ChatAnswerCache extends BaseEntity {

    /** SHA-256 hex of question, language, task type and prompt version. */
    @Column(nullable = false, unique = true, length = 64)
    private String cacheKey;

    @Column(nullable = false, length = ChatConstants.MAX_ANSWER_CHARS)
    private String answer;

    @Column(nullable = false, length = 32)
    private String promptVersion;

    @Column(nullable = false, length = 64)
    private String model;

    /** Optional eviction time; null means the entry lives until the prompt version changes. */
    private Instant expiresAt;
}
