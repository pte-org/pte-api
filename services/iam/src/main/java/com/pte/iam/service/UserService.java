package com.pte.iam.service;

import com.pte.common.security.CurrentUser;
import com.pte.iam.constant.IamConstants;
import com.pte.iam.domain.LoginHash;
import com.pte.iam.domain.User;
import com.pte.iam.domain.enums.Role;
import com.pte.iam.domain.event.UserCreatedEvent;
import com.pte.iam.domain.event.UserPasswordResetEvent;
import com.pte.iam.domain.event.UserSuspendedEvent;
import com.pte.iam.domain.exception.EmailAlreadyUsedException;
import com.pte.iam.domain.exception.UserNotFoundException;
import com.pte.iam.dto.request.CreateUserRequest;
import com.pte.iam.dto.request.ResetPasswordRequest;
import com.pte.iam.dto.response.UserResponse;
import com.pte.iam.mapper.UserMapper;
import com.pte.iam.messaging.outbox.OutboxWriter;
import com.pte.iam.repository.LoginHashRepository;
import com.pte.iam.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * User provisioning + lookup, tenant-isolated. A host creates users only within
 * its own tenant; a platform admin can target a tenant. Creation writes a
 * {@code UserCreated} outbox row in the SAME transaction as the insert (ADR-002).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final LoginHashRepository loginHashRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProvisioningHelper provisioningHelper;
    private final OutboxWriter outboxWriter;

    public UserService(UserRepository userRepository, LoginHashRepository loginHashRepository,
                       PasswordEncoder passwordEncoder, UserProvisioningHelper provisioningHelper,
                       OutboxWriter outboxWriter) {
        this.userRepository = userRepository;
        this.loginHashRepository = loginHashRepository;
        this.passwordEncoder = passwordEncoder;
        this.provisioningHelper = provisioningHelper;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, CurrentUser caller) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException();
        }
        UUID tenantId = provisioningHelper.resolveTargetTenant(caller, request.tenantId());
        Set<Role> roles = provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles());

        User user = new User();
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setTenantId(tenantId);
        user.setRoles(roles);
        User saved = userRepository.save(user);

        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(saved.getId());
        loginHash.setHash(passwordEncoder.encode(request.password()));
        loginHashRepository.save(loginHash);

        outboxWriter.write(IamConstants.AGGREGATE_USER, saved.getPublicId().toString(),
                IamConstants.EVENT_USER_CREATED,
                new UserCreatedEvent(saved.getPublicId(), saved.getEmail(), tenantId, roles.stream().map(Role::name).toList()),
                tenantId);

        return UserMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID publicId, CurrentUser caller) {
        return UserMapper.toResponse(findScoped(publicId, caller));
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser caller) {
        User user = userRepository.findByPublicId(caller.userId())
                .orElseThrow(UserNotFoundException::new);
        return UserMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByTenant(CurrentUser caller) {
        UUID tenantId = caller.tenantId();
        if (tenantId == null) {
            return List.of();
        }
        return userRepository.findByTenantId(tenantId).stream().map(UserMapper::toResponse).toList();
    }

    @Transactional
    public UserResponse suspend(UUID publicId, CurrentUser caller) {
        User user = findScoped(publicId, caller);
        user.suspend();
        outboxWriter.write(IamConstants.AGGREGATE_USER, user.getPublicId().toString(),
                IamConstants.EVENT_USER_SUSPENDED,
                new UserSuspendedEvent(user.getPublicId(), user.getTenantId()), user.getTenantId());
        return UserMapper.toResponse(user);
    }

    @Transactional
    public UserResponse resetPassword(UUID publicId, ResetPasswordRequest request, CurrentUser caller) {
        User user = userRepository.findByPublicId(publicId).orElseThrow(UserNotFoundException::new);
        LoginHash loginHash = loginHashRepository.findByUserId(user.getId())
                .orElseThrow(UserNotFoundException::new);
        loginHash.setHash(passwordEncoder.encode(request.newPassword()));
        loginHashRepository.save(loginHash);

        outboxWriter.write(IamConstants.AGGREGATE_USER, user.getPublicId().toString(),
                IamConstants.EVENT_USER_PASSWORD_RESET,
                new UserPasswordResetEvent(user.getPublicId(), caller.userId(), user.getTenantId()),
                user.getTenantId());

        return UserMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listForTenant(UUID tenantId) {
        return userRepository.findByTenantId(tenantId).stream().map(UserMapper::toResponse).toList();
    }

    private User findScoped(UUID publicId, CurrentUser caller) {
        if (caller.isPlatformUser()) {
            return userRepository.findByPublicId(publicId).orElseThrow(UserNotFoundException::new);
        }
        return userRepository.findByPublicIdAndTenantId(publicId, caller.tenantId())
                .orElseThrow(UserNotFoundException::new);
    }
}
