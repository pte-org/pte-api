package com.aptis.modules.iam.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.domain.Admin;
import com.aptis.modules.iam.domain.Host;
import com.aptis.modules.iam.domain.RefreshToken;
import com.aptis.modules.iam.domain.Student;
import com.aptis.modules.iam.domain.enums.UserStatus;
import com.aptis.modules.iam.dto.response.auth.AuthResponse;
import com.aptis.modules.iam.dto.response.auth.UserProfileResponse;
import com.aptis.modules.iam.interfaces.AuthOperations;
import com.aptis.modules.iam.interfaces.CredentialProvisioning;
import com.aptis.modules.iam.interfaces.JwtOperations;
import com.aptis.modules.iam.repository.AdminRepository;
import com.aptis.modules.iam.repository.HostRepository;
import com.aptis.modules.iam.repository.RefreshTokenRepository;
import com.aptis.modules.iam.repository.StudentRepository;

@Service
@Transactional
public class AuthService implements AuthOperations {

    private final AdminRepository adminRepository;
    private final CredentialProvisioning credentialProvisioning;
    private final GraderAuthHelper graderAuthHelper;
    private final HostRepository hostRepository;
    private final JwtOperations jwtOperations;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StudentRepository studentRepository;

    public AuthService(
            AdminRepository adminRepository,
            CredentialProvisioning credentialProvisioning,
            GraderAuthHelper graderAuthHelper,
            HostRepository hostRepository,
            JwtOperations jwtOperations,
            RefreshTokenRepository refreshTokenRepository,
            StudentRepository studentRepository) {
        this.adminRepository = adminRepository;
        this.credentialProvisioning = credentialProvisioning;
        this.graderAuthHelper = graderAuthHelper;
        this.hostRepository = hostRepository;
        this.jwtOperations = jwtOperations;
        this.refreshTokenRepository = refreshTokenRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    public AuthResponse login(String credential, String password) {
        return adminRepository.findByEmail(credential)
                .map(admin -> loginAdmin(admin, password))
                .or(() -> hostRepository.findByContactEmail(credential)
                        .map(host -> loginHost(host, password)))
                .or(() -> studentRepository.findByUsername(credential)
                        .map(student -> loginStudent(student, password)))
                .or(() -> graderAuthHelper.findByUsername(credential)
                        .map(grader -> graderAuthHelper.login(grader, password)))
                .orElseThrow(() -> new ApiException(ErrorCode.IAM_INVALID_CREDENTIAL));
    }

    @Override
    public AuthResponse refresh(String refreshTokenString) {
        RefreshToken refreshToken = findUsableRefreshToken(refreshTokenString);

        if (IamApiConstants.USER_TYPE_ADMIN.equals(refreshToken.getUserType())) {
            Admin admin = findAdmin(refreshToken.getUserId());
            return createAuthResponse(admin, null, null);
        }
        if (IamApiConstants.USER_TYPE_HOST.equals(refreshToken.getUserType())) {
            Host host = findHost(refreshToken.getUserId());
            return createAuthResponse(host, null);
        }
        if (IamApiConstants.USER_TYPE_GRADER.equals(refreshToken.getUserType())) {
            return graderAuthHelper.refresh(refreshToken.getUserId());
        }

        Student student = findStudent(refreshToken.getUserId());
        return createAuthResponse(student, null);
    }

    @Override
    public void logout(String refreshTokenString, JwtPrincipal principal) {
        RefreshToken refreshToken = findUsableRefreshToken(refreshTokenString);
        if (!refreshToken.getUserId().equals(principal.userId())
                || !refreshToken.getUserType().equals(principal.userType())) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }

        refreshToken.revoke(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);
    }

    @Override
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        Host host = findHost(userId);
        validatePassword(currentPassword, host.getPasswordHash());
        host.setPasswordHash(credentialProvisioning.hashPassword(newPassword));
        host.setMustChangePassword(false);
        hostRepository.save(host);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(JwtPrincipal principal) {
        if (IamApiConstants.USER_TYPE_ADMIN.equals(principal.userType())) {
            Admin admin = findAdmin(principal.userId());
            return new UserProfileResponse(
                    admin.getId(),
                    admin.getName(),
                    admin.getEmail(),
                    IamApiConstants.ROLE_ADMIN,
                    IamApiConstants.USER_TYPE_ADMIN,
                    null,
                    false);
        }
        if (IamApiConstants.USER_TYPE_HOST.equals(principal.userType())) {
            Host host = findHost(principal.userId());
            return new UserProfileResponse(
                    host.getId(),
                    host.getName(),
                    host.getContactEmail(),
                    IamApiConstants.ROLE_HOST,
                    IamApiConstants.USER_TYPE_HOST,
                    host.getOrganizationId(),
                    host.getMustChangePassword());
        }
        if (IamApiConstants.USER_TYPE_GRADER.equals(principal.userType())) {
            return graderAuthHelper.getProfile(principal.userId());
        }

        Student student = findStudent(principal.userId());
        return new UserProfileResponse(
                student.getId(),
                student.getUsername(),
                student.getUsername(),
                IamApiConstants.ROLE_STUDENT,
                IamApiConstants.USER_TYPE_STUDENT,
                student.getOrganizationId(),
                false);
    }

    private AuthResponse loginAdmin(Admin admin, String password) {
        validateStatus(admin.getStatus());
        validatePassword(password, admin.getPasswordHash());
        return createAuthResponse(admin, createRawRefreshToken(admin.getId(), IamApiConstants.USER_TYPE_ADMIN),
                null);
    }

    private AuthResponse loginHost(Host host, String password) {
        validateStatus(host.getStatus());
        validatePassword(password, host.getPasswordHash());
        return createAuthResponse(host, createRawRefreshToken(host.getId(), IamApiConstants.USER_TYPE_HOST));
    }

    private AuthResponse loginStudent(Student student, String password) {
        validateStatus(student.getStatus());
        validatePassword(password, student.getPasswordHash());
        return createAuthResponse(student,
                createRawRefreshToken(student.getId(), IamApiConstants.USER_TYPE_STUDENT));
    }

    private AuthResponse createAuthResponse(Admin admin, String refreshToken, Long tenantId) {
        String accessToken = jwtOperations.generateToken(
                admin.getId(),
                IamApiConstants.USER_TYPE_ADMIN,
                IamApiConstants.ROLE_ADMIN,
                tenantId);
        return new AuthResponse(
                accessToken,
                refreshToken,
                IamApiConstants.TOKEN_TYPE_BEARER,
                jwtOperations.getExpirationSeconds(),
                IamApiConstants.ROLE_ADMIN,
                IamApiConstants.USER_TYPE_ADMIN,
                tenantId,
                false);
    }

    private AuthResponse createAuthResponse(Host host, String refreshToken) {
        String accessToken = jwtOperations.generateToken(
                host.getId(),
                IamApiConstants.USER_TYPE_HOST,
                IamApiConstants.ROLE_HOST,
                host.getOrganizationId());
        return new AuthResponse(
                accessToken,
                refreshToken,
                IamApiConstants.TOKEN_TYPE_BEARER,
                jwtOperations.getExpirationSeconds(),
                IamApiConstants.ROLE_HOST,
                IamApiConstants.USER_TYPE_HOST,
                host.getOrganizationId(),
                host.getMustChangePassword());
    }

    private AuthResponse createAuthResponse(Student student, String refreshToken) {
        Long tenantId = student.getOrganizationId();
        String accessToken = jwtOperations.generateToken(
                student.getId(),
                IamApiConstants.USER_TYPE_STUDENT,
                IamApiConstants.ROLE_STUDENT,
                tenantId);
        return new AuthResponse(
                accessToken,
                refreshToken,
                IamApiConstants.TOKEN_TYPE_BEARER,
                jwtOperations.getExpirationSeconds(),
                IamApiConstants.ROLE_STUDENT,
                IamApiConstants.USER_TYPE_STUDENT,
                tenantId,
                false);
    }

    private String createRawRefreshToken(Long userId, String userType) {
        String rawRefreshToken = UUID.randomUUID().toString();
        RefreshToken refreshToken = new RefreshToken(
                hashRefreshToken(rawRefreshToken),
                userId,
                userType,
                LocalDateTime.now().plusDays(IamApiConstants.REFRESH_TOKEN_DAYS));
        refreshTokenRepository.save(refreshToken);
        return rawRefreshToken;
    }

    private RefreshToken findUsableRefreshToken(String rawRefreshToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashRefreshToken(rawRefreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));

        if (refreshToken.isRevoked() || refreshToken.isExpired(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return refreshToken;
    }

    private String hashRefreshToken(String rawRefreshToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(IamApiConstants.SHA_256_ALGORITHM);
            byte[] hashedToken = messageDigest.digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedToken);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void validatePassword(String rawPassword, String passwordHash) {
        if (!credentialProvisioning.validatePassword(rawPassword, passwordHash)) {
            throw new ApiException(ErrorCode.IAM_INVALID_CREDENTIAL);
        }
    }

    private void validateStatus(UserStatus userStatus) {
        if (!UserStatus.ACTIVE.equals(userStatus)) {
            throw new ApiException(ErrorCode.IAM_INVALID_CREDENTIAL);
        }
    }

    private Admin findAdmin(Long userId) {
        return adminRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private Host findHost(Long userId) {
        return hostRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private Student findStudent(Long userId) {
        return studentRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}
