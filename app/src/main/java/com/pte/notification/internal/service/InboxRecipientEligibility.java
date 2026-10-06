package com.pte.notification.internal.service;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.internal.repository.InboxDeliveryRecord;
import com.pte.tenancy.TenancyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxRecipientEligibility {
    private final IdentityService identity;
    private final TenancyService tenancy;
    public InboxRecipientEligibility(IdentityService identity, TenancyService tenancy) {
        this.identity = identity;
        this.tenancy = tenancy;
    }

    /** Tenant -> user -> delivery -> recipient stream; account deactivation cannot race insertion. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockEligible(InboxDeliveryRecord record) {
        if (record.type() == InboxNotificationType.APPLICATION_SUBMITTED
                || record.type() == InboxNotificationType.SUPPORT_TICKET_SUBMITTED) {
            return record.tenantId() == null && identity.lockActiveRoleMember(record.recipientPublicId(), null, Role.PLATFORM_ADMIN);
        }
        return record.tenantId() != null && tenancy.lockActiveTenant(record.tenantId())
                && identity.lockActiveRoleMember(record.recipientPublicId(), record.tenantId(), Role.HOST_ADMIN);
    }
}
