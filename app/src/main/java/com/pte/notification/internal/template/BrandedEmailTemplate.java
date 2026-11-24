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
import static com.pte.notification.internal.template.EmailLayout.mailtoLink;
import static com.pte.notification.internal.template.EmailLayout.page;
import static com.pte.notification.internal.template.EmailLayout.paragraph;
import static com.pte.notification.internal.template.EmailLayout.steps;
import static com.pte.notification.internal.template.EmailLayout.summary;

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
            rows.append(accountLabel(link.username()))
                    .append(button(link.url(), "Reset password"));
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
                + paragraph(MUTED, "Did not expect this? Contact your center or administrator.");

        String text = BRAND + "\n\n"
                + (named ? "Hello " + fullName + ",\n" : "Hello,\n")
                + "A temporary password was set for your " + BRAND + " account. Sign in and change it right away.\n\n"
                + "Username: " + username + "\n"
                + "Temporary password: " + temporaryPassword + "\n";

        return new RenderedEmail(subject, text, page("Your temporary login", rows, year));
    }

    /** Approval mail: the Host always gets a new account, so it carries the generated username and password. */
    public static RenderedEmail applicationApproved(String organizationName, String tenantCode, String username,
            String temporaryPassword, int year) {
        String subject = BRAND + " - Your application was approved";
        String[][] details = {{"Organization", organizationName}, {"Tenant code", tenantCode},
                {"Username", username}, {"Temporary password", temporaryPassword}};
        String rows = heading("Your application was approved")
                + paragraph(BODY, "Your organization is ready. Sign in with the details below and <strong>change the"
                        + " password right away</strong>.")
                + fields(details);

        StringBuilder text = new StringBuilder(BRAND).append("\n\nYour application was approved.\n")
                .append("Sign in with the details below and change the password right away.\n\n");
        for (String[] row : details) {
            text.append(row[0]).append(": ").append(row[1]).append('\n');
        }

        return new RenderedEmail(subject, text.toString(), page("Your application was approved", rows, year));
    }

    /**
     * Confirmation sent when an application is submitted: what we received, what happens next and where to write.
     * {@code supportEmail} may be blank, in which case the contact line is left out.
     */
    public static RenderedEmail applicationReceived(String organizationName, String requestedCode, int responseHours,
            String supportEmail, int year) {
        String subject = "[" + BRAND + "] We've received your application - " + singleLine(organizationName);
        boolean hasSupport = supportEmail != null && !supportEmail.isBlank();
        String rows = heading("Application received")
                + paragraph(BODY, "Dear " + escape(organizationName) + " team,")
                + paragraph(BODY, "Thank you for choosing " + BRAND + ". We have received your onboarding request with"
                        + " the details below.")
                + summary(new String[][] {{"Organization", organizationName, "text"},
                        {"Requested tenant code", requestedCode, "mono"}, {"Status", "Pending review", "badge"}})
                + paragraph(BODY, "<strong>What happens next</strong>")
                + steps(new String[][] {
                        {"Review:", "our team is checking your details and will reply within <strong>"
                                + responseHours + " hours</strong>."},
                        {"Account setup:", "once approved, we will email you the Host Admin sign-in details so you"
                                + " can set up your center."}})
                + (hasSupport ? paragraph(BODY, "Questions, or need to change your application? Write to "
                        + mailtoLink(supportEmail) + ".") : "")
                + paragraph(BODY, "Warm regards,<br>The " + BRAND + " team");

        String text = BRAND + "\n\nApplication received\n\n"
                + "Dear " + organizationName + " team,\n\n"
                + "Thank you for choosing " + BRAND + ". We have received your onboarding request with the details"
                + " below.\n\n"
                + "Organization: " + organizationName + "\n"
                + "Requested tenant code: " + requestedCode + "\n\n"
                + "What happens next\n"
                + "- Review: our team is checking your details and will reply within " + responseHours + " hours.\n"
                + "- Account setup: once approved, we will email you the Host Admin sign-in details so you can set up"
                + " your center.\n"
                + (hasSupport ? "\nQuestions, or need to change your application? Write to " + supportEmail + ".\n" : "")
                + "\nWarm regards,\nThe " + BRAND + " team\n";

        return new RenderedEmail(subject, text, page("Application received", rows, year));
    }

    /** Header values must stay on one line, whatever the applicant typed. */
    private static String singleLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
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
