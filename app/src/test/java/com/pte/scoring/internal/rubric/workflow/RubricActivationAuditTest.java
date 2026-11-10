package com.pte.scoring.internal.rubric.workflow;

import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.rubric.workflow.dto.RubricStatusRequest;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RubricActivationAuditTest {
    private final RubricWorkflowFixture f = new RubricWorkflowFixture();

    @ParameterizedTest @EnumSource(RubricVersionStatus.class)
    void onlyDraftAndPendingActivateAndEverySuccessHasOneAudit(RubricVersionStatus status) {
        var candidate = f.add(status);
        var manager = f.caller("ACADEMIC_MANAGER");
        var diff = f.diffs.read(candidate.getPublicId());
        var request = new RubricStatusRequest(RubricStatusRequest.Action.ACTIVATE, null, diff.expectedActiveVersionPublicId(), diff.diffDigest());
        if (status != RubricVersionStatus.DRAFT && status != RubricVersionStatus.PENDING) {
            assertThatThrownBy(() -> f.commands.activate(candidate.getPublicId(), request, manager)).isInstanceOf(RuntimeException.class);
            assertThat(f.auditRows).isEmpty();
            return;
        }
        f.commands.activate(candidate.getPublicId(), request, manager);
        assertThat(candidate.getApproverUserPublicId()).isEqualTo(manager.userId());
        assertThat(candidate.getActivatedAt()).isEqualTo(RubricWorkflowFixture.NOW);
        assertThat(f.auditRows).singleElement().satisfies(audit -> {
            assertThat(audit.getActorUserPublicId()).isEqualTo(manager.userId());
            assertThat(audit.getPreviousActivePublicId()).isEqualTo(f.active.getPublicId());
        });
        var order = inOrder(f.versions, f.audits);
        order.verify(f.versions).lockActive(RubricVersionStatus.ACTIVE);
        order.verify(f.versions).retireActive(RubricWorkflowFixture.NOW);
        order.verify(f.versions).flush();
        order.verify(f.versions).saveAndFlush(candidate);
        order.verify(f.audits).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"DIGEST", "ACTIVE", "LOCK", "ABSENT"})
    void staleOrMissingConfirmationNeverWrites(String kind) {
        var candidate = f.add(RubricVersionStatus.DRAFT);
        var diff = f.diffs.read(candidate.getPublicId());
        if (kind.equals("LOCK")) ReflectionTestUtils.setField(candidate, "lockVersion", 2L);
        var request = new RubricStatusRequest(RubricStatusRequest.Action.ACTIVATE, null,
                kind.equals("ACTIVE") ? java.util.UUID.randomUUID() : diff.expectedActiveVersionPublicId(),
                kind.equals("DIGEST") ? "wrong" : kind.equals("ABSENT") ? null : diff.diffDigest());
        assertThatThrownBy(() -> f.commands.activate(candidate.getPublicId(), request, f.caller("ACADEMIC_MANAGER")))
                .hasMessage("RUBRIC_DIFF_STALE");
        verify(f.versions, never()).retireActive(any());
        assertThat(f.auditRows).isEmpty();
    }

    @Test void managerCannotApproveOwnPendingButAdminCanAndDirectManagerEditsActivate() {
        for (String role : List.of("ACADEMIC_MANAGER", "PLATFORM_ADMIN")) {
            var candidate = f.add(RubricVersionStatus.PENDING);
            var caller = new CurrentUser(candidate.getAuthorUserPublicId(), null, List.of(role));
            var diff = f.diffs.read(candidate.getPublicId());
            var request = new RubricStatusRequest(RubricStatusRequest.Action.ACTIVATE, null, diff.expectedActiveVersionPublicId(), diff.diffDigest());
            if (role.equals("ACADEMIC_MANAGER")) assertThatThrownBy(() -> f.commands.activate(candidate.getPublicId(), request, caller))
                    .hasMessage("RUBRIC_SELF_APPROVAL_FORBIDDEN");
            else f.commands.activate(candidate.getPublicId(), request, caller);
        }
        var direct = f.add(RubricVersionStatus.DRAFT);
        var manager = new CurrentUser(direct.getAuthorUserPublicId(), null, List.of("ACADEMIC_MANAGER"));
        var diff = f.diffs.read(direct.getPublicId());
        f.commands.activate(direct.getPublicId(), new RubricStatusRequest(RubricStatusRequest.Action.ACTIVATE, null,
                diff.expectedActiveVersionPublicId(), diff.diffDigest()), manager);
        assertThat(direct.getStatus()).isEqualTo(RubricVersionStatus.ACTIVE);
    }

    @Test void transientLockFailureRetriesAtMostTwice() {
        when(f.versions.lockActive(RubricVersionStatus.ACTIVE)).thenThrow(new CannotAcquireLockException("synthetic"));
        var candidate = f.add(RubricVersionStatus.DRAFT);
        assertThatThrownBy(() -> f.commands.activate(candidate.getPublicId(), new RubricStatusRequest(
                RubricStatusRequest.Action.ACTIVATE, null, f.active.getPublicId(), "digest"), f.caller("ACADEMIC_MANAGER")))
                .hasMessage("RUBRIC_VERSION_CONCURRENT_CHANGE");
        verify(f.versions, times(2)).lockActive(RubricVersionStatus.ACTIVE);
        assertThat(f.auditRows).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"ACADEMIC_STAFF", "PLATFORM_AUTHOR", "HOST_ADMIN", "EXAMINER"})
    void confirmationDoesNotGiveActivationRightsToOtherRoles(String role) {
        var candidate = f.add(RubricVersionStatus.DRAFT);
        var diff = f.diffs.read(candidate.getPublicId());
        assertThatThrownBy(() -> f.commands.activate(candidate.getPublicId(), new RubricStatusRequest(
                RubricStatusRequest.Action.ACTIVATE, null, diff.expectedActiveVersionPublicId(), diff.diffDigest()), f.caller(role)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(f.versions, never()).retireActive(any());
        assertThat(f.auditRows).isEmpty();
    }
}
