package com.pte.itembank.internal.generation.prompt.version;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.exception.AiPromptException;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import org.springframework.http.HttpStatus;

/** Who may do what to a prompt version; the same split as the rubric versions. */
final class PromptWorkflowPolicy {

    private PromptWorkflowPolicy() {
    }

    /** Writing, testing and sending for approval. */
    static void author(CurrentUser caller) {
        caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN, SecurityRoles.ACADEMIC_MANAGER,
                SecurityRoles.ACADEMIC_STAFF, SecurityRoles.LEGACY_PLATFORM_AUTHOR);
    }

    /** Approving or rejecting; an author cannot decide on their own change unless they are an admin. */
    static void reviewer(CurrentUser caller, AiPromptVersion candidate) {
        caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN, SecurityRoles.ACADEMIC_MANAGER);
        if (!isAdmin(caller) && caller.userId().equals(candidate.getAuthorUserPublicId())) {
            throw new AiPromptException(HttpStatus.FORBIDDEN, PromptVersionConstants.SELF_APPROVAL_FORBIDDEN,
                    PromptVersionConstants.SELF_APPROVAL_FORBIDDEN_MESSAGE);
        }
    }

    /** Changing or dropping a version: its author, a manager or an admin. */
    static void editor(CurrentUser caller, AiPromptVersion version) {
        author(caller);
        boolean owner = caller.userId().equals(version.getAuthorUserPublicId());
        if (!owner && !isAdmin(caller) && !caller.hasRole(SecurityRoles.ACADEMIC_MANAGER)) {
            throw new AiPromptException(HttpStatus.FORBIDDEN, PromptVersionConstants.AUTHOR_ONLY,
                    PromptVersionConstants.AUTHOR_ONLY_MESSAGE);
        }
    }

    /** Prompts whose answer can be tried on a sample; a change to the others is sent for approval without a test. */
    /**
     * Whether a version must pass a test before it is sent for approval. The scorer and chatbot prompts have no test,
     * and neither has the chart image reviewer: the test runs on text, and that reviewer needs a picture.
     */
    static boolean testable(AiPromptKind kind, String promptKey) {
        if (kind == AiPromptKind.REVIEWER) {
            return QuestionGenerationConstants.PROMPT_KEY_REVIEWER.equals(promptKey);
        }
        return kind != AiPromptKind.SCORER && kind != AiPromptKind.CHATBOT;
    }

    static boolean isAdmin(CurrentUser caller) {
        return caller.hasRole(SecurityRoles.PLATFORM_ADMIN);
    }
}
