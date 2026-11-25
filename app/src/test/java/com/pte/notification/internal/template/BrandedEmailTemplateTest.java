package com.pte.notification.internal.template;

import com.pte.identity.PasswordResetEmailRequestedEvent.Link;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The card e-mail: English only, a button, a plain link as fallback, and a plain-text
 * twin. Everything an account controls is escaped; nothing is loaded from the internet.
 */
class BrandedEmailTemplateTest {

    private static final Link ALICE = new Link("alice", "https://host.example/reset-password?token=aaa");
    private static final Link BOB = new Link("bob", "https://admin.example/reset-password?token=bbb");

    private static int occurrences(String text, String needle) {
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    // ---- password reset ----

    @Test
    void passwordReset_givesEveryAccountItsOwnButtonWithItsUsername() {
        RenderedEmail email = BrandedEmailTemplate.passwordReset(List.of(ALICE, BOB), 30, 2026);

        assertThat(email.html()).contains("href=\"https://host.example/reset-password?token=aaa\"",
                "href=\"https://admin.example/reset-password?token=bbb\"");
        assertThat(occurrences(email.html(), "<a href=\"https://")).isEqualTo(2);
        assertThat(email.html()).contains(">alice<", ">bob<");
    }

    @Test
    void passwordReset_withASingleLinkStillNamesTheAccountAndNeverAnOrganization() {
        RenderedEmail email = BrandedEmailTemplate.passwordReset(List.of(ALICE), 30, 2026);

        assertThat(email.html()).contains("Account:", ">alice<");
        assertThat(email.text()).contains("- alice: https://host.example/reset-password?token=aaa");
        assertThat(email.html().toLowerCase()).doesNotContain("organization", "organisation");
    }

    @Test
    void passwordReset_saysHowLongTheLinkWorksFromTheConfiguredValue() {
        RenderedEmail email = BrandedEmailTemplate.passwordReset(List.of(ALICE), 45, 2026);

        assertThat(email.html()).contains("45 minutes").doesNotContain("30 ");
        assertThat(email.text()).contains("45");
    }

    @Test
    void passwordReset_isEnglishAndTheSubjectIs() {
        RenderedEmail email = BrandedEmailTemplate.passwordReset(List.of(ALICE), 30, 2026);

        assertThat(email.html()).contains("Reset your password");
        assertThat(email.subject()).contains("Reset your password");
        assertThat(email.html()).contains("PTE Prep");
    }

    @Test
    void passwordReset_escapesEverythingTheAccountControls() {
        Link hostile = new Link("<b onmouseover=\"x\">&", "https://h.example/r?token=a&b=\"c");

        RenderedEmail email = BrandedEmailTemplate.passwordReset(List.of(hostile), 30, 2026);

        assertThat(email.html()).doesNotContain("<b onmouseover");
        assertThat(email.html()).contains("&lt;b onmouseover=&quot;x&quot;&gt;&amp;");
        assertThat(email.html()).contains("token=a&amp;b=&quot;c").doesNotContain("b=\"c");
        // The plain-text twin is not HTML, so it carries the values as they are.
        assertThat(email.text()).contains("https://h.example/r?token=a&b=\"c");
    }

    @Test
    void passwordReset_loadsNothingFromTheInternet() {
        String html = BrandedEmailTemplate.passwordReset(List.of(ALICE), 30, 2026).html();

        assertThat(html).contains("src=\"cid:pte-logo\"").doesNotContain("src=\"http", "<script", "<link", "@import", "url(", "<iframe", "<form");
    }

    @Test
    void passwordReset_usesTheLayoutMailClientsCanRender() {
        String html = BrandedEmailTemplate.passwordReset(List.of(ALICE), 30, 2026).html();

        assertThat(html).contains("role=\"presentation\"", "bgcolor=", "<meta charset=\"utf-8\">");
        assertThat(html).contains("name=\"viewport\"");
        assertThat(html).doesNotContain("display:flex", "display: flex", "display:grid", "display: grid");
    }

    @Test
    void passwordReset_showsTheYearInTheFooter() {
        assertThat(BrandedEmailTemplate.passwordReset(List.of(ALICE), 30, 2031).html()).contains("© 2031 PTE Prep");
    }

    @Test
    void passwordReset_plainTextCarriesTheSameLinks() {
        RenderedEmail email = BrandedEmailTemplate.passwordReset(List.of(ALICE, BOB), 30, 2026);

        assertThat(email.text()).contains("alice", "https://host.example/reset-password?token=aaa", "bob",
                "https://admin.example/reset-password?token=bbb");
        assertThat(email.text()).doesNotContain("<");
    }

    // ---- temporary credentials ----

    @Test
    void credentials_welcomeFlow_rendersSummaryCardCtaAndCallout() {
        RenderedEmail email = BrandedEmailTemplate.temporaryCredentials(
                "Bùi Khánh Linh",
                "Ninh Lê Organization 2",
                "Student",
                "ninh-le2.linhbui",
                "UpGS-pdxr",
                "http://localhost:3000/login",
                true,
                2026);

        assertThat(email.subject()).isEqualTo("[PTE Prep] Welcome to PTE Prep - Ninh Lê Organization 2");
        assertThat(email.html()).contains(
                "Welcome to PTE Prep",
                "Hi Bùi Khánh Linh,",
                "Your account for <strong>Ninh Lê Organization 2</strong> is ready. Use the details below to sign in to PTE Prep Practice or the exam app.",
                "Organization",
                "Role",
                "Student",
                "ninh-le2.linhbui",
                "UpGS-pdxr",
                "Sign in to PTE Prep Practice",
                "http://localhost:3000/login",
                "Button not working? Copy this link into your browser:",
                "<strong>Keep this password private.</strong>",
                "Did not expect this? Contact your center or administrator.",
                "Warm regards,<br>The PTE Prep team");
        assertThat(email.text()).contains(
                "Welcome to PTE Prep",
                "Hi Bùi Khánh Linh,",
                "Organization: Ninh Lê Organization 2",
                "Role: Student",
                "Username: ninh-le2.linhbui",
                "Temporary password: UpGS-pdxr",
                "Sign in: http://localhost:3000/login",
                "Warm regards,\nThe PTE Prep team");
    }

    @Test
    void credentials_resetFlow_rendersTemporaryPasswordHeadingAndIntro() {
        RenderedEmail email = BrandedEmailTemplate.temporaryCredentials(
                "An Nguyen",
                "Acme Center",
                "Student",
                "tenant.student",
                "ABCD-2345",
                "https://app.pteprep.local/login",
                false,
                2026);

        assertThat(email.subject()).contains("Your temporary password");
        assertThat(email.html()).contains(
                "Your temporary password",
                "Hi An Nguyen,",
                "A temporary password was set for your <strong>Acme Center</strong> account on PTE Prep.",
                "Sign in to PTE Prep Practice",
                "<strong>Keep this password private.</strong>");
        assertThat(email.html()).doesNotContain("Welcome to PTE Prep");
    }

    @Test
    void credentials_putsEachValueInItsOwnSelectableBlockAndStopsMailAutoLinking() {
        String html = BrandedEmailTemplate.temporaryCredentials("An", "a@b.vn", "ABCD-2345", 2026).html();

        assertThat(html).contains("a<span>@</span>b<span>.</span>vn").doesNotContain("a@b.vn");
        assertThat(html).contains("ABCD-2345", "Keep this password private.");
    }

    @Test
    void credentials_escapesTheName() {
        RenderedEmail email = BrandedEmailTemplate.temporaryCredentials("<script>alert(1)</script>", "u", "p", 2026);

        assertThat(email.html()).doesNotContain("<script>");
        assertThat(email.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void credentials_greetsPoliteWhenThereIsNoName() {
        RenderedEmail email = BrandedEmailTemplate.temporaryCredentials(null, "u", "p", 2026);

        assertThat(email.html()).contains("Hi,").doesNotContain("null");
        assertThat(email.text()).doesNotContain("null");
    }

    @Test
    void rejection_keepsReasonLineBreaksAndEscapesMarkup() {
        RenderedEmail email = BrandedEmailTemplate.applicationRejected("Acme", "acme",
                "Line one\r\nLine <b>two</b> & three", "https://x.example/register", "help@x.example", 2026);

        assertThat(email.html()).contains("Line one<br>Line &lt;b&gt;two&lt;/b&gt; &amp; three")
                .doesNotContain("<b>two");
        assertThat(email.text()).contains("Reason: Line one");
    }

    @Test
    void approvalAndRejection_shareTheSameButtonColourAndLinkSpacing() {
        String approved = BrandedEmailTemplate.applicationApproved("Acme", "acme", "u", "p", "https://x.example/login",
                "help@x.example", 2026).html();
        String rejected = BrandedEmailTemplate.applicationRejected("Acme", "acme", "r", "https://x.example/register",
                "help@x.example", 2026).html();

        for (String html : new String[] {approved, rejected}) {
            assertThat(html).contains("bgcolor=\"#3359d2\"").contains("margin:0 0 6px").contains("margin:0 0 22px");
        }
    }
}
