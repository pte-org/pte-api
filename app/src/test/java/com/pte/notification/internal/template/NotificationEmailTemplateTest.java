package com.pte.notification.internal.template;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The exam notification cards: same frame as the account mails, values escaped, long content kept inside the card. */
class NotificationEmailTemplateTest {

    private static final String HOSTILE = "<script>alert(1)</script>";

    @Test
    void studentEnrolled_showsTheSessionInASelectableBlock() {
        String html = NotificationEmailTemplate.studentEnrolled("0b8f2c3e-6d4a", 2026);

        assertThat(html).contains("You&#39;re enrolled in an exam session", "0b8f2c3e-6d4a", "user-select:all",
                "src=\"cid:pte-logo\"", "© 2026 PTE Prep");
    }

    @Test
    void attemptPublished_listsEveryScore_andNeverShowsMissingDataAsZero() {
        String html = NotificationEmailTemplate.attemptPublished("Nguyen Van A", 72, 70, null, 68, 74, 2026);

        assertThat(html).contains("Hello Nguyen Van A,", "Your exam results have been published.", ">72<", ">70<",
                ">68<", ">74<", ">Not enough data<", "Please keep this email for your records.")
                .doesNotContain(">0<", "log in");
    }

    @Test
    void attemptPublished_withoutAName_greetsPlainly_andEscapesTheName() {
        assertThat(NotificationEmailTemplate.attemptPublished(null, 1, 1, 1, 1, 1, 2026)).contains(">Hello,<");
        assertThat(NotificationEmailTemplate.attemptPublished(HOSTILE, 1, 1, 1, 1, 1, 2026))
                .contains("&lt;script&gt;").doesNotContain("<script");
    }

    @Test
    void violationFlagged_showsTheDetailOnlyWhenThereIsOne() {
        String withDetail = NotificationEmailTemplate.violationFlagged("TAB_SWITCH", "att-1", "ses-1", "left tab", 2026);
        String without = NotificationEmailTemplate.violationFlagged("TAB_SWITCH", "att-1", "ses-1", null, 2026);

        assertThat(withDetail).contains("Violation flagged", "TAB_SWITCH", "left tab", "att-1", "ses-1");
        assertThat(without).contains("TAB_SWITCH", "att-1").doesNotContain(">Detail<");
    }

    @Test
    void violationFlagged_escapesWhatAProctorTyped() {
        String html = NotificationEmailTemplate.violationFlagged("OTHER", "a", "s", HOSTILE, 2026);

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script");
    }

    @Test
    void sessionCancelled_showsTheNameAndTime() {
        String html = NotificationEmailTemplate.sessionCancelled("Mock exam", "2026-10-29T03:00:00Z", 2026);

        assertThat(html).contains("Exam session cancelled", "Mock exam", "2026-10-29T03:00:00Z");
    }

    @Test
    void enrollmentConflict_listsEachSkippedStudent_withLongNamesWrapped() {
        String longName = "Very long session name ".repeat(30);
        String html = NotificationEmailTemplate.enrollmentConflict(longName, List.of("s1 -> A", "s2 -> " + HOSTILE),
                2026);

        assertThat(html).contains("Students skipped", "s1 -&gt; A<br>", "&lt;script&gt;", "overflow-wrap:anywhere")
                .doesNotContain("<script");
    }

    @Test
    void everyCard_isSelfContained() {
        List<String> cards = List.of(
                NotificationEmailTemplate.studentEnrolled("s", 2026),
                NotificationEmailTemplate.attemptPublished("n", 1, 2, 3, 4, 5, 2026),
                NotificationEmailTemplate.violationFlagged("OTHER", "a", "s", "d", 2026),
                NotificationEmailTemplate.sessionCancelled("n", "t", 2026),
                NotificationEmailTemplate.enrollmentConflict("n", List.of("x"), 2026));

        assertThat(cards).allSatisfy(html -> assertThat(html)
                .contains("max-width:520px", "src=\"cid:pte-logo\"")
                .doesNotContain("<script", "<link", "@import", "url(", "src=\"http", "<iframe", "<form"));
    }
}
