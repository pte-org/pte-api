package com.pte.identity.internal.validation;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.auth.ChangePasswordRequest;
import com.pte.identity.internal.dto.request.auth.ResetPasswordRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEdgeSpacesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> messages(Object request) {
        return validator.validate(request).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aNewPasswordMayNotStartOrEndWithASpace() {
        assertThat(messages(new ChangePasswordRequest("old", " Secret#123"))).contains(IdentityConstants.PASSWORD_EDGE_SPACES);
        assertThat(messages(new ChangePasswordRequest("old", "Secret#123 "))).contains(IdentityConstants.PASSWORD_EDGE_SPACES);
        assertThat(messages(new ResetPasswordRequest("Secret#123\t"))).contains(IdentityConstants.PASSWORD_EDGE_SPACES);
    }

    @Test
    void spacesInsideThePasswordAreStillAllowed() {
        assertThat(messages(new ChangePasswordRequest("old", "my secret 123"))).isEmpty();
        assertThat(messages(new ResetPasswordRequest("Secret#123"))).isEmpty();
    }
}
