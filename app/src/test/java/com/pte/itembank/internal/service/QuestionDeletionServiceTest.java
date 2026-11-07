package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.QuestionStatus;
import com.pte.itembank.internal.exception.QuestionDeletionException;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.media.MediaService;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestionDeletionServiceTest {
    private final QuestionRepository repository = mock(QuestionRepository.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final MediaService media = mock(MediaService.class);
    private final QuestionDeletionService service = new QuestionDeletionService(repository, audit, media);
    private final CurrentUser author = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_AUTHOR"));

    private Question draft() {
        Question question = new Question();
        question.setPublicId(UUID.randomUUID());
        question.setRevisionGroupPublicId(UUID.randomUUID());
        question.setStatus(QuestionStatus.DRAFT);
        question.setEverPublished(false);
        when(repository.findByPublicIdForUpdate(question.getPublicId())).thenReturn(Optional.of(question));
        return question;
    }

    @Test void unusedDraftIsSoftDeletedAndAuditedOnlyOnce() {
        Question question = draft();
        service.deleteDraft(question.getPublicId(), author);
        service.deleteDraft(question.getPublicId(), author);
        assertThat(question.isDeleted()).isTrue();
        assertThat(question.isCurrent()).isFalse();
        verify(audit, times(1)).record(eq(author), anyString(), eq(question.getPublicId().toString()), anyString(), anyString());
        verify(repository, never()).delete(any());
    }

    @Test void unknownAndPublishedHistoryFailClosed() {
        for (Boolean provenance : new Boolean[] {null, true}) {
            Question question = draft();
            question.setEverPublished(provenance);
            assertThatThrownBy(() -> service.deleteDraft(question.getPublicId(), author)).isInstanceOf(QuestionDeletionException.class);
            assertThat(question.isDeleted()).isFalse();
        }
        verifyNoInteractions(audit);
    }

    @Test void pendingApprovedAndArchivedCannotBeDeleted() {
        for (QuestionStatus status : List.of(QuestionStatus.PENDING_APPROVAL, QuestionStatus.APPROVED, QuestionStatus.ARCHIVED)) {
            Question question = draft();
            question.setStatus(status);
            assertThatThrownBy(() -> service.deleteDraft(question.getPublicId(), author)).isInstanceOf(QuestionDeletionException.class);
        }
        verifyNoInteractions(audit);
    }

    @Test void draftAnotherRevisionWasOpenedFromBlocksDeletion() {
        Question question = draft();
        when(repository.existsBySupersedesPublicId(question.getPublicId())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteDraft(question.getPublicId(), author)).isInstanceOf(QuestionDeletionException.class);
        verifyNoInteractions(audit);
    }

    @Test void unpublishedRevisionLeavesItsPublishedSourceUntouched() {
        Question source = new Question();
        source.setPublicId(UUID.randomUUID());
        source.setStatus(QuestionStatus.APPROVED);
        source.setCurrent(true);
        Question revision = draft();
        revision.setSupersedesPublicId(source.getPublicId());
        revision.setRevisionGroupPublicId(source.getRevisionGroupPublicId());
        revision.setRevisionNumber(2);

        service.deleteDraft(revision.getPublicId(), author);

        assertThat(revision.isDeleted()).isTrue();
        assertThat(source.isDeleted()).isFalse();
        assertThat(source.isCurrent()).isTrue();
        verify(repository, never()).delete(any());
    }

    @Test void platformAuthorCannotDeleteTenantQuestionEvenWhenAlreadyDeleted() {
        Question question = draft();
        question.setTenantId(UUID.randomUUID());
        question.setDeleted(true);
        assertThatThrownBy(() -> service.deleteDraft(question.getPublicId(), author))
                .isInstanceOf(com.pte.itembank.internal.exception.QuestionNotFoundException.class);
        verifyNoInteractions(audit);
    }

    @Test void tenantAndUnprivilegedPlatformCannotDeleteEvenTombstone() {
        for (CurrentUser caller : List.of(new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("PLATFORM_ADMIN")),
                new CurrentUser(UUID.randomUUID(), null, List.of("STUDENT")))) {
            assertThatThrownBy(() -> service.deleteDraft(UUID.randomUUID(), caller)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(repository, audit);
    }

    /** Runs what the service registered for after the transaction commits, as Spring would. */
    private void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void deletingAnAiDraftRemovesItsGeneratedAudioAndImageAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            Question question = draft();
            question.setGenerationMeta("{}");
            UUID audio = UUID.randomUUID();
            UUID image = UUID.randomUUID();
            question.setAudioPromptRef(audio);
            question.setImagePromptRef(image);

            service.deleteDraft(question.getPublicId(), author);
            verify(media, never()).deleteGenerated(any(), any());
            commit();

            verify(media).deleteGenerated(audio, author);
            verify(media).deleteGenerated(image, author);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test void aHandWrittenDraftKeepsItsMedia() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            Question question = draft();
            question.setAudioPromptRef(UUID.randomUUID());

            service.deleteDraft(question.getPublicId(), author);
            commit();

            verifyNoInteractions(media);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test void mediaAnotherLiveQuestionStillUsesIsNotRemoved() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            Question question = draft();
            question.setGenerationMeta("{}");
            UUID audio = UUID.randomUUID();
            question.setAudioPromptRef(audio);
            when(repository.existsByAudioPromptRefAndDeletedFalseAndPublicIdNot(audio, question.getPublicId()))
                    .thenReturn(true);

            service.deleteDraft(question.getPublicId(), author);
            commit();

            verifyNoInteractions(media);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test void aFailureRemovingTheFileNeverUndoesTheDeletion() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            Question question = draft();
            question.setGenerationMeta("{}");
            question.setImagePromptRef(UUID.randomUUID());
            doThrow(new IllegalStateException("down")).when(media).deleteGenerated(any(), any());

            service.deleteDraft(question.getPublicId(), author);
            commit();

            assertThat(question.isDeleted()).isTrue();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
