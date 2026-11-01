package com.pte.notification.internal.template;

import java.util.List;

/**
 * The shared look of every PTE Prep e-mail: one white card on a pale page, the project mark and wordmark on top, a
 * short heading, compact content blocks and a one-line footer. Built for mail clients rather than browsers: nested
 * tables, inline styles, a `bgcolor` button that Outlook can draw and an {@code mso} wrapper that keeps Outlook
 * desktop at the card width. The only image is the project logo, attached to the message and referenced as
 * {@code cid:pte-logo} (see {@link #LOGO_CID}); nothing is loaded from the internet. Callers pass HTML fragments, so
 * every value an account controls must go through {@link #escape} or {@link #plain} first.
 */
final class EmailLayout {

    /** Content-ID of the inline logo; the sender attaches {@code notification/logo-mark.png} under this id. */
    static final String LOGO_CID = "pte-logo";
    static final String BRAND = "PTE Prep";

    static final String BODY = "#374151";
    static final String MUTED = "#6b7280";

    private static final String FONT = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    private static final String MONO = "Consolas,Menlo,'Courier New',monospace";
    private static final String PAGE = "#f2f5f9";
    private static final String CARD = "#ffffff";
    private static final String ACTION = "#0d6fbe";
    private static final String BRAND_INK = "#3359d2";
    private static final String HEADING = "#111827";
    private static final String RULE = "#d9dee7";
    private static final String WRAP = "word-break:break-word;overflow-wrap:anywhere;";

    private EmailLayout() {
    }

    static String page(String preheader, String rows, int year) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="color-scheme" content="light">
                <meta name="supported-color-schemes" content="light">
                <title>{{brand}}</title>
                </head>
                <body style="margin:0;padding:0;background-color:{{page}};">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">{{preheader}}</div>
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" bgcolor="{{page}}" style="background-color:{{page}};">
                <tr><td align="center" style="padding:24px 12px;">
                <!--[if mso]><table role="presentation" width="520" cellpadding="0" cellspacing="0" border="0"><tr><td><![endif]-->
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;">
                <tr><td bgcolor="{{card}}" style="background-color:{{card}};border-radius:8px;padding:28px 32px 24px;font-family:{{font}};">
                {{brandRow}}
                {{rows}}
                </td></tr>
                <tr><td align="center" style="padding:16px;font-family:{{font}};font-size:12px;line-height:18px;color:{{muted}};">
                © {{year}} {{brand}} · Automated email, please do not reply.
                </td></tr>
                </table>
                <!--[if mso]></td></tr></table><![endif]-->
                </td></tr>
                </table>
                </body>
                </html>
                """
                .replace("{{brandRow}}", brandRow())
                .replace("{{rows}}", rows)
                .replace("{{preheader}}", escape(preheader))
                .replace("{{year}}", String.valueOf(year))
                .replace("{{brand}}", BRAND)
                .replace("{{page}}", PAGE)
                .replace("{{card}}", CARD)
                .replace("{{muted}}", MUTED)
                .replace("{{font}}", FONT);
    }

    /** The project's own mark (attached inline) beside the wordmark. */
    private static String brandRow() {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\""
                + " style=\"margin:0 auto 20px;\"><tr>"
                + "<td valign=\"middle\"><img src=\"cid:" + LOGO_CID + "\" width=\"26\" height=\"30\" alt=\"\""
                + " style=\"display:block;border:0;\"></td>"
                + "<td valign=\"middle\" style=\"padding-left:10px;font-family:" + FONT
                + ";font-size:22px;font-weight:800;color:" + BRAND_INK + ";\">" + BRAND + "</td>"
                + "</tr></table>";
    }

    static String heading(String html) {
        return "<h1 style=\"margin:0 0 12px;font-family:" + FONT + ";font-size:22px;line-height:28px;font-weight:700;"
                + "color:" + HEADING + ";" + WRAP + "\">" + html + "</h1>";
    }

    static String paragraph(String color, String html) {
        return "<p style=\"margin:0 0 14px;font-family:" + FONT + ";font-size:14px;line-height:22px;color:" + color
                + ";" + WRAP + "\">" + html + "</p>";
    }

    static String accountLabel(String username) {
        return "<p style=\"margin:16px 0 0;text-align:center;font-family:" + FONT + ";font-size:13px;color:" + MUTED
                + ";" + WRAP + "\">Account: <strong style=\"color:" + HEADING + ";\">" + plain(username)
                + "</strong></p>";
    }

    static String button(String url, String label) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\""
                + " style=\"margin:16px auto 20px;\"><tr>"
                + "<td align=\"center\" bgcolor=\"" + ACTION + "\" style=\"background-color:" + ACTION
                + ";border-radius:6px;\">"
                + "<a href=\"" + escape(url) + "\" target=\"_blank\" style=\"display:inline-block;padding:12px 28px;"
                + "font-family:" + FONT + ";font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;"
                + "border-radius:6px;\">" + escape(label) + "</a>"
                + "</td></tr></table>";
    }

    static String fallbackLink(String username, String url) {
        String prefix = username == null ? "" : plain(username) + ": ";
        return "<p style=\"margin:0 0 8px;font-family:" + MONO + ";font-size:12px;line-height:18px;color:" + BODY
                + ";word-break:break-all;\">" + prefix + escape(url) + "</p>";
    }

    /**
     * Label above a value, each value in its own bordered block so a triple-click selects exactly that value (and
     * {@code user-select:all} lets one click do it where the client honours it).
     */
    static String fields(String[][] rows) {
        StringBuilder html = new StringBuilder();
        for (String[] row : rows) {
            html.append("<p style=\"margin:0 0 4px;font-family:").append(FONT)
                    .append(";font-size:12px;line-height:16px;color:").append(MUTED).append(";\">")
                    .append(escape(row[0])).append("</p>")
                    .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
                            + " border=\"0\" style=\"margin:0 0 12px;\"><tr><td bgcolor=\"" + PAGE
                            + "\" style=\"background-color:" + PAGE + ";border:1px solid " + RULE
                            + ";border-radius:6px;padding:10px 12px;font-family:" + MONO
                            + ";font-size:15px;line-height:20px;font-weight:700;color:" + HEADING
                            + ";word-break:break-all;user-select:all;-webkit-user-select:all;\">")
                    .append(plain(row[1])).append("</td></tr></table>");
        }
        return html.toString();
    }

    /** Label and value side by side, one row each; the first row is shown larger as the headline figure. */
    static String scores(String[][] rows) {
        StringBuilder html = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\""
                + " cellspacing=\"0\" border=\"0\" bgcolor=\"" + PAGE + "\" style=\"margin:0 0 14px;background-color:"
                + PAGE + ";border:1px solid " + RULE + ";border-radius:6px;\">");
        for (int i = 0; i < rows.length; i++) {
            boolean headline = i == 0;
            String rule = i == 0 ? "" : "border-top:1px solid " + RULE + ";";
            html.append("<tr><td style=\"padding:").append(headline ? "12px 14px" : "9px 14px").append(";").append(rule)
                    .append("font-family:").append(FONT).append(";font-size:").append(headline ? "15px" : "14px")
                    .append(";color:").append(headline ? HEADING : BODY).append(";font-weight:")
                    .append(headline ? "700" : "400").append(";\">").append(escape(rows[i][0]))
                    .append("</td><td align=\"right\" style=\"padding:").append(headline ? "12px 14px" : "9px 14px")
                    .append(";").append(rule).append("font-family:").append(FONT).append(";font-size:")
                    .append(headline ? "20px" : "14px").append(";color:").append(HEADING)
                    .append(";font-weight:700;\">").append(escape(rows[i][1])).append("</td></tr>");
        }
        return html.append("</table>").toString();
    }

    /** A short list of monospace lines in one bordered block, for identifiers the reader may copy. */
    static String lines(List<String> values) {
        StringBuilder html = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\""
                + " cellspacing=\"0\" border=\"0\" style=\"margin:0 0 14px;\"><tr><td bgcolor=\"" + PAGE
                + "\" style=\"background-color:" + PAGE + ";border:1px solid " + RULE
                + ";border-radius:6px;padding:10px 12px;font-family:" + MONO
                + ";font-size:12px;line-height:20px;color:" + HEADING + ";word-break:break-all;\">");
        for (String value : values) {
            html.append(plain(value)).append("<br>");
        }
        return html.append("</td></tr></table>").toString();
    }

    /**
     * Escapes a value and keeps mail clients from auto-linking an address inside it: Gmail looks for addresses and
     * domains inside one run of text, so the "@" and every "." of an address are each wrapped in their own span. The
     * visible text, and what a copy picks up, is unchanged.
     */
    static String plain(String value) {
        String escaped = escape(value);
        if (!escaped.contains("@")) {
            return escaped;
        }
        return escaped.replace("@", "<span>@</span>").replace(".", "<span>.</span>");
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
