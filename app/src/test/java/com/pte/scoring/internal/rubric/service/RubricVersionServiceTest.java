package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricActivationAudit;
import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.repository.ScoringRubricActivationAuditRepository;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CATALOG;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CODEC;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.assertRubricVersionError;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.defaultsJson;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Draft -> submit -> activate / reject, the retire-then-activate order, the audit row and immutability. */
class RubricVersionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
    private static final UUID APPROVER = UUID.randomUUID();
    private static final UUID PREVIOUS_ACTIVE = UUID.randomUUID();

    private final ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
    private final ScoringRubricActivationAuditRepository audits = mock(ScoringRubricActivationAuditRepository.class);
    private RubricVersionService service;

    @BeforeEach
    void setUp() {
        service = new RubricVersionService(versions, audits, new RubricParametersValidator(CATALOG, CODEC),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(versions.save(any(ScoringRubricVersion.class))).thenAnswer(call -> call.getArgument(0));
        when(versions.saveAndFlush(any(ScoringRubricVersion.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createDraftStoresTheValidatedParametersAsTheNextVersionNumber() {
        UUID author = UUID.randomUUID();
        when(versions.findMaxVersionNo()).thenReturn(3);

        ScoringRubricVersion draft = service.createDraft(defaultsJson(), RubricConstants.CATALOG_VERSION, author);

        assertThat(draft.getStatus()).isEqualTo(RubricVersionStatus.DRAFT);
        assertThat(draft.getVersionNo()).isEqualTo(4);
        assertThat(draft.getParameters()).isEqualTo(defaultsJson());
        assertThat(draft.getAuthorUserPublicId()).isEqualTo(author);
        verify(versions).saveAndFlush(draft);
    }

    /** QUAL-003: two drafts racing for the same version number — the loser is a 409, not a 500. */
    @Test
    void aConcurrentDraftWithTheSameVersionNumberIsAConflict() {
        when(versions.findMaxVersionNo()).thenReturn(3);
        when(versions.saveAndFlush(any(ScoringRubricVersion.class)))
                .thenThrow(new DataIntegrityViolationException("ux_scoring_rubric_versions_version_no"));

        assertRubricVersionError(() -> service.createDraft(defaultsJson(), RubricConstants.CATALOG_VERSION,
                UUID.randomUUID()), HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_CONCURRENT_CHANGE);
    }

    /** QUAL-003: another activation committed first — the single-ACTIVE index refuses this one with a 409. */
    @Test
    void aConcurrentActivationIsAConflictAndWritesNoAudit() {
        ScoringRubricVersion candidate = stored(RubricVersionStatus.PENDING);
        when(versions.saveAndFlush(candidate))
                .thenThrow(new DataIntegrityViolationException("ux_scoring_rubric_versions_single_active"));

        assertRubricVersionError(() -> service.activate(candidate.getPublicId(), APPROVER, "diff"),
                HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_CONCURRENT_CHANGE);
        verifyNoInteractions(audits);
    }

    @Test
    void createDraftWithInvalidParametersStoresNothing() {
        assertRubricVersionError(() -> service.createDraft("{}", RubricConstants.CATALOG_VERSION, UUID.randomUUID()),
                HttpStatus.BAD_REQUEST, RubricConstants.RUBRIC_AZURE_MAPPING_VERSION_INVALID);
        verify(versions, never()).save(any());
    }

    @Test
    void submitMovesADraftToPending() {
        ScoringRubricVersion draft = stored(RubricVersionStatus.DRAFT);

        service.submit(draft.getPublicId());

        assertThat(draft.getStatus()).isEqualTo(RubricVersionStatus.PENDING);
        assertThat(draft.getSubmittedAt()).isEqualTo(NOW);
    }

    @Test
    void submitRejectsAVersionThatIsNotADraft() {
        ScoringRubricVersion pending = stored(RubricVersionStatus.PENDING);

        assertRubricVersionError(() -> service.submit(pending.getPublicId()), HttpStatus.CONFLICT,
                RubricConstants.RUBRIC_VERSION_STATUS_INVALID);
    }

    @Test
    void activationKeepsTheApproversReasonInTheAuditRowAndTreatsABlankOneAsNone() {
        ScoringRubricVersion first = stored(RubricVersionStatus.PENDING);
        service.activate(first.getPublicId(), APPROVER, "CONTENT 1 -> 2", "  Content matters more for read aloud.  ");
        ScoringRubricVersion second = stored(RubricVersionStatus.PENDING);
        service.activate(second.getPublicId(), APPROVER, "CONTENT 2 -> 3", "   ");

        org.mockito.ArgumentCaptor<ScoringRubricActivationAudit> saved =
                org.mockito.ArgumentCaptor.forClass(ScoringRubricActivationAudit.class);
        verify(audits, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getReason()).isEqualTo("Content matters more for read aloud.");
        assertThat(saved.getAllValues().get(1).getReason()).isNull();
    }

    @Test
    void activateRetiresTheOldActiveAndFlushesBeforeTheNewOneBecomesActive() {
        ScoringRubricVersion candidate = stored(RubricVersionStatus.PENDING);
        when(versions.findPublicIdByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(PREVIOUS_ACTIVE));
        List<RubricVersionStatus> statusWhenRetiring = new ArrayList<>();
        when(versions.retireActive(NOW)).thenAnswer(call -> {
            statusWhenRetiring.add(candidate.getStatus());
            return 1;
        });

        service.activate(candidate.getPublicId(), APPROVER, "CONTENT 6 -> 5");

        InOrder order = inOrder(versions, audits);
        order.verify(versions).retireActive(NOW);
        order.verify(versions).flush();
        order.verify(versions).saveAndFlush(candidate);
        order.verify(audits).save(any(ScoringRubricActivationAudit.class));
        assertThat(statusWhenRetiring).containsExactly(RubricVersionStatus.PENDING);
        assertThat(candidate.getStatus()).isEqualTo(RubricVersionStatus.ACTIVE);
        assertThat(candidate.getApproverUserPublicId()).isEqualTo(APPROVER);
        assertThat(candidate.getActivatedAt()).isEqualTo(NOW);
        assertThat(candidate.getDecidedAt()).isEqualTo(NOW);
    }

    @Test
    void activateWritesExactlyOneAuditRow() {
        ScoringRubricVersion candidate = stored(RubricVersionStatus.PENDING);
        when(versions.findPublicIdByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(PREVIOUS_ACTIVE));

        service.activate(candidate.getPublicId(), APPROVER, "CONTENT 6 -> 5");

        ArgumentCaptor<ScoringRubricActivationAudit> captor = ArgumentCaptor.forClass(ScoringRubricActivationAudit.class);
        verify(audits).save(captor.capture());
        ScoringRubricActivationAudit audit = captor.getValue();
        assertThat(audit.getVersionPublicId()).isEqualTo(candidate.getPublicId());
        assertThat(audit.getPreviousActivePublicId()).isEqualTo(PREVIOUS_ACTIVE);
        assertThat(audit.getActorUserPublicId()).isEqualTo(APPROVER);
        assertThat(audit.getActivatedAt()).isEqualTo(NOW);
        assertThat(audit.getDiffSummary()).isEqualTo("CONTENT 6 -> 5");
    }

    @Test
    void aDraftCanBeActivatedDirectlyAndTheFirstActivationHasNoPrevious() {
        ScoringRubricVersion draft = stored(RubricVersionStatus.DRAFT);
        when(versions.findPublicIdByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.empty());

        service.activate(draft.getPublicId(), APPROVER, null);

        ArgumentCaptor<ScoringRubricActivationAudit> captor = ArgumentCaptor.forClass(ScoringRubricActivationAudit.class);
        verify(audits).save(captor.capture());
        assertThat(captor.getValue().getPreviousActivePublicId()).isNull();
        assertThat(draft.getStatus()).isEqualTo(RubricVersionStatus.ACTIVE);
    }

    /** A draft edited after creation (or validated against an older catalog) must not become the platform rubric. */
    @Test
    void activateRevalidatesTheCandidatesParametersBeforeRetiringTheActiveVersion() {
        ScoringRubricVersion broken = version(RubricVersionStatus.PENDING, "{}");
        when(versions.findByPublicId(broken.getPublicId())).thenReturn(Optional.of(broken));

        assertRubricVersionError(() -> service.activate(broken.getPublicId(), APPROVER, null),
                HttpStatus.BAD_REQUEST, RubricConstants.RUBRIC_AZURE_MAPPING_VERSION_INVALID);

        assertThat(broken.getStatus()).isEqualTo(RubricVersionStatus.PENDING);
        verify(versions, never()).retireActive(any());
        verify(versions, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test
    void activateRejectsACandidateWhoseScalesNoLongerMatchTheCatalog() {
        String drifted = RubricVersionFixtures.editTask("DESCRIBE_IMAGE", task -> RubricVersionFixtures
                .child(task, RubricConstants.PARAM_TRAIT_SCALES).put("CONTENT", 5));
        ScoringRubricVersion candidate = version(RubricVersionStatus.DRAFT, drifted);
        when(versions.findByPublicId(candidate.getPublicId())).thenReturn(Optional.of(candidate));

        assertRubricVersionError(() -> service.activate(candidate.getPublicId(), APPROVER, null),
                HttpStatus.BAD_REQUEST, RubricConstants.RUBRIC_PARAMETERS_STRUCTURE_MISMATCH);
        verify(versions, never()).retireActive(any());
    }

    @ParameterizedTest
    @EnumSource(value = RubricVersionStatus.class, names = {"ACTIVE", "RETIRED"})
    void activeAndRetiredVersionsAreImmutable(RubricVersionStatus status) {
        ScoringRubricVersion frozen = stored(status);

        assertRubricVersionError(() -> service.activate(frozen.getPublicId(), APPROVER, null), HttpStatus.CONFLICT,
                RubricConstants.RUBRIC_VERSION_IMMUTABLE);
        assertRubricVersionError(() -> service.submit(frozen.getPublicId()), HttpStatus.CONFLICT,
                RubricConstants.RUBRIC_VERSION_IMMUTABLE);
        assertRubricVersionError(() -> service.reject(frozen.getPublicId(), APPROVER, "reason"),
                HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_IMMUTABLE);
        assertThat(frozen.getStatus()).isEqualTo(status);
        verify(versions, never()).retireActive(any());
        verify(versions, never()).save(any());
        verifyNoInteractions(audits);
    }

    @Test
    void aRejectedVersionCannotBeActivated() {
        ScoringRubricVersion rejected = stored(RubricVersionStatus.REJECTED);

        assertRubricVersionError(() -> service.activate(rejected.getPublicId(), APPROVER, null), HttpStatus.CONFLICT,
                RubricConstants.RUBRIC_VERSION_STATUS_INVALID);
        verify(versions, never()).retireActive(any());
    }

    @Test
    void rejectRecordsTheReasonAndTheDecider() {
        ScoringRubricVersion pending = stored(RubricVersionStatus.PENDING);
        String reason = "x".repeat(RubricConstants.MAX_REJECTION_REASON_LENGTH);

        service.reject(pending.getPublicId(), APPROVER, reason);

        assertThat(pending.getStatus()).isEqualTo(RubricVersionStatus.REJECTED);
        assertThat(pending.getRejectionReason()).isEqualTo(reason);
        assertThat(pending.getApproverUserPublicId()).isEqualTo(APPROVER);
        assertThat(pending.getDecidedAt()).isEqualTo(NOW);
        assertThat(pending.getActivatedAt()).isNull();
    }

    @Test
    void rejectNeedsANonBlankReasonOfAtMost1000Characters() {
        ScoringRubricVersion pending = stored(RubricVersionStatus.PENDING);
        for (String reason : new String[]{null, "", "   ", "x".repeat(RubricConstants.MAX_REJECTION_REASON_LENGTH + 1)}) {
            assertRubricVersionError(() -> service.reject(pending.getPublicId(), APPROVER, reason),
                    HttpStatus.BAD_REQUEST, RubricConstants.RUBRIC_REJECTION_REASON_INVALID);
        }
        assertThat(pending.getStatus()).isEqualTo(RubricVersionStatus.PENDING);
    }

    @Test
    void onlyAPendingVersionCanBeRejected() {
        ScoringRubricVersion draft = stored(RubricVersionStatus.DRAFT);

        assertRubricVersionError(() -> service.reject(draft.getPublicId(), APPROVER, "reason"), HttpStatus.CONFLICT,
                RubricConstants.RUBRIC_VERSION_STATUS_INVALID);
    }

    @Test
    void anUnknownVersionIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(versions.findByPublicId(unknown)).thenReturn(Optional.empty());

        assertRubricVersionError(() -> service.submit(unknown), HttpStatus.NOT_FOUND,
                RubricConstants.RUBRIC_VERSION_NOT_FOUND);
    }

    private ScoringRubricVersion stored(RubricVersionStatus status) {
        ScoringRubricVersion version = version(status, defaultsJson());
        when(versions.findByPublicId(version.getPublicId())).thenReturn(Optional.of(version));
        return version;
    }
}
