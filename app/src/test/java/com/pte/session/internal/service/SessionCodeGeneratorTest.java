package com.pte.session.internal.service;

import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionCodeGeneratorTest {

    private static final String CODE_PATTERN = "^[A-Z0-9]{1,8}-\\d{6}-[A-HJ-NP-Z2-9]{4}$";
    private static final Instant OPENS_AT = Instant.parse("2026-10-10T03:00:00Z");

    @Mock
    private TenancyService tenancyService;

    @Mock
    private ExamSessionRepository sessionRepository;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void generate_buildsTenantDateAndReadableSuffix() {
        when(tenancyService.getTenantCode(tenantId)).thenReturn("fpt");

        String code = new SessionCodeGenerator(tenancyService, sessionRepository).generate(tenantId, OPENS_AT);

        assertThat(code).matches(CODE_PATTERN).startsWith("FPT-261010-");
        assertThat(code.substring(code.length() - 4)).doesNotContain("0", "1", "I", "O");
    }

    @Test
    void generate_removesHyphensFromTenantCode() {
        when(tenancyService.getTenantCode(tenantId)).thenReturn("fpt-edu-vn");

        assertThat(generator().generate(tenantId, OPENS_AT)).isEqualTo("FPTEDUVN-261010-AAAA");
    }

    @Test
    void generate_truncatesLongTenantCodeToEightCharacters() {
        when(tenancyService.getTenantCode(tenantId)).thenReturn("hanoi-university-of-tech");

        assertThat(generator().generate(tenantId, OPENS_AT)).isEqualTo("HANOIUNI-261010-AAAA");
    }

    @Test
    void generate_usesVietnamCalendarDayForDatePart() {
        when(tenancyService.getTenantCode(tenantId)).thenReturn("fpt");

        assertThat(generator().generate(tenantId, Instant.parse("2026-10-09T17:30:00Z")))
                .startsWith("FPT-261010-");
        assertThat(generator().generate(tenantId, Instant.parse("2026-10-09T16:59:59Z")))
                .startsWith("FPT-261009-");
    }

    @Test
    void generate_retriesWhenCandidateAlreadyExists() {
        when(tenancyService.getTenantCode(tenantId)).thenReturn("fpt");
        when(sessionRepository.existsBySessionCode("FPT-261010-AAAA")).thenReturn(true);
        when(sessionRepository.existsBySessionCode("FPT-261010-BBBB")).thenReturn(true);
        when(sessionRepository.existsBySessionCode("FPT-261010-CCCC")).thenReturn(false);

        String code = new SessionCodeGenerator(tenancyService, sessionRepository, new SequenceRandom(0, 1, 2))
                .generate(tenantId, OPENS_AT);

        assertThat(code).isEqualTo("FPT-261010-CCCC");
    }

    @Test
    void generate_failsAfterFiveCollidingCandidates() {
        when(tenancyService.getTenantCode(tenantId)).thenReturn("fpt");
        when(sessionRepository.existsBySessionCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> generator().generate(tenantId, OPENS_AT))
                .isInstanceOf(IllegalStateException.class);
        verify(sessionRepository, times(5)).existsBySessionCode(anyString());
    }

    /** Every suffix is "AAAA" (alphabet index 0). */
    private SessionCodeGenerator generator() {
        return new SessionCodeGenerator(tenancyService, sessionRepository, new SequenceRandom(0));
    }

    /** Returns each value for one whole 4-char suffix, then moves to the next (last one repeats). */
    private static final class SequenceRandom extends SecureRandom {
        private final int[] perSuffix;
        private int calls;

        SequenceRandom(int... perSuffix) {
            this.perSuffix = perSuffix;
        }

        @Override
        public int nextInt(int bound) {
            int suffixIndex = Math.min(calls++ / 4, perSuffix.length - 1);
            return perSuffix[suffixIndex];
        }
    }
}
