package com.pte.itembank.internal.service.lifecycle;

import com.pte.itembank.domain.Question;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.audit.AuditStateChange;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * Records a draft edit with before/after snapshots. Published content needs no snapshot: a published question is
 * never edited in place, its history is the revision chain ({@code supersedesPublicId}).
 */
@Component
class QuestionUpdateAudit {

    private final AuditLogChangeWriter changeWriter;

    QuestionUpdateAudit(AuditLogChangeWriter changeWriter) {
        this.changeWriter = changeWriter;
    }

    void record(CurrentUser caller, Question question, QuestionAuditState before, String summary) {
        String text = summary.length() <= ItembankConstants.AUDIT_SUMMARY_MAX_LENGTH ? summary
                : summary.substring(0, ItembankConstants.AUDIT_SUMMARY_MAX_LENGTH);
        changeWriter.recordState(caller, new AuditStateChange(ItembankConstants.QUESTION_AGGREGATE,
                String.valueOf(question.getPublicId()), null, null, ItembankConstants.AUDIT_UPDATED, text,
                before, QuestionAuditState.of(question)));
    }
}
