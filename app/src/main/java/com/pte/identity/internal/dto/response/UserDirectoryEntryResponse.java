package com.pte.identity.internal.dto.response;

import com.pte.identity.domain.User;

import java.util.UUID;

/**
 * Minimal, unfiltered tenant-user identity for display purposes (e.g. resolving an
 * Audit Log actor's name). Unlike {@link UserResponse} via {@code GET /users}, this is
 * NOT scoped by {@code canManageTarget} — a HOST_ADMIN cannot "manage" its own or a peer
 * HOST_ADMIN account, so that endpoint omits HOST_ADMIN users entirely, which made every
 * admin-performed action show as an "Unknown" actor.
 */
public record UserDirectoryEntryResponse(UUID publicId, String fullName) {

    /**
     * The entry for a user. An account created without a full name (seed and bootstrap accounts) is shown by its
     * username, so its actions never read as an unknown actor.
     */
    public static UserDirectoryEntryResponse from(User user) {
        String name = user.getFullName();
        return new UserDirectoryEntryResponse(user.getPublicId(),
                name == null || name.isBlank() ? user.getUsername() : name);
    }
}
