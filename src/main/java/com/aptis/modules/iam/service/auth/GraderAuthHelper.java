package com.aptis.modules.iam.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.domain.Grader;
import com.aptis.modules.iam.domain.RefreshToken;
import com.aptis.modules.iam.domain.enums.UserStatus;
import com.aptis.modules.iam.dto.response.auth.AuthResponse;
import com.aptis.modules.iam.dto.response.auth.UserProfileResponse;
import com.aptis.modules.iam.interfaces.CredentialProvisioning;
import com.aptis.modules.iam.interfaces.JwtOperations;
import com.aptis.modules.iam.repository.GraderRepository;
import com.aptis.modules.iam.repository.RefreshTokenRepository;

@Service
@Transactional
public class GraderAuthHelper {

    private final GraderRepository graderRepository;
    private final CredentialProvisioning credentialProvisioning;
    private final JwtOperations jwtOperations;
    private final RefreshTokenRepository refreshTokenRepository;

    public GraderAuthHelper(
            GraderRepository graderRepository,
            CredentialProvisioning credentialProvisioning,
            JwtOperations jwtOperations,
            RefreshTokenRepository refreshTokenRepository) {
        this.graderRepository = graderRepository;
        this.credentialProvisioning = credentialProvisioning;
        this.jwtOperations = jwtOperations;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public Optional<Grader> findByUsername(String username) {
        return graderRepository.findByUsername(username);
    }

    public AuthResponse login(Grader grader, String password) {
        validateActive(grader);
        if (!credentialProvisioning.validatePassword(password, grader.getPasswordHash())) {
            throw new ApiException(ErrorCode.IAM_INVALID_CREDENTIAL);
        }
        return buildAuthResponse(grader, createRefreshToken(grader.getId()));
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(Long userId) {
        Grader grader = findGrader(userId);
        validateActive(grader);
        return buildAuthResponse(grader, null);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        Grader grader = findGrader(userId);
        return new UserProfileResponse(
                grader.getId(),
                grader.getUsername(),
                grader.getUsername(),
                IamApiConstants.ROLE_GRADER,
                IamApiConstants.USER_TYPE_GRADER,
                null,
                false);
    }

    private void validateActive(Grader grader) {
        if (!UserStatus.ACTIVE.equals(grader.getStatus())) {
            throw new ApiException(ErrorCode.IAM_INVALID_CREDENTIAL);
        }
    }

    private Grader findGrader(Long userId) {
        return graderRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private AuthResponse buildAuthResponse(Grader grader, String refreshToken) {
        String accessToken = jwtOperations.generateToken(
                grader.getId(),
                IamApiConstants.USER_TYPE_GRADER,
                IamApiConstants.ROLE_GRADER,
                null);
        return new AuthResponse(
                accessToken,
                refreshToken,
                IamApiConstants.TOKEN_TYPE_BEARER,
                jwtOperations.getExpirationSeconds(),
                IamApiConstants.ROLE_GRADER,
                IamApiConstants.USER_TYPE_GRADER,
                null,
                false);
    }

    private String createRefreshToken(Long userId) {
        String raw = UUID.randomUUID().toString();
        RefreshToken token = new RefreshToken(
                hashToken(raw),
                userId,
                IamApiConstants.USER_TYPE_GRADER,
                LocalDateTime.now().plusDays(IamApiConstants.REFRESH_TOKEN_DAYS));
        refreshTokenRepository.save(token);
        return raw;
    }

    private String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance(IamApiConstants.SHA_256_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
