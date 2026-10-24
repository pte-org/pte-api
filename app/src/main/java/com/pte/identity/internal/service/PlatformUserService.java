package com.pte.identity.internal.service;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.PlatformUserCreateRequest;
import com.pte.identity.internal.dto.request.PlatformUserRoleUpdateRequest;
import com.pte.identity.internal.dto.response.UserDirectoryEntryResponse;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.EmailAlreadyUsedException;
import com.pte.identity.internal.exception.ForbiddenRoleAssignmentException;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.util.PasswordGenerator;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityCapability;
import com.pte.shared.security.SecurityPolicy;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Platform-admin-only lifecycle and role management for tenantless users. */
@Service
public class PlatformUserService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProvisioningHelper provisioningHelper;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;

    public PlatformUserService(UserRepository userRepository,
            PasswordEncoder passwordEncoder, UserProvisioningHelper provisioningHelper,
            AuditLogService auditLogService, RefreshTokenService refreshTokenService,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.provisioningHelper = provisioningHelper;
        this.auditLogService = auditLogService;
        this.refreshTokenService = refreshTokenService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UserResponse create(PlatformUserCreateRequest request, CurrentUser caller) {
        requirePlatformUserManagement(caller);
        Set<Role> roles = resolveAssignablePlatformRoles(caller, request.roles());
        if (userRepository.existsByUsername(request.email())) {
            throw new EmailAlreadyUsedException();
        }

        User user = new User();
        user.setUsername(request.email());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setRoles(roles);
        String password = request.password();
        boolean generatedPassword = password == null || password.isBlank();
        if (generatedPassword) {
            password = PasswordGenerator.generateReadable();
            user.setMustChangePassword(true);
        }
        user.setCredentialHash(passwordEncoder.encode(password));
        User saved = userRepository.saveAndFlush(user);
        if (generatedPassword) {
            saved.setMustChangePassword(true);
        }
        auditLogService.record(caller, IdentityConstants.PLATFORM_USER_AGGREGATE,
                saved.getPublicId().toString(), IdentityConstants.PLATFORM_USER_CREATED,
                IdentityConstants.PLATFORM_USER_CREATED_SUMMARY);
        if (generatedPassword) {
            eventPublisher.publishEvent(new UserCredentialsEmailRequestedEvent(
                    UUID.randomUUID(), saved.getPublicId(), null, saved.getUsername(),
                    saved.getEmail(), saved.getFullName(), password));
        }
        return UserMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PagedResult<UserResponse> list(int requestedPage, int requestedSize, CurrentUser caller) {
        return list(requestedPage, requestedSize, null, null, null, caller);
    }

    @Transactional(readOnly = true)
    public PagedResult<UserResponse> list(int requestedPage, int requestedSize, String role,
            String status, CurrentUser caller) {
        return list(requestedPage, requestedSize, null, role, status, caller);
    }

    @Transactional(readOnly = true)
    public PagedResult<UserResponse> list(int requestedPage, int requestedSize, String search,
            String role, String status, CurrentUser caller) {
        return list(requestedPage, requestedSize, search, role, status, false, caller);
    }

    @Transactional(readOnly = true)
    public PagedResult<UserResponse> list(int requestedPage, int requestedSize, String search,
            String role, String status, boolean includeArchived, CurrentUser caller) {
        requirePlatformUserManagement(caller);
        int page = Math.max(DEFAULT_PAGE, requestedPage);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);
        Role roleFilter = parseRoleFilter(role);
        UserStatus statusFilter = parseStatusFilter(status);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        var result = includeArchived
                ? userRepository.findPageForPlatformUsersIncludingArchived(provisioningHelper.platformRoleValues(),
                        roleFilter, statusFilter, normalizedSearch, true, pageable)
                : userRepository.findPageForPlatformUsers(provisioningHelper.platformRoleValues(),
                        roleFilter, statusFilter, normalizedSearch, pageable);
        return new PagedResult<>(result.map(UserMapper::toResponse).getContent(),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(),
                        result.getTotalPages(), result.isFirst(), result.isLast(), result.hasNext(),
                        result.hasPrevious()));
    }

    @Transactional(readOnly = true)
    public List<UserDirectoryEntryResponse> listDirectory(CurrentUser caller) {
        if (caller == null || !caller.isPlatformUser()
                || (!caller.hasRole("PLATFORM_ADMIN") && !caller.hasRole("PLATFORM_MANAGER"))) {
            throw new AccessDeniedException(SharedConstants.ACCESS_DENIED);
        }
        return userRepository.findPlatformDirectory(provisioningHelper.platformRoleValues()).stream()
                .map(user -> new UserDirectoryEntryResponse(user.getPublicId(), user.getFullName()))
                .toList();
    }

    private Role parseRoleFilter(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            Role role = Role.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            return provisioningHelper.platformRoleValues().contains(role) ? role : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UserStatus parseStatusFilter(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return UserStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID publicId, CurrentUser caller) {
        requirePlatformUserManagement(caller);
        return UserMapper.toResponse(findPlatformTarget(publicId));
    }

    @Transactional
    public UserResponse updateRoles(UUID publicId, PlatformUserRoleUpdateRequest request, CurrentUser caller) {
        requirePlatformUserManagement(caller);
        User target = findPlatformTargetWithLock(publicId);
        rejectSelfOrAdminTarget(target, caller);
        authorizePlatformTarget(caller, publicId, target.getRoles());
        target.setRoles(resolveAssignablePlatformRoles(caller, request.roles()));
        userRepository.save(target);
        auditLogService.record(caller, IdentityConstants.PLATFORM_USER_AGGREGATE,
                publicId.toString(), IdentityConstants.PLATFORM_USER_ROLES_UPDATED,
                IdentityConstants.PLATFORM_USER_ROLES_UPDATED_SUMMARY);
        return UserMapper.toResponse(target);
    }

    @Transactional
    public UserResponse suspend(UUID publicId, CurrentUser caller) {
        requirePlatformUserManagement(caller);
        User target = findPlatformTargetWithLock(publicId);
        rejectSelfOrAdminTarget(target, caller);
        authorizePlatformTarget(caller, publicId, target.getRoles());
        target.suspend();
        refreshTokenService.revokeAllForUser(target.getId());
        auditLogService.record(caller, IdentityConstants.PLATFORM_USER_AGGREGATE,
                publicId.toString(), IdentityConstants.PLATFORM_USER_SUSPENDED,
                IdentityConstants.PLATFORM_USER_SUSPENDED_SUMMARY);
        return UserMapper.toResponse(target);
    }

    @Transactional
    public UserResponse reactivate(UUID publicId, CurrentUser caller) {
        requirePlatformUserManagement(caller);
        User target = findPlatformTargetWithLock(publicId);
        rejectSelfOrAdminTarget(target, caller);
        authorizePlatformTarget(caller, publicId, target.getRoles());
        if (target.isSuspended()) {
            target.reactivate();
            auditLogService.record(caller, IdentityConstants.PLATFORM_USER_AGGREGATE,
                    publicId.toString(), IdentityConstants.PLATFORM_USER_REACTIVATED,
                    IdentityConstants.PLATFORM_USER_REACTIVATED_SUMMARY);
        }
        return UserMapper.toResponse(target);
    }

    void requirePlatformUserManagement(CurrentUser caller) {
        if (!SecurityPolicy.hasCapability(caller, SecurityCapability.PLATFORM_USER_MANAGE)) {
            if (caller != null && caller.userId() != null) {
                recordAuthorizationDenied(caller, IdentityConstants.PLATFORM_AUTHORIZATION_AGGREGATE_ID);
            }
            throw new ForbiddenUserManagementException();
        }
    }

    private Set<Role> resolveAssignablePlatformRoles(CurrentUser caller, List<String> roleNames) {
        try {
            return provisioningHelper.resolvePlatformRoles(caller, roleNames);
        } catch (ForbiddenRoleAssignmentException ex) {
            recordAuthorizationDenied(caller, IdentityConstants.PLATFORM_ROLE_ASSIGNMENT_AGGREGATE_ID);
            throw ex;
        }
    }

    void authorizePlatformTarget(CurrentUser caller, UUID publicId, Set<Role> targetRoles) {
        try {
            provisioningHelper.authorizePlatformTarget(caller, targetRoles);
        } catch (ForbiddenUserManagementException ex) {
            recordAuthorizationDenied(caller, publicId.toString());
            throw ex;
        }
    }

    private User findPlatformTarget(UUID publicId) {
        User target = userRepository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(UserNotFoundException::new);
        if (!provisioningHelper.isPlatformTarget(target.getRoles())) {
            throw new UserNotFoundException();
        }
        return target;
    }

    User findPlatformTargetWithLock(UUID publicId) {
        User target = userRepository.findWithLockByPublicId(publicId).orElseThrow(UserNotFoundException::new);
        if (!provisioningHelper.isPlatformTarget(target.getRoles())) {
            throw new UserNotFoundException();
        }
        return target;
    }

    User findPlatformTargetWithLockIncludingDeleted(UUID publicId) {
        User target = userRepository.findWithLockByPublicIdIncludingDeleted(publicId)
                .orElseThrow(UserNotFoundException::new);
        if (!provisioningHelper.isPlatformTarget(target.getRoles())) {
            throw new UserNotFoundException();
        }
        return target;
    }

    void rejectSelfOrAdminTarget(User target, CurrentUser caller) {
        if (target.getPublicId().equals(caller.userId()) || target.getRoles().contains(Role.PLATFORM_ADMIN)) {
            recordAuthorizationDenied(caller, target.getPublicId().toString());
            throw new ForbiddenUserManagementException();
        }
    }

    void recordAuthorizationDenied(CurrentUser caller, String aggregateId) {
        if (caller != null && caller.userId() != null) {
            auditLogService.recordFailure(caller, IdentityConstants.PLATFORM_USER_AGGREGATE, aggregateId,
                    IdentityConstants.PLATFORM_AUTHORIZATION_DENIED,
                    IdentityConstants.PLATFORM_AUTHORIZATION_DENIED_SUMMARY);
        }
    }
}
