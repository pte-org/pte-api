package com.pte.itembank.internal.generation.worker;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationCallerResolverTest {

    private final IdentityService identity = mock(IdentityService.class);
    private final GenerationCallerResolver resolver = new GenerationCallerResolver(identity);
    private final UUID id = UUID.randomUUID();

    private User user(UserStatus status, boolean deleted, Role... roles) {
        User user = new User();
        user.setPublicId(id);
        user.setStatus(status);
        user.setDeleted(deleted);
        user.getRoles().addAll(java.util.List.of(roles));
        return user;
    }

    @Test
    void anActivePlatformAdminRunsAsAPlatformAdminWithoutATenant() {
        when(identity.findById(id)).thenReturn(Optional.of(user(UserStatus.ACTIVE, false, Role.PLATFORM_ADMIN)));

        Optional<CurrentUser> caller = resolver.resolve(id);

        assertThat(caller).isPresent();
        assertThat(caller.get().userId()).isEqualTo(id);
        assertThat(caller.get().tenantId()).isNull();
        assertThat(caller.get().hasRole("PLATFORM_ADMIN")).isTrue();
    }

    @Test
    void aSuspendedAdminIsNotResolved() {
        when(identity.findById(id)).thenReturn(Optional.of(user(UserStatus.SUSPENDED, false, Role.PLATFORM_ADMIN)));

        assertThat(resolver.resolve(id)).isEmpty();
    }

    @Test
    void aDeletedAdminIsNotResolved() {
        when(identity.findById(id)).thenReturn(Optional.of(user(UserStatus.ACTIVE, true, Role.PLATFORM_ADMIN)));

        assertThat(resolver.resolve(id)).isEmpty();
    }

    @Test
    void aDemotedAdminIsNotResolved() {
        when(identity.findById(id)).thenReturn(Optional.of(user(UserStatus.ACTIVE, false, Role.ACADEMIC_STAFF)));

        assertThat(resolver.resolve(id)).isEmpty();
    }

    @Test
    void anUnknownUserIsNotResolved() {
        when(identity.findById(id)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(id)).isEmpty();
    }
}
