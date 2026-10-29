package com.pte.chat.internal.service.consent;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.exception.ChatConsentRequiredException;
import com.pte.chat.internal.exception.ChatInvalidRequestException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/**
 * Records that a student accepted the chat privacy notice, and which version of it. Consent to an older version
 * does not count: the check compares with {@link ChatConstants#CONSENT_POLICY_VERSION}, so changing the text and
 * the constant asks everyone again. The row is only ever replaced, never merged, so it always names one version.
 */
@Service
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatConsentService implements ChatConsentCheck {

    private static final String SELECT_CURRENT = "SELECT consented_at FROM chat_consent "
            + "WHERE student_id = :s AND policy_version = :v";
    private static final String UPSERT = "INSERT INTO chat_consent (student_id, policy_version, consented_at, "
            + "age_confirmed) VALUES (:s, :v, :c, true) ON CONFLICT (student_id) DO UPDATE SET "
            + "policy_version = EXCLUDED.policy_version, consented_at = EXCLUDED.consented_at, "
            + "age_confirmed = EXCLUDED.age_confirmed";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public ChatConsentService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public ChatConsentStatus status(UUID studentId) {
        return jdbc.query(SELECT_CURRENT, params(studentId), (rs, n) -> rs.getTimestamp(1).toInstant()).stream()
                .findFirst()
                .map(at -> new ChatConsentStatus(ChatConstants.CONSENT_POLICY_VERSION, true, at))
                .orElseGet(() -> new ChatConsentStatus(ChatConstants.CONSENT_POLICY_VERSION, false, null));
    }

    @Override
    public void require(UUID studentId) {
        if (!status(studentId).consented()) {
            throw new ChatConsentRequiredException();
        }
    }

    public ChatConsentStatus give(UUID studentId, String policyVersion, boolean ageConfirmed) {
        if (!ChatConstants.CONSENT_POLICY_VERSION.equals(policyVersion)) {
            throw new ChatInvalidRequestException(ChatConstants.DETAIL_CONSENT_VERSION_MISMATCH);
        }
        if (!ageConfirmed) {
            throw new ChatInvalidRequestException(ChatConstants.DETAIL_AGE_NOT_CONFIRMED);
        }
        jdbc.update(UPSERT, params(studentId).addValue("c", Timestamp.from(clock.instant())));
        return status(studentId);
    }

    private static MapSqlParameterSource params(UUID studentId) {
        return new MapSqlParameterSource("s", studentId).addValue("v", ChatConstants.CONSENT_POLICY_VERSION);
    }
}
