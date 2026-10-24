package com.pte.identity.internal.service;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.UpdatePlatformUserProfileRequest;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.UserEmailRequiredException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.util.PasswordGenerator;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Profile, password and soft-delete lifecycle for manageable platform users. */
@Service
public class PlatformUserLifecycleService {

    private final PlatformUserService platformUserService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    public PlatformUserLifecycleService(PlatformUserService platformUserService, UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService, ApplicationEventPublisher eventPublisher,
            AuditLogService auditLogService) {
        this.platformUserService = platformUserService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public UserResponse updateProfile(UUID publicId, UpdatePlatformUserProfileRequest request, CurrentUser caller) {
        platformUserService.requirePlatformUserManagement(caller);
        User target = activeTarget(publicId, caller);
        target.setFullName(request.fullName().trim());
        target.setPhone(normalizeOptional(request.phone()));
        target.setDateOfBirth(request.dateOfBirth());
        User saved = userRepository.save(target);
        auditLogService.record(caller, IdentityConstants.PLATFORM_USER_AGGREGATE, publicId.toString(),
                IdentityConstants.PLATFORM_USER_PROFILE_UPDATED,
                IdentityConstants.PLATFORM_USER_PROFILE_UPDATED_SUMMARY);
        return UserMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse resetPassword(UUID publicId, CurrentUser caller) {
        platformUserService.requirePlatformUserManagement(caller);
        User target = activeTarget(publicId, caller);
        if (target.getEmail() == null || target.getEmail().isBlank()) {
            throw new UserEmailRequiredException();
        }

        String temporaryPassword = PasswordGenerator.generateReadable();
        target.setCredentialHash(passwordEncoder.encode(temporaryPassword));
        target.setMustChangePassword(true);
        userRepository.save(target);
        refreshTokenService.revokeAllForUser(target.getId());
        eventPublisher.publishEvent(new UserCredentialsEmailRequestedEvent(
                UUID.randomUUID(), target.getPublicId(), null, target.getUsername(),
                target.getEmail(), target.getFullName(), temporaryPassword));
        auditLogService.record(caller, IdentityConstants.PLATFORM_USER_AGGREGATE, publicId.toString(),
                IdentityConstants.PLATFORM_USER_PASSWORD_RESET,
                IdentityConstants.PLATFORM_USER_PASSWORD_RESET_SUMMARY);
        return UserMapper.toResponse(target);
    }

    @Transactional
    public UserResponse archive(UUID publicId, CurrentUser caller) {
        platformUserService.requirePlatformUserManagement(caller);
        User target = activeTarget(publicId, caller);
        target.setDeleted(true);
        userRepository.save(target);
        refreshTokenService.revokeAllForUser(target.getId());
        auditLogService.record(caller, IdentityConstants.PLATFORM_USER_AGGREGATE, publicId.toString(),
                IdentityConstants.PLATFORM_USER_ARCHIVED,
                IdentityConstants.PLATFORM_USER_ARCHIVED_SUMMARY);
        return UserMapper.toResponse(target);
    }

    @Transactional
    public UserResponse restore(UUID publicId, CurrentUser caller) {
        platformUserService.requirePlatformUserManagement(caller);
        User target = platformUserService.findPlatformTargetWithLockIncludingDeleted(publicId);
        platformUserService.rejectSelfOrAdminTarget(target, caller);
        platformUserService.authorizePlatformTarget(caller, publicId, target.getRoles());
        if (target.isDeleted()) {
            target.setDeleted(false);
            userRepository.save(target);
            auditLogService.record(caller, IdentityConstants.PLATFORM_USER_AGGREGATE, publicId.toString(),
                    IdentityConstants.PLATFORM_USER_RESTORED,
                    IdentityConstants.PLATFORM_USER_RESTORED_SUMMARY);
        }
        return UserMapper.toResponse(target);
    }

    private User activeTarget(UUID publicId, CurrentUser caller) {
        User target = platformUserService.findPlatformTargetWithLock(publicId);
        platformUserService.rejectSelfOrAdminTarget(target, caller);
        platformUserService.authorizePlatformTarget(caller, publicId, target.getRoles());
        return target;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
