package com.pte.overview.internal.service.admin;

import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.shared.security.CurrentUser;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The admin sections one caller may see, computed from the per-section permission
 * checks. Its {@link #bucket()} (the sorted allowed section keys) is what the cache key
 * embeds, so two callers share cache entries only when they see exactly the same
 * sections, and no raw role ever reaches a key. A section the caller cannot see is
 * decided here, before any cache lookup or query runs.
 */
public final class AdminVisibility {

    private final Set<AdminSection> allowed;
    private final String bucket;

    private AdminVisibility(Set<AdminSection> allowed) {
        this.allowed = allowed;
        Set<String> keys = new TreeSet<>();
        allowed.forEach(section -> keys.add(section.key()));
        this.bucket = keys.isEmpty() ? OverviewConstants.CACHE_BUCKET_NONE
                : String.join(OverviewConstants.CACHE_BUCKET_SEPARATOR, keys);
    }

    public static AdminVisibility resolve(CurrentUser caller) {
        Set<AdminSection> allowed = EnumSet.noneOf(AdminSection.class);
        Arrays.stream(AdminSection.values()).filter(section -> section.isAllowedFor(caller)).forEach(allowed::add);
        return new AdminVisibility(allowed);
    }

    public boolean allows(AdminSection section) {
        return allowed.contains(section);
    }

    /** Sorted allowed section keys joined for use in a cache key, or {@code none}. */
    public String bucket() {
        return bucket;
    }
}
