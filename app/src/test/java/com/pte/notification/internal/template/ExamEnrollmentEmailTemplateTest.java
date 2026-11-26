package com.pte.notification.internal.template;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ExamEnrollmentEmailTemplateTest {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant OPENS = Instant.parse("2026-11-26T06:05:00Z");
    private static final Instant CLOSES = Instant.parse("2026-11-26T09:00:00Z");

    private static RenderedEmail full() {
        return ExamEnrollmentEmailTemplate.render("Hoàng Đức", "Mock Test 1", "Ninh Lê Organization", OPENS, CLOSES,
                VIETNAM, 2026);
    }

    @Test
    void subjectNamesTheExam() {
        assertThat(full().subject()).isEqualTo("[PTE Prep] Enrolled: Mock Test 1");
    }

    @Test
    void carriesTheScheduleInTheMailItself() {
        RenderedEmail email = full();

        assertThat(email.html()).contains("Hi Hoàng Đức,", "Mock Test 1", "Ninh Lê Organization",
                "26 Nov 2026, 13:05 (GMT+7)", "26 Nov 2026, 16:00 (GMT+7)", "How to join",
                "Have questions? Contact your center or administrator.", "Warm regards");
        assertThat(email.text()).contains("Opens: 26 Nov 2026, 13:05 (GMT+7)", "Closes: 26 Nov 2026, 16:00 (GMT+7)");
    }

    @Test
    void neverGivesTheExamCode_theProctorDoesOnTheDay() {
        RenderedEmail email = full();

        assertThat(email.html()).contains("your proctor gives you at the exam").doesNotContain(">Exam code<");
        assertThat(email.text()).contains("exam code your proctor gives you").doesNotContain("Exam code:");
    }

    @Test
    void doesNotPointToASchedulePage() {
        RenderedEmail email = full();

        assertThat(email.html()).doesNotContain("Log in to view", "<a href=\"http");
        assertThat(email.text()).doesNotContain("Log in to view");
    }

    @Test
    void leavesOutWhatItDoesNotKnow() {
        RenderedEmail email = ExamEnrollmentEmailTemplate.render(null, null, null, null, null, VIETNAM, 2026);

        assertThat(email.subject()).isEqualTo("[PTE Prep] You're enrolled in an exam");
        assertThat(email.html()).contains("Hi,", "Your center has enrolled you")
                .doesNotContain(">Exam<", ">Opens<", ">Closes<", "null");
        assertThat(email.text()).doesNotContain("null");
    }

    @Test
    void escapesWhatAHostTyped() {
        RenderedEmail email = ExamEnrollmentEmailTemplate.render("<b>Eve</b>", "<script>x</script>\nSecond line",
                "<i>Org</i>", OPENS, CLOSES, VIETNAM, 2026);

        assertThat(email.html()).contains("&lt;b&gt;Eve&lt;/b&gt;", "&lt;script&gt;").doesNotContain("<script", "<b>Eve");
        assertThat(email.subject()).doesNotContain("\n");
    }

    @Test
    void namesTheZoneWhateverItIs() {
        assertThat(ExamEnrollmentEmailTemplate.when(OPENS, ZoneId.of("UTC"))).isEqualTo("26 Nov 2026, 06:05 (GMT)");
        assertThat(ExamEnrollmentEmailTemplate.when(OPENS, ZoneId.of("Asia/Kolkata")))
                .isEqualTo("26 Nov 2026, 11:35 (GMT+5:30)");
        assertThat(ExamEnrollmentEmailTemplate.when(OPENS, ZoneId.of("America/New_York")))
                .isEqualTo("26 Nov 2026, 01:05 (GMT-5)");
    }
}
