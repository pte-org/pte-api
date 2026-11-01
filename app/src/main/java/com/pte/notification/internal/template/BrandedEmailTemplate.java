package com.pte.notification.internal.template;

import com.pte.identity.PasswordResetEmailRequestedEvent.Link;

import java.util.List;

import static com.pte.notification.internal.template.EmailLayout.BODY;
import static com.pte.notification.internal.template.EmailLayout.BRAND;
import static com.pte.notification.internal.template.EmailLayout.MUTED;
import static com.pte.notification.internal.template.EmailLayout.accountLabel;
import static com.pte.notification.internal.template.EmailLayout.button;
import static com.pte.notification.internal.template.EmailLayout.escape;
import static com.pte.notification.internal.template.EmailLayout.fallbackLink;
import static com.pte.notification.internal.template.EmailLayout.fields;
import static com.pte.notification.internal.template.EmailLayout.heading;
import static com.pte.notification.internal.template.EmailLayout.page;
import static com.pte.notification.internal.template.EmailLayout.paragraph;

/**
 * PTE Prep's card e-mails, in English and short enough to read without scrolling: logo, a few lines, copy-friendly
 * fields or a button, and a footer. Built for mail clients rather than browsers: nested tables, inline styles and a
 * `bgcolor` button that Outlook can draw. The only image is the project logo, attached to the message and referenced as
 * {@code cid:pte-logo} (see {@link #LOGO_CID}); nothing is loaded from the internet. Every value an account controls is
 * HTML-escaped; links come only from our own configuration. Each mail also has a plain-text twin.
 */
public final class BrandedEmailTemplate {

    /** Content-ID of the inline logo; the sender attaches {@code notification/logo-mark.png} under this id. */
    public static final String LOGO_CID = EmailLayout.LOGO_CID;

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
}
