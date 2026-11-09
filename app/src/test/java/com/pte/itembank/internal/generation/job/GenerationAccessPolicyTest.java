package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.exception.GenerationJobNotFoundException;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationAccessPolicyTest {

    private final UUID owner = UUID.randomUUID();

    private QuestionGenerationJob jobOf(UUID requester) {
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setRequestedByUserPublicId(requester);
        return job;
    }

    private CurrentUser user(UUID id, String role) {
        return new CurrentUser(id, null, List.of(role));
    }

    @Test
    void aStaffWriterReachesOnlyTheirOwnJobAndAnotherOneIsNotFound() {
        assertThatCode(() -> GenerationAccessPolicy.requireAccess(user(owner, "ACADEMIC_STAFF"), jobOf(owner)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> GenerationAccessPolicy.requireAccess(
                user(UUID.randomUUID(), "ACADEMIC_STAFF"), jobOf(owner)))
                .isInstanceOf(GenerationJobNotFoundException.class);
    }

    @Test
    void reviewersReachEveryWritersJob() {
        assertThatCode(() -> GenerationAccessPolicy.requireAccess(
                user(UUID.randomUUID(), "ACADEMIC_MANAGER"), jobOf(owner))).doesNotThrowAnyException();
        assertThatCode(() -> GenerationAccessPolicy.requireAccess(
                user(UUID.randomUUID(), "PLATFORM_ADMIN"), jobOf(owner))).doesNotThrowAnyException();
    }

    @Test
    void aRoleThatDoesNotWriteQuestionsIsDenied() {
        assertThatThrownBy(() -> GenerationAccessPolicy.requireAccess(user(owner, "HOST_ADMIN"), jobOf(owner)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> GenerationAccessPolicy.requireAuthor(user(owner, "PLATFORM_MANAGER")))
                .isInstanceOf(AccessDeniedException.class);
    }
}
