package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.exception.GenerationJobNotFoundException;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;

import java.util.List;

/**
 * Who may generate questions with AI and whose jobs they see. Everyone who writes questions for the platform bank
 * may generate them; Academic Staff see only their own jobs, while Platform Admins and Academic Managers, who
 * review what staff write, see every job. Another writer's job answers as not found, so its existence is not told.
 */
public final class GenerationAccessPolicy {

    /** The roles that write questions for the platform bank, as role names without the ROLE_ prefix. */
    public static final List<String> AUTHOR_ROLES = List.of(SecurityRoles.PLATFORM_ADMIN,
            SecurityRoles.ACADEMIC_MANAGER, SecurityRoles.ACADEMIC_STAFF, SecurityRoles.LEGACY_PLATFORM_AUTHOR);

    private GenerationAccessPolicy() {
    }

    public static void requireAuthor(CurrentUser caller) {
        caller.requireAnyRole(AUTHOR_ROLES.toArray(String[]::new));
    }

    /** Reviewers of the bank see every writer's jobs. */
    public static boolean seesAllJobs(CurrentUser caller) {
        return caller.hasRole(SecurityRoles.PLATFORM_ADMIN) || caller.hasRole(SecurityRoles.ACADEMIC_MANAGER);
    }

    public static void requireAccess(CurrentUser caller, QuestionGenerationJob job) {
        requireAuthor(caller);
        if (!seesAllJobs(caller) && !caller.userId().equals(job.getRequestedByUserPublicId())) {
            throw new GenerationJobNotFoundException();
        }
    }
}
