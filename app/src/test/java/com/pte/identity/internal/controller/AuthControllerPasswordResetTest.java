package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.request.auth.PasswordResetConfirmRequest;
import com.pte.identity.internal.dto.request.auth.PasswordResetRequest;
import com.pte.identity.internal.service.AuthService;
import com.pte.identity.internal.service.PasswordResetDispatcher;
import com.pte.identity.internal.service.PasswordResetService;
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

        ApiResponse<Void> response = controller.requestPasswordReset(new PasswordResetRequest("host@x.vn"), http);

        assertThat(response.success()).isTrue();
        verify(passwordResetDispatcher).requestAsync("host@x.vn", "198.51.100.7");
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void request_withoutAForwardedHeaderUsesTheSocketAddress() {
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("10.0.0.5");

        controller.requestPasswordReset(new PasswordResetRequest("host@x.vn"), http);

        verify(passwordResetDispatcher).requestAsync("host@x.vn", "10.0.0.5");
    }

    @Test
    void confirm_isHandledOnTheRequestThread() {
        controller.confirmPasswordReset(new PasswordResetConfirmRequest("raw", "NewPassword456"));

        verify(passwordResetService).confirm("raw", "NewPassword456");
        verifyNoInteractions(passwordResetDispatcher);
    }
}
