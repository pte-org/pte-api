package com.pte.identity.internal.validation;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.auth.ChangePasswordRequest;
import com.pte.identity.internal.dto.request.auth.PasswordResetConfirmRequest;
import com.pte.identity.internal.dto.request.auth.ResetPasswordRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MaxUtf8BytesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> messages(Object request) {
        return validator.validate(request).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aPasswordOfExactlyTheLimitIsAcceptedAndOneByteMoreIsRefused() {
        assertThat(messages(new ResetPasswordRequest("a".repeat(72)))).isEmpty();
        assertThat(messages(new ResetPasswordRequest("a".repeat(73)))).containsExactly(IdentityConstants.PASSWORD_TOO_LONG);
    }

    @Test
    void theLimitCountsUtf8BytesSoAccentedVietnameseCharactersCountForMore() {
        String accented = "Mật khẩu của tôi rất là dài đó nhé các bạn ơi nhớ kỹ"; // fewer than 72 characters
        assertThat(accented.length()).isLessThan(72);
        assertThat(accented.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);

        assertThat(messages(new ResetPasswordRequest(accented))).containsExactly(IdentityConstants.PASSWORD_TOO_LONG);
    }

    @Test
    void everythingTheValidatorAcceptsCanReallyBeHashedByBcrypt() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String longestAllowed = "ậ".repeat(24); // 3 bytes each, exactly 72
        assertThat(messages(new ResetPasswordRequest(longestAllowed))).isEmpty();
        assertThat(encoder.matches(longestAllowed, encoder.encode(longestAllowed))).isTrue();
    }

    @Test
    void anEmptyOrShortPasswordKeepsItsOwnMessagesAndNullIsLeftToNotBlank() {
        assertThat(messages(new ResetPasswordRequest("short"))).containsExactly(IdentityConstants.PASSWORD_MIN_LENGTH);
        assertThat(messages(new ResetPasswordRequest(null))).containsExactly(IdentityConstants.PASSWORD_REQUIRED);
    }

    @Test
    void thePasswordChangeAndResetLinkRequestsApplyTheSameLimit() {
        assertThat(messages(new ChangePasswordRequest("current", "a".repeat(73)))).containsExactly(IdentityConstants.PASSWORD_TOO_LONG);
        assertThat(messages(new PasswordResetConfirmRequest("token", "a".repeat(73)))).containsExactly(IdentityConstants.PASSWORD_TOO_LONG);
        assertThat(messages(new ChangePasswordRequest("current", "a".repeat(72)))).isEmpty();
    }
}
