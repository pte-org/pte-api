package com.pte.shared.idempotency;

import com.pte.shared.idempotency.domain.IdempotencyKey;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import com.pte.shared.idempotency.internal.exception.IdempotencyKeyInFlightException;
import com.pte.shared.idempotency.internal.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** A concurrent claim of the same key is decided by the unique constraint and must not surface as a 500. */
@ExtendWith(MockitoExtension.class)
class IdempotencyKeyRaceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    @InjectMocks
    private IdempotencyKeyService service;

    @Test
    void losingTheUniqueConstraintRaceIsReportedAsAConflictDomainError() {
        when(repository.findByScopeAndKey(any(), any(), any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(IdempotencyKey.class)))
                .thenThrow(new DataIntegrityViolationException("uq_idempotency_scope_key_org"));

        assertThatThrownBy(() -> service.begin(IdempotencyScope.ATTENDANCE, "k", UUID.randomUUID(), "h", Instant.now()))
                .isInstanceOfSatisfying(IdempotencyKeyInFlightException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
}
