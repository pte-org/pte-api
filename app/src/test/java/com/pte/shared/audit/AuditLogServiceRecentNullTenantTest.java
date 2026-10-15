package com.pte.shared.audit;

import com.pte.shared.audit.internal.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AuditLogService#recent} must reject a null tenant before touching the repository: a derived
 * {@code tenantId = null} query would otherwise match tenantless platform-level audit rows.
 */
class AuditLogServiceRecentNullTenantTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditLogService service = new AuditLogService(repository);

    @Test
    void nullTenantThrowsNullPointerExceptionAndNeverQueriesTheRepository() {
        assertThatNullPointerException().isThrownBy(() -> service.recent(null, 8));

        verifyNoInteractions(repository);
    }

    @Test
    void nullTenantIsRejectedForAnySize() {
        assertThatNullPointerException().isThrownBy(() -> service.recent(null, 0));
        assertThatNullPointerException().isThrownBy(() -> service.recent(null, 10_000));

        verifyNoInteractions(repository);
    }

    @Test
    void aRealTenantStillReachesTheRepositoryWithTheBoundedPage() {
        UUID tenant = UUID.randomUUID();
        when(repository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(any(), any())).thenReturn(java.util.List.of());

        service.recent(tenant, 8);

        verify(repository).findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenant,
                org.springframework.data.domain.PageRequest.of(0, 8));
    }
}
