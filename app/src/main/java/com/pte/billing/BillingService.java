package com.pte.billing;

import org.springframework.stereotype.Service;

/**
 * The only door other modules use to reach {@code billing}. Empty for now —
 * no other module needs anything from billing yet (Phase 2 only has billing
 * calling OUT to {@code tenancy}/{@code identity}, never the other way).
 * Add methods here when an actual caller needs one; don't grow this
 * speculatively ahead of that (see {@code identity.IdentityService}'s own
 * javadoc for the same rule).
 */
@Service
public class BillingService {
}
