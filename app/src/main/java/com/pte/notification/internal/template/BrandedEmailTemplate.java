package com.pte.notification.internal.template;

import com.pte.identity.PasswordResetEmailRequestedEvent.Link;

import java.util.List;

/**
 * PTE Prep's card e-mails, in English and short enough to read without scrolling: logo, a few lines, copy-friendly
 * fields or a button, and a footer. Built for mail clients rather than browsers: nested tables, inline styles and a
 * `bgcolor` button that Outlook can draw. The only image is the project logo, attached to the message and referenced as
 * {@code cid:pte-logo} (see {@link #LOGO_CID}); nothing is loaded from the internet. Every value an account controls is
 * HTML-escaped; links come only from our own configuration. Each mail also has a plain-text twin.
 */
public final class BrandedEmailTemplate {

    /** Content-ID of the inline logo; the sender attaches {@code notification/logo-mark.png} under this id. */
    public static final String LOGO_CID = "pte-logo";

    private static final String BRAND = "PTE Prep";
    private static final String FONT = "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    private static final String MONO = "Consolas,Menlo,'Courier New',monospace";
    private static final String PAGE = "#f2f5f9";
    private static final String CARD = "#ffffff";
    private static final String ACTION = "#0d6fbe";
    private static final String BRAND_INK = "#3359d2";
    private static final String HEADING = "#111827";
    private static final String BODY = "#374151";
    private static final String MUTED = "#6b7280";
    private static final String RULE = "#d9dee7";

    private BrandedEmailTemplate() {
    }

    public static RenderedEmail passwordReset(List<Link> links, int expiresInMinutes, int year) {
        String subject = BRAND + " - Reset your password";
        StringBuilder rows = new StringBuilder()
                .append(heading("Reset your password"))
                .append(paragraph(BODY, "Use the button below to choose a new password for your " + BRAND
                        + " account. The link works once and expires in <strong>" + expiresInMinutes
                        + " minutes</strong>."));
        for (Link link : links) {
            if (links.size() > 1) {
                rows.append(accountLabel(link.username()));
            }
            rows.append(button(link.url(), "Reset password"));
        }
        rows.append(paragraph(MUTED, "Did not ask for this? Ignore this email and your password stays the same."))
                .append(paragraph(MUTED, "Button not working? Copy this link into your browser:"));
        for (Link link : links) {
            rows.append(fallbackLink(links.size() > 1 ? link.username() : null, link.url()));
        }

        StringBuilder text = new StringBuilder(BRAND).append("\n\nReset your password\n")
                .append("Open the link below to choose a new password. It works once and expires in ")
                .append(expiresInMinutes).append(" minutes.\n\n");
        for (Link link : links) {
            text.append("- ").append(link.username()).append(": ").append(link.url()).append('\n');
        }
        text.append("\nDid not ask for this? Ignore this email and your password stays the same.\n");

        return new RenderedEmail(subject, text.toString(), page("Reset your password", rows.toString(), year));
    }

    public static RenderedEmail temporaryCredentials(String fullName, String username, String temporaryPassword,
            int year) {
        String subject = BRAND + " - Your temporary login";
        boolean named = fullName != null && !fullName.isBlank();
        String rows = heading(named ? "Hello " + escape(fullName) + "," : "Hello,")
                + paragraph(BODY, "A temporary password was set for your " + BRAND + " account. Sign in with the"
                        + " details below and <strong>change it right away</strong>.")
                + fields(new String[][] {{"Username", username}, {"Temporary password", temporaryPassword}})
                + paragraph(MUTED, "Did not expect this? Contact your centre or administrator.");

        String text = BRAND + "\n\n"
                + (named ? "Hello " + fullName + ",\n" : "Hello,\n")
                + "A temporary password was set for your " + BRAND + " account. Sign in and change it right away.\n\n"
                + "Username: " + username + "\n"
                + "Temporary password: " + temporaryPassword + "\n";

        return new RenderedEmail(subject, text, page("Your temporary login", rows, year));
    }

    /**
     * Approval mail. A contact who had no account gets the generated credentials; one who already had an account is
     * told to sign in with their current password ({@code temporaryPassword} is then null).
     */
    public static RenderedEmail applicationApproved(String organizationName, String tenantCode, String username,
            String temporaryPassword, int year) {
        String subject = BRAND + " - Your application was approved";
        boolean newAccount = temporaryPassword != null;
        String[][] details = newAccount
                ? new String[][] {{"Organization", organizationName}, {"Tenant code", tenantCode},
                        {"Username", username}, {"Temporary password", temporaryPassword}}
                : new String[][] {{"Organization", organizationName}, {"Tenant code", tenantCode},
                        {"Username", username}};
        String rows = heading("Your application was approved")
                + paragraph(BODY, newAccount
                        ? "Your organization is ready. Sign in with the details below and <strong>change the"
                                + " password right away</strong>."
                        : "Your organization was added to your existing account. Sign in with your current"
                                + " password and choose it.")
                + fields(details);

        StringBuilder text = new StringBuilder(BRAND).append("\n\nYour application was approved.\n")
                .append(newAccount
                        ? "Sign in with the details below and change the password right away.\n\n"
                        : "Your organization was added to your existing account. Sign in with your current password"
                                + " and choose it.\n\n");
        for (String[] row : details) {
            text.append(row[0]).append(": ").append(row[1]).append('\n');
        }

        return new RenderedEmail(subject, text.toString(), page("Your application was approved", rows, year));
    }

    public static RenderedEmail applicationReceived(int responseHours, int year) {
        String subject = BRAND + " - Application received";
        String rows = heading("Application received")
                + paragraph(BODY, "Thanks for applying to " + BRAND + ". Our team will review it and reply within <strong>"
                        + responseHours + " hours</strong>.");

        String text = BRAND + "\n\nApplication received\n"
                + "Thanks for applying to " + BRAND + ". Our team will review it and reply within " + responseHours
                + " hours.\n";

        return new RenderedEmail(subject, text, page("Application received", rows, year));
    }

    public static RenderedEmail applicationRejected(String reason, int year) {
        String subject = BRAND + " - Update on your application";
        String rows = heading("Update on your application")
                + paragraph(BODY, "We can't approve your application at this time.")
                + fields(new String[][] {{"Reason", reason}})
                + paragraph(MUTED, "Address the reason above and submit a new application to be reviewed again.");

        String text = BRAND + "\n\nUpdate on your application\n"
                + "We can't approve your application at this time.\n\n"
                + "Reason: " + reason + "\n\n"
                + "Address the reason above and submit a new application to be reviewed again.\n";

        return new RenderedEmail(subject, text, page("Update on your application", rows, year));
    }

    // ---- building blocks ----

    private static String page(String preheader, String rows, int year) {
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
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;">
                <tr><td bgcolor="{{card}}" style="background-color:{{card}};border-radius:8px;padding:28px 32px 24px;font-family:{{font}};">
                {{brandRow}}
                {{rows}}
                </td></tr>
                <tr><td align="center" style="padding:16px;font-family:{{font}};font-size:12px;line-height:18px;color:{{muted}};">
                © {{year}} {{brand}} · Automated email, please do not reply.
                </td></tr>
                </table>
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

    private static String heading(String html) {
        return "<h1 style=\"margin:0 0 12px;font-family:" + FONT + ";font-size:22px;line-height:28px;font-weight:700;"
                + "color:" + HEADING + ";\">" + html + "</h1>";
    }

    private static String paragraph(String color, String html) {
        return "<p style=\"margin:0 0 14px;font-family:" + FONT + ";font-size:14px;line-height:22px;color:" + color
                + ";\">" + html + "</p>";
    }

    private static String accountLabel(String username) {
        return "<p style=\"margin:16px 0 0;text-align:center;font-family:" + FONT + ";font-size:13px;color:" + MUTED
                + ";\">Account: <strong style=\"color:" + HEADING + ";\">" + plain(username) + "</strong></p>";
    }

    private static String button(String url, String label) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\""
                + " style=\"margin:16px auto 20px;\"><tr>"
                + "<td align=\"center\" bgcolor=\"" + ACTION + "\" style=\"background-color:" + ACTION
                + ";border-radius:6px;\">"
                + "<a href=\"" + escape(url) + "\" target=\"_blank\" style=\"display:inline-block;padding:12px 28px;"
                + "font-family:" + FONT + ";font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;"
                + "border-radius:6px;\">" + escape(label) + "</a>"
                + "</td></tr></table>";
    }

    private static String fallbackLink(String username, String url) {
        String prefix = username == null ? "" : plain(username) + ": ";
        return "<p style=\"margin:0 0 8px;font-family:" + MONO + ";font-size:12px;line-height:18px;color:" + BODY
                + ";word-break:break-all;\">" + prefix + escape(url) + "</p>";
    }

    /**
     * Label above a value, each value in its own bordered block so a triple-click selects exactly that value (and
     * {@code user-select:all} lets one click do it where the client honours it). E-mail addresses are split around the
     * "@" so Gmail does not turn them into links that swallow the click.
     */
    private static String fields(String[][] rows) {
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

    /** Escapes a value and keeps mail clients from auto-linking an address inside it. */
    private static String plain(String value) {
        return escape(value).replace("@", "<span>@</span>");
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
