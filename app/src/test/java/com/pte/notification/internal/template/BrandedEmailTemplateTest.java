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

        // A username only shows when one address has several accounts, so give it a second one.
        RenderedEmail email = BrandedEmailTemplate.passwordReset(List.of(hostile, ALICE), 30, 2026);

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
    void credentials_showsTheLoginDetailsWithoutAnyLink() {
        RenderedEmail email = BrandedEmailTemplate.temporaryCredentials("An Nguyen", "tenant.student", "ABCD-2345", 2026);

        assertThat(email.html()).contains("An Nguyen", "tenant.student", "ABCD-2345");
        assertThat(email.html()).doesNotContain("href=");
        assertThat(email.html()).contains("change it right away", "Temporary password");
        assertThat(email.text()).contains("tenant.student", "ABCD-2345");
        assertThat(email.subject()).isNotBlank();
    }

    @Test
    void credentials_putsEachValueInItsOwnSelectableBlockAndStopsMailAutoLinking() {
        String html = BrandedEmailTemplate.temporaryCredentials("An", "a@b.vn", "ABCD-2345", 2026).html();

        assertThat(html).contains("user-select:all", "a<span>@</span>b.vn").doesNotContain("a@b.vn");
        assertThat(occurrences(html, "-webkit-user-select:all")).isEqualTo(2);
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

        assertThat(email.html()).contains("Hello,").doesNotContain("null");
        assertThat(email.text()).doesNotContain("null");
    }
}
