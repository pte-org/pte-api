package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.enums.QuestionStatus;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class QuestionLifecyclePolicyTest {
    @Test void newDraftCanBeDeletedButNotArchived() {
        Question q = draft(false);
        assertThat(QuestionLifecyclePolicy.canDeleteDraft(q)).isTrue();
        assertThat(QuestionLifecyclePolicy.canArchive(q)).isFalse();
    }
    @Test void unknownLegacyAndRestoredDraftCannotBeDeleted() {
        assertThat(QuestionLifecyclePolicy.canDeleteDraft(draft(null))).isFalse();
        Question restored = draft(true);
        assertThat(QuestionLifecyclePolicy.canDeleteDraft(restored)).isFalse();
        assertThat(QuestionLifecyclePolicy.canArchive(restored)).isTrue();
    }
    @Test void pendingAndDeletedFailClosed() {
        Question q = draft(false);
        q.setStatus(QuestionStatus.PENDING_APPROVAL);
        assertThat(QuestionLifecyclePolicy.canDeleteDraft(q)).isFalse();
        assertThat(QuestionLifecyclePolicy.canArchive(q)).isFalse();
        q.setStatus(QuestionStatus.DRAFT);
        q.setDeleted(true);
        assertThat(QuestionLifecyclePolicy.canDeleteDraft(q)).isFalse();
    }
    @Test void unpublishedRevisionOfAPublishedQuestionCanBeDeleted() {
        Question revision = draft(false);
        revision.setSupersedesPublicId(UUID.randomUUID());
        revision.setRevisionNumber(2);
        assertThat(QuestionLifecyclePolicy.canDeleteDraft(revision)).isTrue();
        assertThat(QuestionLifecyclePolicy.canArchive(revision)).isFalse();
    }
    private Question draft(Boolean history) {
        Question q = new Question();
        q.setEverPublished(history);
        return q;
    }
}
