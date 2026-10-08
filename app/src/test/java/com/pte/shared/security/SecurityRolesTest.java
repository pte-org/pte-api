package com.pte.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityRolesTest {

    @Test
    void canonicalizeRoles_mapsLegacyAuthorOnceAndPreservesOrder() {
        List<String> roles = SecurityRoles.canonicalizeRoles(
                List.of("PLATFORM_AUTHOR", "ACADEMIC_STAFF", "PLATFORM_MANAGER"));

        assertThat(roles).containsExactly("ACADEMIC_STAFF", "PLATFORM_MANAGER");
    }

    @Test
    void currentUser_hasRoleTreatsLegacyAuthorAsAcademicStaff() {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_AUTHOR"));

        assertThat(currentUser.roles()).containsExactly("ACADEMIC_STAFF");
        assertThat(currentUser.hasRole("ACADEMIC_STAFF")).isTrue();
        assertThat(currentUser.hasRole("PLATFORM_AUTHOR")).isTrue();
    }

    @Test
    void roleScopePredicatesSeparatePlatformAndTenantRoles() {
        assertThat(SecurityRoles.isPlatformRole("ACADEMIC_MANAGER")).isTrue();
        assertThat(SecurityRoles.isPlatformRole("PLATFORM_AUTHOR")).isTrue();
        assertThat(SecurityRoles.isTenantRole("HOST_ADMIN")).isTrue();
        assertThat(SecurityRoles.isTenantRole("ACADEMIC_STAFF")).isFalse();
        assertThat(SecurityRoles.isPlatformRole("STUDENT")).isFalse();
    }

    @Test
    void rolesConverter_keepsLegacyAuthorityForExistingControllerPolicies() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim(SecurityClaims.ROLES, List.of("PLATFORM_AUTHOR"))
                .build();

        Authentication authentication = ResourceServerJwt.rolesConverter().convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains("ROLE_ACADEMIC_STAFF", "ROLE_PLATFORM_AUTHOR");
    }
}
