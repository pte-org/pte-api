package com.pte.identity.internal.service;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.User;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves an account only through its membership in the caller's organization. A person who belongs to another
 * organization (or whose membership was removed) is reported as not found, never as forbidden, so ids never leak
 * across organizations.
 */
@Service
public class OrganizationMemberDirectory {

    private final OrganizationMemberRepository memberRepository;

    public OrganizationMemberDirectory(OrganizationMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationMember requireMember(UUID organizationId, UUID userPublicId) {
        return find(organizationId, userPublicId).orElseThrow(UserNotFoundException::new);
    }

    /** Same as {@link #requireMember} but holds a row lock for the caller's transaction. */
    @Transactional
    public OrganizationMember requireMemberForUpdate(UUID organizationId, UUID userPublicId) {
        if (organizationId == null || userPublicId == null) {
            throw new UserNotFoundException();
        }
        return memberRepository.findWithLockByUserAndOrganization(userPublicId, organizationId)
                .orElseThrow(UserNotFoundException::new);
    }

    /**
     * Whether the organization alone owns the account behind this membership: the account has no roles of its own
     * (platform or practice) and no membership anywhere else. Only then may the organization's Host change the
     * shared credential or profile; otherwise the change would reach other organizations or a self-registered
     * account.
     */
    @Transactional(readOnly = true)
    public boolean isExclusivelyOwned(OrganizationMember member) {
        User user = member.getUser();
        return user.getRoles().isEmpty()
                && memberRepository.findByUser_IdAndDeletedFalse(user.getId()).size() == 1;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<OrganizationMember> find(UUID organizationId, UUID userPublicId) {
        if (organizationId == null || userPublicId == null) {
            return java.util.Optional.empty();
        }
        return memberRepository.findActiveByUserAndOrganization(userPublicId, organizationId);
    }
}
