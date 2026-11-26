package com.pte.notification.internal.template;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.pte.notification.internal.template.EmailLayout.BODY;
import static com.pte.notification.internal.template.EmailLayout.BRAND;
import static com.pte.notification.internal.template.EmailLayout.callout;
import static com.pte.notification.internal.template.EmailLayout.escape;
import static com.pte.notification.internal.template.EmailLayout.heading;
import static com.pte.notification.internal.template.EmailLayout.page;
import static com.pte.notification.internal.template.EmailLayout.paragraph;
import static com.pte.notification.internal.template.EmailLayout.steps;
import static com.pte.notification.internal.template.EmailLayout.summary;

/**
 * The mail a student gets when they are enrolled in an exam. Students have no web page to look their exam up, so the
 * mail itself carries the schedule: the exam, when it opens and closes, and how to join. It does not carry the exam
 * code: the proctor gives that out on exam day, so only someone who is at the exam can start it. Any detail that is
 * missing is simply left out.
 */
public final class ExamEnrollmentEmailTemplate {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm", Locale.ENGLISH);
    private static final int SECONDS_PER_HOUR = 3600;
    private static final int SECONDS_PER_MINUTE = 60;

    private ExamEnrollmentEmailTemplate() {
    }

    public static RenderedEmail render(String fullName, String examName, String organizationName, Instant opensAt,
            Instant closesAt, ZoneId zone, int year) {
        boolean hasExam = notBlank(examName);
        boolean hasOrg = notBlank(organizationName);
        String opens = opensAt == null ? null : when(opensAt, zone);
        String closes = closesAt == null ? null : when(closesAt, zone);

        String title = "You're enrolled in an exam";
        String subject = "[" + BRAND + "] " + (hasExam ? "Enrolled: " + singleLine(examName) : title);
        boolean named = notBlank(fullName);

        List<String[]> rows = new ArrayList<>();
        List<String> textRows = new ArrayList<>();
        if (hasExam) {
            rows.add(new String[] {"Exam", examName, "text"});
            textRows.add("Exam: " + examName);
        }
        if (opens != null) {
            rows.add(new String[] {"Opens", opens, "text"});
            textRows.add("Opens: " + opens);
        }
        if (closes != null) {
            rows.add(new String[] {"Closes", closes, "text"});
            textRows.add("Closes: " + closes);
        }
        if (hasOrg) {
            rows.add(new String[] {"Organization", organizationName, "text"});
            textRows.add("Organization: " + organizationName);
        }

        String introHtml = (hasOrg ? "<strong>" + escape(organizationName) + "</strong> has" : "Your center has")
                + " enrolled you in an upcoming exam. The details are below.";
        String introText = (hasOrg ? organizationName + " has" : "Your center has")
                + " enrolled you in an upcoming exam. The details are below.";

        StringBuilder html = new StringBuilder(heading(title));
        html.append(paragraph(BODY, named ? "Hi " + escape(fullName.trim()) + "," : "Hi,"));
        html.append(paragraph(BODY, introHtml));
        if (!rows.isEmpty()) {
            html.append(summary(rows.toArray(new String[0][])));
        }
        html.append(paragraph(BODY, "<strong>How to join</strong>"));
        html.append(steps(new String[][] {
                {"Come to the exam", "at the time shown above."},
                {"Open the " + BRAND + " exam app", "on your device and sign in with your student username and"
                        + " password."},
                {"Enter the exam code", "your proctor gives you at the exam, then start when the exam opens."}}));
        html.append(callout("<strong>Be ready early.</strong> Sign in a few minutes before the exam opens and check"
                + " your microphone and headset. The exam closes at the time shown above, so start on time."));
        html.append(paragraph(BODY, "Have questions? Contact your center or administrator."));
        html.append(paragraph(BODY, "Warm regards,<br>The " + BRAND + " team"));

        StringBuilder text = new StringBuilder(BRAND).append("\n\n").append(title).append("\n\n")
                .append(named ? "Hi " + fullName.trim() + "," : "Hi,").append("\n\n").append(introText).append("\n\n");
        for (String row : textRows) {
            text.append(row).append('\n');
        }
        text.append("\nHow to join\n");
        text.append("1. Come to the exam at the time shown above.\n2. Open the ").append(BRAND)
                .append(" exam app on your device and sign in with your student username and password.\n")
                .append("3. Enter the exam code your proctor gives you at the exam, then start when the exam")
                .append(" opens.\n");
        text.append("\nBe ready early. Sign in a few minutes before the exam opens and check your microphone and")
                .append(" headset. The exam closes at the time shown above, so start on time.\n")
                .append("\nHave questions? Contact your center or administrator.\n")
                .append("\nWarm regards,\nThe ").append(BRAND).append(" team\n");

        return new RenderedEmail(subject, text.toString(), page(title, html.toString(), year));
    }

    /** {@code 26 Nov 2026, 13:05 (GMT+7)}: the time in the given zone, with the zone named so nobody has to guess. */
    static String when(Instant instant, ZoneId zone) {
        ZonedDateTime time = instant.atZone(zone);
        return WHEN.format(time) + " (" + gmtOffset(time) + ")";
    }

    private static String gmtOffset(ZonedDateTime time) {
        int total = time.getOffset().getTotalSeconds();
        if (total == 0) {
            return "GMT";
        }
        int absolute = Math.abs(total);
        int hours = absolute / SECONDS_PER_HOUR;
        int minutes = absolute % SECONDS_PER_HOUR / SECONDS_PER_MINUTE;
        return "GMT" + (total < 0 ? "-" : "+") + hours + (minutes == 0 ? "" : ":" + String.format("%02d", minutes));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** Header values must stay on one line, whatever a host typed into the exam name. */
    private static String singleLine(String value) {
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }
}
