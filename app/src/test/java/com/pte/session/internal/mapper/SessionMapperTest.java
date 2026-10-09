package com.pte.session.internal.mapper;

import com.pte.session.domain.ExamSession;
import com.pte.session.internal.dto.response.SessionResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionMapperTest {

    @Test
    void toResponse_mapsSessionCode() {
        ExamSession session = new ExamSession();
        session.setName("Mock");
        session.setSessionCode("FPT-261010-K7QM");
        session.setTenantId(UUID.randomUUID());
        session.setSubscriptionId(UUID.randomUUID());
        session.setOpensAt(Instant.parse("2026-10-10T01:00:00Z"));
        session.setClosesAt(Instant.parse("2026-10-10T03:00:00Z"));
        session.setCapacity(5);
        session.setExamMode(com.pte.session.domain.enums.ExamMode.OFFICIAL_EXAM);
        session.setPolicy(com.pte.session.domain.ExamPolicy.realExamDefault());

        SessionResponse response = SessionMapper.toResponse(session);

        assertThat(response.sessionCode()).isEqualTo("FPT-261010-K7QM");
        assertThat(response.name()).isEqualTo("Mock");
    }
}
