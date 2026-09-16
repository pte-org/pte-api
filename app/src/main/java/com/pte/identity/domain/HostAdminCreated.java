package com.pte.identity.domain;

/**
 * Result of
 * {@link com.pte.identity.internal.service.IdentityService#createHostAdmin}.
 * Lives
 * in {@code domain}, not the module root — this package already carries
 * {@code @NamedInterface} (see this package's {@code package-info.java}) for
 * exactly this reason: {@link User}/{@link Role} are exposed the same way
 * for {@code notification} (Phase 09) to consume. A plain value type sitting
 * in the bare module-root package next to the {@code IdentityService} facade
 * was NOT enough on its own — verified by direct experiment 2026-09-16 that
 * reproduced {@code MODULITH_TYPE_REF_VIOLATION} deterministically there,
 * immune to reordering/retrying. Putting it here, alongside the other
 * already-proven-working exposed types, is what actually clears it.
 *
 * <p>
 * {@code generatedPassword} is shown to the caller exactly once — never
 * stored in the clear, never re-fetchable.
 */
public record HostAdminCreated(User user, String generatedPassword) {
}
