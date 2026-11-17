package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.request.auth.PasswordResetConfirmRequest;
import com.pte.identity.internal.dto.request.auth.PasswordResetRequest;
import com.pte.identity.internal.dto.request.auth.PasswordResetValidateRequest;
import com.pte.identity.internal.service.AuthService;
import com.pte.identity.internal.service.passwordreset.PasswordResetDispatcher;
import com.pte.identity.internal.service.passwordreset.PasswordResetService;
import com.pte.identity.internal.service.UserProfileService;
import com.pte.identity.internal.service.UserService;
import com.pte.shared.web.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/** Asking for a reset link is acknowledged at once and handed off; spending a link is handled in the request. */
@ExtendWith(MockitoExtension.class)
class AuthControllerPasswordResetTest {

    @Mock
    private AuthService authService;
    @Mock
    private UserService userService;
    @Mock
    private UserProfileService userProfileService;
    @Mock
    private PasswordResetService passwordResetService;
    @Mock
    private PasswordResetDispatcher passwordResetDispatcher;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, userService, userProfileService, passwordResetService,
                passwordResetDispatcher);
    }

    @Test
    void request_handsTheEmailAndTheTrustedAddressToTheDispatcherAndAnswersSuccess() {
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("10.0.0.5");
        http.addHeader("X-Forwarded-For", "203.0.113.1, 198.51.100.7");

        ApiResponse<Void> response = controller.requestPasswordReset(new PasswordResetRequest("host@x.vn", "host-1"),
                http);

        assertThat(response.success()).isTrue();
        verify(passwordResetDispatcher).requestAsync("host@x.vn", "host-1", "198.51.100.7");
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void request_withoutAForwardedHeaderUsesTheSocketAddress() {
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("10.0.0.5");

        controller.requestPasswordReset(new PasswordResetRequest("host@x.vn", "host-1"), http);

        verify(passwordResetDispatcher).requestAsync("host@x.vn", "host-1", "10.0.0.5");
    }

    @Test
    void requestBody_requiresBothTheEmailAndTheUsername() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new PasswordResetRequest("host@x.vn", "host-1"))).isEmpty();
            assertThat(validator.validate(new PasswordResetRequest("host@x.vn", null))).hasSize(1);
            assertThat(validator.validate(new PasswordResetRequest("host@x.vn", "  "))).hasSize(1);
            assertThat(validator.validate(new PasswordResetRequest(" ", "host-1"))).hasSize(1);
        }
    }

    @Test
    void confirm_isHandledOnTheRequestThread() {
        controller.confirmPasswordReset(new PasswordResetConfirmRequest("raw", "NewPassword456"));

        verify(passwordResetService).confirm("raw", "NewPassword456");
        verifyNoInteractions(passwordResetDispatcher);
    }

    @Test
    void validate_tellsWhetherALinkCanStillBeUsed_withoutSpendingIt() {
        when(passwordResetService.isUsable("raw")).thenReturn(true);
        when(passwordResetService.isUsable("spent")).thenReturn(false);

        assertThat(controller.validatePasswordReset(new PasswordResetValidateRequest("raw")).data().valid()).isTrue();
        assertThat(controller.validatePasswordReset(new PasswordResetValidateRequest("spent")).data().valid()).isFalse();

        verifyNoInteractions(passwordResetDispatcher);
    }
}
