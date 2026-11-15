package com.pte.practice.internal.service.session;

import com.pte.practice.internal.exception.PracticeSessionExpiredException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A mocked repository never goes through Spring's transaction proxy, so the behavioural tests cannot see that a
 * thrown exception rolls the EXPIRED status back. This pins the declaration that prevents it: every operation that
 * marks the session expired and then answers 410 must commit that status.
 */
class PracticeSessionExpiryTransactionTest {

    private static final List<String> OPERATIONS_THAT_EXPIRE = List.of(
            "heartbeat", "answer", "skip", "saveDraft", "saveAndExit");

    @Test
    void operationsThatExpireTheSessionKeepTheExpiredStatusWhenTheyAnswerGone() {
        for (String name : OPERATIONS_THAT_EXPIRE) {
            Method method = Arrays.stream(PracticeSessionService.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing operation " + name));
            Transactional transactional = method.getAnnotation(Transactional.class);

            assertThat(transactional).as("%s is transactional", name).isNotNull();
            assertThat(transactional.noRollbackFor())
                    .as("%s must not roll back the EXPIRED status", name)
                    .contains(PracticeSessionExpiredException.class);
        }
    }
}
