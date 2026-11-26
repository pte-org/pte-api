package com.pte.notification.internal.template;

import com.pte.identity.PasswordResetEmailRequestedEvent.Link;

import java.util.List;

import static com.pte.notification.internal.template.EmailLayout.BODY;
import static com.pte.notification.internal.template.EmailLayout.BRAND;
import static com.pte.notification.internal.template.EmailLayout.MUTED;
import static com.pte.notification.internal.template.EmailLayout.accountLabel;
import static com.pte.notification.internal.template.EmailLayout.button;
import static com.pte.notification.internal.template.EmailLayout.dangerCallout;
import static com.pte.notification.internal.template.EmailLayout.callout;
import static com.pte.notification.internal.template.EmailLayout.escape;
import static com.pte.notification.internal.template.EmailLayout.fallbackLink;
import static com.pte.notification.internal.template.EmailLayout.fields;
import static com.pte.notification.internal.template.EmailLayout.heading;
import static com.pte.notification.internal.template.EmailLayout.linkHint;
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
        return temporaryCredentials(fullName, null, null, username, temporaryPassword, null, true, year);
    }

    /**
     * Credentials card shared by both new-account welcome emails ({@code newAccount = true}) and admin-issued
     * temporary password resets ({@code newAccount = false}). Dynamic title and intro distinguish the two flows while
     * keeping the summary card, sign-in button, fallback link, and amber callout uniform.
     */
    public static RenderedEmail temporaryCredentials(String fullName, String organizationName, String roleLabel,
            String username, String temporaryPassword, String loginUrl, boolean newAccount, int year) {
        boolean named = fullName != null && !fullName.isBlank();
        boolean hasOrg = organizationName != null && !organizationName.isBlank();
        boolean hasRole = roleLabel != null && !roleLabel.isBlank();
        boolean hasLoginUrl = loginUrl != null && !loginUrl.isBlank();

        boolean isStudent = "Student".equalsIgnoreCase(roleLabel);
        String title = newAccount ? "Welcome to " + BRAND : "Your temporary password";
        String subject = "[" + BRAND + "] " + (newAccount ? "Welcome to " + BRAND : "Your temporary password")
                + (hasOrg ? " - " + singleLine(organizationName) : "");
        String greeting = named ? "Hi " + escape(fullName) + "," : "Hi,";
        String plainGreeting = named ? "Hi " + fullName + "," : "Hi,";

        String introHtml;
        String introText;
        if (newAccount) {
            if (isStudent) {
                introHtml = hasOrg
                        ? "Your account for <strong>" + escape(organizationName)
                                + "</strong> is ready. Use the details below to sign in to " + BRAND
                                + " Practice or the exam app."
                        : "Your " + BRAND + " student account is ready. Use the details below to sign in to " + BRAND
                                + " Practice or the exam app.";
                introText = hasOrg
                        ? "Your account for " + organizationName + " is ready. Use the details below to sign in to "
                                + BRAND + " Practice or the exam app."
                        : "Your " + BRAND + " student account is ready. Use the details below to sign in to " + BRAND
                                + " Practice or the exam app.";
            } else {
                introHtml = hasOrg
                        ? "Your account for <strong>" + escape(organizationName)
                                + "</strong> is ready. Use the details below to sign in to " + BRAND + "."
                        : "Your " + BRAND + " account is ready. Use the details below to sign in.";
                introText = hasOrg
                        ? "Your account for " + organizationName + " is ready. Use the details below to sign in to "
                                + BRAND + "."
                        : "Your " + BRAND + " account is ready. Use the details below to sign in.";
            }
        } else {
            introHtml = hasOrg
                    ? "A temporary password was set for your <strong>" + escape(organizationName)
                            + "</strong> account on " + BRAND + ". Use the details below to sign in."
                    : "A temporary password was set for your " + BRAND
                            + " account. Use the details below to sign in.";
            introText = hasOrg
                    ? "A temporary password was set for your " + organizationName + " account on " + BRAND
                            + ". Use the details below to sign in."
                    : "A temporary password was set for your " + BRAND
                            + " account. Use the details below to sign in.";
        }

        String ctaLabel = isStudent ? "Sign in to " + BRAND + " Practice" : "Sign in to " + BRAND;

        java.util.ArrayList<String[]> summaryRows = new java.util.ArrayList<>();
        if (hasOrg) {
            summaryRows.add(new String[] {"Organization", organizationName, "text"});
        }
        if (hasRole) {
            summaryRows.add(new String[] {"Role", roleLabel, "badge-info"});
        }
        summaryRows.add(new String[] {"Username", username, "mono"});
        summaryRows.add(new String[] {"Temporary password", temporaryPassword, "mono"});

        StringBuilder rows = new StringBuilder()
                .append(heading(title))
                .append(paragraph(BODY, greeting))
                .append(paragraph(BODY, introHtml))
                .append(summary(summaryRows.toArray(String[][]::new)));
        if (hasLoginUrl) {
            rows.append(button(loginUrl, ctaLabel))
                    .append(linkHint("Button not working? Copy this link into your browser:", loginUrl));
        }
        rows.append(callout("<strong>Keep this password private.</strong> It is temporary: you will be asked to"
                        + " choose a new one the first time you sign in."))
                .append(paragraph(MUTED, "Did not expect this? Contact your center or administrator."))
                .append(paragraph(BODY, "Warm regards,<br>The " + BRAND + " team"));

        StringBuilder text = new StringBuilder(BRAND).append("\n\n").append(title).append("\n\n")
                .append(plainGreeting).append("\n\n")
                .append(introText).append("\n\n");
        if (hasOrg) {
            text.append("Organization: ").append(organizationName).append('\n');
        }
        if (hasRole) {
            text.append("Role: ").append(roleLabel).append('\n');
        }
        text.append("Username: ").append(username).append('\n')
                .append("Temporary password: ").append(temporaryPassword).append("\n\n");
        if (hasLoginUrl) {
            text.append("Sign in: ").append(loginUrl).append("\n\n");
        }
        text.append("Keep this password private. It is temporary: you will be asked to choose a new one the first")
                .append(" time you sign in.\n\n")
                .append("Did not expect this? Contact your center or administrator.\n\n")
                .append("Warm regards,\nThe ").append(BRAND).append(" team\n");

        return new RenderedEmail(subject, text.toString(), page(title, rows.toString(), year));
    }

    /**
     * Approval mail: the Host always gets a new account, so it carries the generated username and password and a
     * button to the sign-in page. {@code supportEmail} may be blank, in which case the contact line is left out.
     */
    public static RenderedEmail applicationApproved(String organizationName, String tenantCode, String username,
            String temporaryPassword, String loginUrl, String supportEmail, int year) {
        String subject = "[" + BRAND + "] Your application was approved - " + singleLine(organizationName);
        boolean hasSupport = supportEmail != null && !supportEmail.isBlank();
        String rows = heading("Your application was approved")
                + paragraph(BODY, "Dear " + escape(organizationName) + " team,")
                + paragraph(BODY, "Congratulations! Your organization is ready on " + BRAND + ". Use the details below"
                        + " to sign in as Host Admin.")
                + summary(new String[][] {{"Organization", organizationName, "text"},
                        {"Tenant code", tenantCode, "mono"}, {"Username", username, "mono"},
                        {"Temporary password", temporaryPassword, "mono"},
                        {"Status", "Approved", "badge-success"}})
                + button(loginUrl, "Sign in to " + BRAND)
                + linkHint("Button not working? Copy this link into your browser:", loginUrl)
                + callout("<strong>Keep this password private.</strong> It is temporary: you will be asked to choose a"
                        + " new one the first time you sign in.")
                + (hasSupport ? paragraph(BODY, "Need help getting started? Write to " + mailtoLink(supportEmail) + ".")
                        : "")
                + paragraph(BODY, "Warm regards,<br>The " + BRAND + " team");

        StringBuilder text = new StringBuilder(BRAND).append("\n\nYour application was approved\n\n")
                .append("Dear ").append(organizationName).append(" team,\n\n")
                .append("Congratulations! Your organization is ready on ").append(BRAND)
                .append(". Use the details below to sign in as Host Admin.\n\n")
                .append("Organization: ").append(organizationName).append('\n')
                .append("Tenant code: ").append(tenantCode).append('\n')
                .append("Username: ").append(username).append('\n')
                .append("Temporary password: ").append(temporaryPassword).append("\n\n")
                .append("Sign in: ").append(loginUrl).append("\n\n")
                .append("Keep this password private. It is temporary: you will be asked to choose a new one the first")
                .append(" time you sign in.\n");
        if (hasSupport) {
            text.append("\nNeed help getting started? Write to ").append(supportEmail).append(".\n");
        }
        text.append("\nWarm regards,\nThe ").append(BRAND).append(" team\n");

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

    /**
     * Rejection mail: names the application, gives the reviewer's reason as plain text, and links back to the
     * registration page. {@code supportEmail} may be blank, in which case the contact line is left out.
     */
    public static RenderedEmail applicationRejected(String organizationName, String requestedCode, String reason,
            String registerUrl, String supportEmail, int year) {
        String subject = "[" + BRAND + "] Update on your application - " + singleLine(organizationName);
        boolean hasSupport = supportEmail != null && !supportEmail.isBlank();
        String rows = heading("Update on your application")
                + paragraph(BODY, "Dear " + escape(organizationName) + " team,")
                + paragraph(BODY, "Thank you for your interest in " + BRAND + ". After reviewing your request, we are"
                        + " unable to approve your application at this time.")
                + summary(new String[][] {{"Organization", organizationName, "text"},
                        {"Requested tenant code", requestedCode, "mono"}, {"Status", "Not approved", "badge-danger"}})
                + dangerCallout("<strong>Reason</strong><br>" + escape(reason.replace("\r\n", "\n").replace('\r', '\n')).replace("\n", "<br>"))
                + paragraph(BODY, "You are welcome to address the reason above and submit a new application.")
                + button(registerUrl, "Submit a new application")
                + linkHint("Button not working? Copy this link into your browser:", registerUrl)
                + (hasSupport ? paragraph(BODY, "Think this was a mistake, or need to explain more? Write to "
                        + mailtoLink(supportEmail) + ".") : "")
                + paragraph(BODY, "Warm regards,<br>The " + BRAND + " team");

        StringBuilder text = new StringBuilder(BRAND).append("\n\nUpdate on your application\n\n")
                .append("Dear ").append(organizationName).append(" team,\n\n")
                .append("Thank you for your interest in ").append(BRAND)
                .append(". After reviewing your request, we are unable to approve your application at this time.\n\n")
                .append("Organization: ").append(organizationName).append('\n')
                .append("Requested tenant code: ").append(requestedCode).append('\n')
                .append("Status: Not approved\n\n")
                .append("Reason: ").append(reason).append("\n\n")
                .append("You are welcome to address the reason above and submit a new application: ")
                .append(registerUrl).append('\n');
        if (hasSupport) {
            text.append("\nThink this was a mistake, or need to explain more? Write to ").append(supportEmail)
                    .append(".\n");
        }
        text.append("\nWarm regards,\nThe ").append(BRAND).append(" team\n");

        return new RenderedEmail(subject, text.toString(), page("Update on your application", rows, year));
    }
}
