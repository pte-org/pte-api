package com.pte.overview.internal.service;

import com.pte.itembank.ItembankOverviewService;
import com.pte.itembank.ItembankOverviewService.QuestionBankCounts;
import com.pte.overview.internal.dto.response.AdminSectionData.QuestionBank;
import org.springframework.stereotype.Component;

/**
 * Payload builder for the question bank admin section (content health). Visible to every platform
 * caller; the check happens in the caller, before this runs. Counts only: no question text, answer
 * or author data is read.
 */
@Component
public class AdminQuestionBankSections {

    private final ItembankOverviewService itembank;

    public AdminQuestionBankSections(ItembankOverviewService itembank) {
        this.itembank = itembank;
    }

    public QuestionBank questionBank(DashboardWindow w) {
        QuestionBankCounts counts = itembank.counts();
        return new QuestionBank(counts.total(), counts.byStatus(), counts.bySection(), counts.byPool(),
                counts.approvedByPool(),
                TrendCalculator.of(itembank.countCreatedBetween(w.start(), w.end()),
                        itembank.countCreatedBetween(w.previousStart(), w.start())));
    }
}
