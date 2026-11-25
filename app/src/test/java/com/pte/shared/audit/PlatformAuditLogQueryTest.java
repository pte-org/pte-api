package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.dto.AuditLogDetailResponse;
import com.pte.shared.audit.internal.exception.AuditLogNotFoundException;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAuditLogQueryTest {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    @Mock
    private AuditLogRepository auditLogRepository;

    private PlatformAuditLogQuery query;

    @BeforeEach
    void setUp() {
        query = new PlatformAuditLogQuery(auditLogRepository);
    }

    @Test
    void listForwardsEveryFilterNormalized() {
        UUID actorId = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 11, 25);
        PageRequest pageable = PageRequest.of(0, 20, NEWEST_FIRST);
        AuditLog row = row("{\"a\":1}", null);
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        var result = query.list(new PlatformAuditFilter(" QUESTION ", "q-1", actorId, " Answer ", day, day), 0, 20);

        assertThat(result.data()).singleElement().satisfies(entry -> {
            assertThat(entry.hasStateChange()).isTrue();
            assertThat(entry.actorRole()).isEqualTo("PLATFORM_MANAGER");
        });
    }

    @Test
    void listWithoutFiltersPassesNullsAndClampsTheSize() {
        PageRequest pageable = PageRequest.of(0, 100, NEWEST_FIRST);
        when(auditLogRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(row(null, null)), pageable, 1));

        var result = query.list(new PlatformAuditFilter(" ", null, null, "", null, null), -3, 500);

        assertThat(result.data()).singleElement().satisfies(entry -> assertThat(entry.hasStateChange()).isFalse());
        verify(auditLogRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void detailReturnsSnapshotsAsJsonObjects() {
        AuditLog row = row("{\"prompt\":\"old\"}", "{\"prompt\":\"new\"}");
        when(auditLogRepository.findByPublicIdAndTenantIdIsNull(row.getPublicId())).thenReturn(Optional.of(row));

        AuditLogDetailResponse detail = query.detail(row.getPublicId());

        assertThat(detail.entry().publicId()).isEqualTo(row.getPublicId());
        assertThat(detail.beforeState().get("prompt").asString()).isEqualTo("old");
        assertThat(detail.afterState().get("prompt").asString()).isEqualTo("new");
    }

    @Test
    void detailOfATenantOrMissingRowIsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(auditLogRepository.findByPublicIdAndTenantIdIsNull(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.detail(publicId)).isInstanceOf(AuditLogNotFoundException.class);
    }

    private static AuditLog row(String before, String after) {
        AuditLog auditLog = new AuditLog();
        auditLog.setPublicId(UUID.randomUUID());
        auditLog.setActorUserId(UUID.randomUUID());
        auditLog.setActorRole("PLATFORM_MANAGER");
        auditLog.setAggregateType("QUESTION");
        auditLog.setAggregateId("q-1");
        auditLog.setAction("QUESTION_UPDATED");
        auditLog.setSummary("Updated");
        auditLog.setBeforeState(before);
        auditLog.setAfterState(after);
        return auditLog;
    }
}
