package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.rubric.workflow.RubricWorkflowFixture;
import com.pte.scoring.internal.rubric.workflow.dto.*;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.*;
import static org.assertj.core.api.Assertions.*;

class RubricVersionCommandServiceTest {
    private final RubricWorkflowFixture f = new RubricWorkflowFixture();
    private final CurrentUser staff = f.caller("ACADEMIC_STAFF");

    @ParameterizedTest @ValueSource(strings = {"ACADEMIC_STAFF", "PLATFORM_AUTHOR", "ACADEMIC_MANAGER", "PLATFORM_ADMIN"})
    void authorsCreateFullDraftsStampedWithTheCurrentCatalog(String role) {
        var caller = f.caller(role);
        var result = f.commands.createDraft(new RubricWriteRequest(JSON.readTree(seedJson()), null), caller);
        var stored = f.rows.get(result.publicId());
        assertThat(stored.getAuthorUserPublicId()).isEqualTo(caller.userId());
        assertThat(stored.getCatalogVersion()).isEqualTo(1);
        assertThat(CODEC.decode(stored.getParameters()).taskTypes()).hasSize(22);
        assertThat(result.status()).isEqualTo("DRAFT");
    }

    @ParameterizedTest @EnumSource(RubricVersionStatus.class)
    void onlyDraftsAndRejectedVersionsCanBeEdited(RubricVersionStatus status) {
        var row = f.add(status);
        var request = new RubricWriteRequest(JSON.readTree(seedJson()), row.getLockVersion());
        if (status == RubricVersionStatus.DRAFT || status == RubricVersionStatus.REJECTED) {
            assertThat(f.commands.updateDraft(row.getPublicId(), request, staff).status()).isEqualTo("DRAFT");
        } else assertThatThrownBy(() -> f.commands.updateDraft(row.getPublicId(), request, staff))
                .hasMessage(status == RubricVersionStatus.PENDING ? "RUBRIC_VERSION_STATUS_INVALID" : "RUBRIC_VERSION_IMMUTABLE");
    }

    @Test void editsRequireTheExpectedOptimisticVersion() {
        var row = f.add(RubricVersionStatus.DRAFT);
        assertThatThrownBy(() -> f.commands.updateDraft(row.getPublicId(),
                new RubricWriteRequest(JSON.readTree(seedJson()), 99L), staff)).hasMessage("RUBRIC_VERSION_CONCURRENT_CHANGE");
        assertThatThrownBy(() -> f.commands.updateDraft(row.getPublicId(),
                new RubricWriteRequest(JSON.readTree(seedJson()), null), staff)).hasMessage("RUBRIC_VERSION_CONCURRENT_CHANGE");
    }

    @ParameterizedTest @EnumSource(RubricVersionStatus.class)
    void submitAndDiscardAreDraftOnly(RubricVersionStatus status) {
        for (var action : List.of(RubricStatusRequest.Action.SUBMIT, RubricStatusRequest.Action.DISCARD)) {
            var row = f.add(status);
            var request = new RubricStatusRequest(action, null, null, null);
            if (status == RubricVersionStatus.DRAFT) {
                var response = f.commands.changeStatus(row.getPublicId(), request, staff);
                assertThat(action == RubricStatusRequest.Action.DISCARD ? row.isDeleted() : response.status().equals("PENDING")).isTrue();
            } else assertThatThrownBy(() -> f.commands.changeStatus(row.getPublicId(), request, staff)).isInstanceOf(RuntimeException.class);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "TOO_LONG"})
    void rejectionNeedsAReason(String reason) {
        var row = f.add(RubricVersionStatus.PENDING);
        var request = new RubricStatusRequest(RubricStatusRequest.Action.REJECT, reason.equals("TOO_LONG") ? "x".repeat(1001) : reason, null, null);
        assertThatThrownBy(() -> f.commands.changeStatus(row.getPublicId(), request, f.caller("ACADEMIC_MANAGER")))
                .hasMessage("RUBRIC_REJECTION_REASON_INVALID");
    }

    @ParameterizedTest @ValueSource(strings = {"ACADEMIC_STAFF", "PLATFORM_AUTHOR", "HOST_ADMIN", "EXAMINER", "PLATFORM_MANAGER"})
    void staffAndTenantRolesCannotReject(String role) {
        var row = f.add(RubricVersionStatus.PENDING);
        assertThatThrownBy(() -> f.commands.changeStatus(row.getPublicId(),
                new RubricStatusRequest(RubricStatusRequest.Action.REJECT, "Revise weights.", null, null), f.caller(role)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void oversizedParametersNeverReachPersistenceOnCreateOrUpdate() {
        var row = f.add(RubricVersionStatus.DRAFT);
        var oversized = JSON.createObjectNode().put("unknown", "x".repeat(70000));
        int before = f.rows.size();
        assertThatThrownBy(() -> f.commands.createDraft(new RubricWriteRequest(oversized, null), staff))
                .hasMessage("RUBRIC_PARAMETERS_TOO_LARGE");
        assertThatThrownBy(() -> f.commands.updateDraft(row.getPublicId(), new RubricWriteRequest(oversized, row.getLockVersion()), staff))
                .hasMessage("RUBRIC_PARAMETERS_TOO_LARGE");
        assertThat(f.rows).hasSize(before);
        assertThat(row.getParameters()).isEqualTo(seedJson());
    }
}
