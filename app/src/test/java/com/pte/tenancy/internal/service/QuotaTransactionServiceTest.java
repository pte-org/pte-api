package com.pte.tenancy.internal.service;

import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.domain.QuotaTransaction;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.QuotaActionType;
import com.pte.tenancy.internal.exception.QuotaConflictException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.dto.request.GrantQuotaRequest;
import com.pte.tenancy.internal.dto.response.QuotaTransactionResponse;
import com.pte.tenancy.internal.repository.QuotaTransactionRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaTransactionServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private QuotaTransactionRepository quotaTransactionRepository;


    private QuotaTransactionService service;
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

    @BeforeEach
    void setUp() {
        service = new QuotaTransactionService(tenantRepository, quotaTransactionRepository);
    }

    private Tenant tenantWithPublicId(UUID publicId) {
        Tenant tenant = new Tenant();
        tenant.setPublicId(publicId);
        tenant.setName("Acme School");
        tenant.setOrganizationType("SCHOOL");
        tenant.setPackageName("starter");
        tenant.setStudentLimit(500);
        return tenant;
    }

    @Test
    void grant_addsAmountToCachedLimitAndWritesLedgerRow() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        when(tenantRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quotaTransactionRepository.save(any(QuotaTransaction.class))).thenAnswer(invocation -> {
            QuotaTransaction saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        QuotaTransactionResponse response = service.grant(tenantPublicId,
                new GrantQuotaRequest("professional", 200, "Upsell"), caller);

        assertThat(response.publicId()).isNotNull();
        assertThat(response.tenantPublicId()).isEqualTo(tenantPublicId);
        assertThat(response.packageName()).isEqualTo("professional");
        assertThat(response.amount()).isEqualTo(200);
        assertThat(response.actionType()).isEqualTo("GRANTED");
        assertThat(response.actorUserId()).isEqualTo(caller.userId());
        assertThat(response.note()).isEqualTo("Upsell");

        assertThat(tenant.getPackageName()).isEqualTo("professional");
        assertThat(tenant.getStudentLimit()).isEqualTo(700);

        ArgumentCaptor<QuotaTransaction> captor = ArgumentCaptor.forClass(QuotaTransaction.class);
        verify(quotaTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo(QuotaActionType.GRANTED);
        assertThat(captor.getValue().getTenant()).isSameAs(tenant);
    }

    @Test
    void grant_unknownTenant_throwsNotFound() {
        UUID tenantPublicId = UUID.randomUUID();
        when(tenantRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(tenantPublicId,
                new GrantQuotaRequest("professional", 200, null), caller))
                .isInstanceOf(TenantNotFoundException.class);
        verify(quotaTransactionRepository, never()).save(any());
    }

    @Test
    void grant_concurrentUpdateRacesTheVersionCheck_translatesToQuotaConflict() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        when(tenantRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.of(tenant));
        // Simulates the second of two racing grant requests: by the time this
        // caller's saveAndFlush() runs, the row's @Version column has already
        // moved out from under it (the first caller's grant committed first),
        // so Hibernate's version check fails the UPDATE and Spring translates
        // the resulting StaleObjectStateException into this exception.
        when(tenantRepository.saveAndFlush(any(Tenant.class)))
                .thenThrow(new OptimisticLockingFailureException("stale version"));

        assertThatThrownBy(() -> service.grant(tenantPublicId,
                new GrantQuotaRequest("professional", 200, null), caller))
                .isInstanceOf(QuotaConflictException.class);

        // The lost update must not silently proceed to write a ledger row for
        // an amount that was never actually applied to the tenant's cache.
        verify(quotaTransactionRepository, never()).save(any());
    }

    @Test
    void history_unknownTenant_throwsNotFound() {
        UUID tenantPublicId = UUID.randomUUID();
        when(tenantRepository.existsByPublicId(tenantPublicId)).thenReturn(false);

        assertThatThrownBy(() -> service.history(tenantPublicId, caller)).isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void history_returnsTransactionsMappedWithTenantPublicId() {
        UUID tenantPublicId = UUID.randomUUID();
        Tenant tenant = tenantWithPublicId(tenantPublicId);
        QuotaTransaction transaction = new QuotaTransaction();
        transaction.setPublicId(UUID.randomUUID());
        transaction.setTenant(tenant);
        transaction.setPackageName("starter");
        transaction.setAmount(500);
        transaction.setActionType(QuotaActionType.GRANTED);
        transaction.setActorUserId(caller.userId());
        when(tenantRepository.existsByPublicId(tenantPublicId)).thenReturn(true);
        when(quotaTransactionRepository.findByTenant_PublicIdOrderByCreatedAtDesc(tenantPublicId))
                .thenReturn(List.of(transaction));

        List<QuotaTransactionResponse> history = service.history(tenantPublicId, caller);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).tenantPublicId()).isEqualTo(tenantPublicId);
        assertThat(history.get(0).amount()).isEqualTo(500);
    }
}
