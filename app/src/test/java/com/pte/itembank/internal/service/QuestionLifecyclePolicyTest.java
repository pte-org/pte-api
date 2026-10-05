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
    @Test void pendingDeletedAndLinkedRevisionFailClosed() {
        Question q = draft(false);
        q.setStatus(QuestionStatus.PENDING_APPROVAL);
        assertThat(QuestionLifecyclePolicy.canDeleteDraft(q)).isFalse();
        assertThat(QuestionLifecyclePolicy.canArchive(q)).isFalse();
        q.setStatus(QuestionStatus.DRAFT);
        q.setSupersedesPublicId(UUID.randomUUID());
        assertThat(QuestionLifecyclePolicy.canDeleteDraft(q)).isFalse();
        q.setSupersedesPublicId(null);
        q.setDeleted(true);
        assertThat(QuestionLifecyclePolicy.canDeleteDraft(q)).isFalse();
    }
    private Question draft(Boolean history) {
        Question q = new Question();
        q.setEverPublished(history);
        return q;
    }
}
