package com.pte.session.internal.repository;

import com.pte.session.domain.ExamSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ExamSessionRepositorySessionCodeTest {

    @Autowired
    private ExamSessionRepository repository;

    private ExamSession saved(UUID tenantId, String code) {
        ExamSession s = new ExamSession();
        s.setName("Session");
        s.setSessionCode(code);
        s.setTenantId(tenantId);
        s.setSubscriptionId(UUID.randomUUID());
        s.setLicenseKey("LIC");
        s.setOpensAt(Instant.parse("2026-10-10T01:00:00Z"));
        s.setClosesAt(Instant.parse("2026-10-10T03:00:00Z"));
        s.setCapacity(10);
        return repository.saveAndFlush(s);
    }

    @Test
    void findBySessionCodeAndTenantIdAndDeletedFalse_returnsSessionInSameTenant() {
        UUID tenant = UUID.randomUUID();
        ExamSession s = saved(tenant, "FPT-261010-K7QM");

        assertThat(repository.findBySessionCodeAndTenantIdAndDeletedFalse("FPT-261010-K7QM", tenant))
                .isPresent()
                .get().extracting(ExamSession::getPublicId).isEqualTo(s.getPublicId());
    }

    @Test
    void findBySessionCodeAndTenantIdAndDeletedFalse_excludesOtherTenant() {
        saved(UUID.randomUUID(), "FPT-261010-AAAA");

        assertThat(repository.findBySessionCodeAndTenantIdAndDeletedFalse("FPT-261010-AAAA", UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void findBySessionCodeAndTenantIdAndDeletedFalse_excludesSoftDeleted() {
        UUID tenant = UUID.randomUUID();
        ExamSession s = saved(tenant, "FPT-261010-BBBB");
        s.setDeleted(true);
        repository.saveAndFlush(s);

        assertThat(repository.findBySessionCodeAndTenantIdAndDeletedFalse("FPT-261010-BBBB", tenant)).isEmpty();
    }

    @Test
    void existsBySessionCode_isGlobalAndIncludesSoftDeleted() {
        ExamSession s = saved(UUID.randomUUID(), "FPT-261010-CCCC");
        s.setDeleted(true);
        repository.saveAndFlush(s);

        assertThat(repository.existsBySessionCode("FPT-261010-CCCC")).isTrue();
        assertThat(repository.existsBySessionCode("FPT-261010-ZZZZ")).isFalse();
    }

    @Test
    void sessionCode_isUniqueAcrossTenants() {
        saved(UUID.randomUUID(), "FPT-261010-DDDD");

        assertThatThrownBy(() -> saved(UUID.randomUUID(), "FPT-261010-DDDD"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sessionCode_isNotUpdatable() {
        UUID tenant = UUID.randomUUID();
        ExamSession s = saved(tenant, "FPT-261010-EEEE");

        s.setSessionCode("FPT-261010-FFFF");
        s.setName("Renamed");
        repository.saveAndFlush(s);

        assertThat(repository.findBySessionCodeAndTenantIdAndDeletedFalse("FPT-261010-EEEE", tenant)).isPresent();
        assertThat(repository.existsBySessionCode("FPT-261010-FFFF")).isFalse();
    }
}
