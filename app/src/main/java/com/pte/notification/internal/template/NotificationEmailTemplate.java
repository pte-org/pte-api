package com.pte.notification.internal.template;

import com.pte.notification.internal.constant.NotificationConstants;

import java.util.List;

import static com.pte.notification.internal.template.EmailLayout.BODY;
import static com.pte.notification.internal.template.EmailLayout.MUTED;
import static com.pte.notification.internal.template.EmailLayout.escape;
import static com.pte.notification.internal.template.EmailLayout.fields;
import static com.pte.notification.internal.template.EmailLayout.heading;
import static com.pte.notification.internal.template.EmailLayout.lines;
import static com.pte.notification.internal.template.EmailLayout.page;
import static com.pte.notification.internal.template.EmailLayout.paragraph;
import static com.pte.notification.internal.template.EmailLayout.scores;

/**
 * HTML cards for the exam notifications (enrolment, results, violations, cancellations, time conflicts), in the same
 * look as {@link BrandedEmailTemplate}. Each method returns only the HTML part: the listener keeps building the
 * plain-text twin and the subject, so what a text-only client shows is unchanged. Everything that comes from a
 * session, student or proctor is HTML-escaped.
 */
public final class NotificationEmailTemplate {

    private NotificationEmailTemplate() {
    }

    public static String studentEnrolled(String sessionId, int year) {
        String title = "You're enrolled in an exam session";
        return page(title, heading(title)
                + paragraph(BODY, "Log in to view your schedule.")
                + fields(new String[][] {{"Session", sessionId}}), year);
    }

    /** A null score is shown as "Not enough data", as in the plain-text mail. */
    public static String attemptPublished(String fullName, Integer overall, Integer listening, Integer reading,
            Integer speaking, Integer writing, int year) {
        boolean named = fullName != null && !fullName.isBlank();
        String title = named ? "Hello " + escape(fullName.trim()) + "," : "Hello,";
        String[][] rows = {
                {NotificationConstants.ATTEMPT_RESULT_OVERALL_LABEL, score(overall)},
                {NotificationConstants.ATTEMPT_RESULT_LISTENING_LABEL, score(listening)},
                {NotificationConstants.ATTEMPT_RESULT_READING_LABEL, score(reading)},
                {NotificationConstants.ATTEMPT_RESULT_SPEAKING_LABEL, score(speaking)},
                {NotificationConstants.ATTEMPT_RESULT_WRITING_LABEL, score(writing)}};
        return page(NotificationConstants.ATTEMPT_RESULT_SUBJECT, heading(title)
                + paragraph(BODY, escape(NotificationConstants.ATTEMPT_RESULT_INTRO))
                + scores(rows)
                + paragraph(MUTED, escape(NotificationConstants.ATTEMPT_RESULT_FOOTER)), year);
    }

    /** {@code detail} is optional free text typed by the proctor. */
    public static String violationFlagged(String violationType, String attemptId, String sessionId, String detail,
            int year) {
        String title = "Violation flagged";
        String[][] rows = detail == null || detail.isBlank()
                ? new String[][] {{"Type", violationType}, {"Attempt", attemptId}, {"Session", sessionId}}
                : new String[][] {{"Type", violationType}, {"Detail", detail}, {"Attempt", attemptId},
                        {"Session", sessionId}};
        return page(title + ": " + violationType, heading(title)
                + paragraph(BODY, "A proctor flagged an attempt in one of your exam sessions.")
                + fields(rows), year);
    }

    public static String sessionCancelled(String sessionLabel, String cancelledAt, int year) {
        String title = "Exam session cancelled";
        return page(title, heading(title)
                + paragraph(BODY, "This session was cancelled. Please review the exam workspace.")
                + fields(new String[][] {{"Session", sessionLabel}, {"Cancelled at", cancelledAt}}), year);
    }

    /** {@code conflicts} holds one line per skipped student: "student id -> overlapping session". */
    public static String enrollmentConflict(String sessionLabel, List<String> conflicts, int year) {
        String title = "Students skipped";
        return page(NotificationConstants.STUDENT_ENROLLMENT_CONFLICT_SUBJECT, heading(title)
                + paragraph(BODY, "These students were not added to <strong>" + escape(sessionLabel)
                        + "</strong> because they are enrolled in another overlapping exam:")
                + lines(conflicts), year);
    }

    private static String score(Integer value) {
        return value == null ? NotificationConstants.ATTEMPT_RESULT_INSUFFICIENT_DATA : String.valueOf(value);
    }
}
