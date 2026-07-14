package com.aptis.modules.iam.interfaces;

import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.dto.response.auth.AuthResponse;
import com.aptis.modules.iam.dto.response.auth.UserProfileResponse;

public interface AuthOperations {
    AuthResponse login(String credential, String password);
    AuthResponse refresh(String refreshTokenString);
    void logout(String refreshTokenString, JwtPrincipal principal);
    void changePassword(Long userId, String currentPassword, String newPassword);
    UserProfileResponse getProfile(JwtPrincipal principal);
}
