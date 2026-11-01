package com.pte.notification.internal.template;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The building blocks every PTE Prep e-mail is made of: fixed frame, escaping, copy-friendly values. */
class EmailLayoutTest {

    @Test
    void page_isOneCardWithTheLogoAFooterAndAnOutlookWidthGuard() {
        String html = EmailLayout.page("Pre", EmailLayout.heading("Hi"), 2031);

        assertThat(html).contains("max-width:520px", "src=\"cid:pte-logo\"", "© 2031 PTE Prep",
                "<!--[if mso]>", "<!--[if mso]></td></tr></table><![endif]-->", "role=\"presentation\"")
                .doesNotContain("<script", "<link", "@import", "url(", "src=\"http");
    }

    @Test
    void escape_neutralisesMarkupAndQuotes() {
        assertThat(EmailLayout.escape("<a href=\"x\">'&'</a>"))
                .isEqualTo("&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#39;&lt;/a&gt;");
        assertThat(EmailLayout.escape(null)).isEmpty();
    }

    @Test
    void plain_splitsAnAddressSoNoClientLinksIt_butLeavesOtherValuesAlone() {
        assertThat(EmailLayout.plain("a.b@mail.co")).isEqualTo("a<span>.</span>b<span>@</span>mail<span>.</span>co");
        assertThat(EmailLayout.plain("tenant.student")).isEqualTo("tenant.student");
    }

    @Test
    void fields_putEachValueInItsOwnBlock_andWrapLongValues() {
        String html = EmailLayout.fields(new String[][] {{"Username", "u<b>"}, {"Token", "x".repeat(400)}});

        assertThat(html).contains("u&lt;b&gt;", "word-break:break-all", "user-select:all").doesNotContain("<b>");
        assertThat(html.split("-webkit-user-select:all;", -1)).hasSize(3);
    }

    @Test
    void scores_showTheFirstRowAsTheHeadline() {
        String html = EmailLayout.scores(new String[][] {{"Overall", "72"}, {"Reading", "Not enough data"}});

        assertThat(html).contains("font-size:20px", ">72<", ">Not enough data<");
    }

    @Test
    void lines_escapeEachValueAndKeepOneLineEach() {
        String html = EmailLayout.lines(List.of("<s> -> A", "id2 -> B"));

        assertThat(html).contains("&lt;s&gt; -&gt; A<br>", "id2 -&gt; B<br>").doesNotContain("<s>");
    }
}
