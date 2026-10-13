package com.pte.overview.internal.service;

import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The section-to-permission table and the computed visibility bucket. Expectations are DERIVED from the
 * real {@link PlatformOperationPolicy} (using the operation each section requires per the phase table), never
 * assumed per role; a few outcomes are additionally pinned because they are the documented contract.
 */
class AdminVisibilityTest {

    private static final UUID USER = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** The authoritative table of the phase file (section -> required permission). */
    private static final Map<AdminSection, PlatformOperation> REQUIRED = new EnumMap<>(Map.of(
            AdminSection.TENANTS, PlatformOperation.TENANT_READ,
            AdminSection.LEARNERS, PlatformOperation.TENANT_READ,
            AdminSection.RECENT_TENANTS, PlatformOperation.TENANT_READ,
            AdminSection.APPLICATIONS, PlatformOperation.APPLICATION_READ,
            AdminSection.LICENSES, PlatformOperation.LICENSE_MASKED_READ,
            AdminSection.SUBSCRIPTIONS, PlatformOperation.COMMERCIAL_READ,
            AdminSection.PLANS, PlatformOperation.PLAN_READ_ALL,
            AdminSection.SUPPORT, PlatformOperation.SUPPORT_READ));

    private static final String ALL_SECTIONS_BUCKET =
            "applications+learners+licenses+plans+recentTenants+subscriptions+support+tenants";

    private static CurrentUser platform(String... roles) {
        return new CurrentUser(USER, null, List.of(roles));
    }

    @Test
    void theTableCoversEverySectionExactlyOnce() {
        assertThat(REQUIRED.keySet()).containsExactlyInAnyOrder(AdminSection.values());
        assertThat(Arrays.stream(AdminSection.values()).map(AdminSection::key))
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder("tenants", "learners", "recentTenants", "applications", "licenses",
                        "subscriptions", "plans", "support");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_MANAGER", "ACADEMIC_MANAGER", "ACADEMIC_STAFF",
            "PLATFORM_AUTHOR"})
    void everySectionFollowsTheOperationPolicyForEveryPlatformRole(String role) {
        CurrentUser caller = platform(role);
        AdminVisibility visibility = AdminVisibility.resolve(caller);

        for (AdminSection section : AdminSection.values()) {
            boolean expected = PlatformOperationPolicy.can(caller, REQUIRED.get(section));
            assertThat(visibility.allows(section)).as("%s for %s", section, role).isEqualTo(expected);
            assertThat(section.isAllowedFor(caller)).as("%s.isAllowedFor %s", section, role).isEqualTo(expected);
        }
    }

    @Test
    void platformAdminSeesEverySection() {
        AdminVisibility visibility = AdminVisibility.resolve(platform("PLATFORM_ADMIN"));

        assertThat(Arrays.stream(AdminSection.values())).allMatch(visibility::allows);
        assertThat(visibility.bucket()).isEqualTo(ALL_SECTIONS_BUCKET);
    }

    @Test
    void platformManagerSeesEverySectionBecauseTheManagerPolicyDelegatesAllEightOperations() {
        AdminVisibility visibility = AdminVisibility.resolve(platform("PLATFORM_MANAGER"));

        assertThat(Arrays.stream(AdminSection.values())).allMatch(visibility::allows);
        assertThat(visibility.bucket()).isEqualTo(ALL_SECTIONS_BUCKET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACADEMIC_MANAGER", "ACADEMIC_STAFF", "PLATFORM_AUTHOR"})
    void academicRolesAndTheLegacyAuthorAliasSeeNoPhaseThreeSection(String role) {
        AdminVisibility visibility = AdminVisibility.resolve(platform(role));

        assertThat(Arrays.stream(AdminSection.values())).noneMatch(visibility::allows);
        assertThat(visibility.bucket()).isEqualTo(OverviewConstants.CACHE_BUCKET_NONE).isEqualTo("none");
    }

    @Test
    void legacyPlatformAuthorCanonicalizesToAcademicStaffAndShareTheirVisibility() {
        AdminVisibility legacy = AdminVisibility.resolve(platform("PLATFORM_AUTHOR"));
        AdminVisibility staff = AdminVisibility.resolve(platform("ACADEMIC_STAFF"));

        assertThat(legacy.bucket()).isEqualTo(staff.bucket());
        assertThat(platform("PLATFORM_AUTHOR").roles()).containsExactly("ACADEMIC_STAFF");
    }

    @Test
    void adminAndManagerShareOneBucketBecauseTheySeeTheSameSections() {
        assertThat(AdminVisibility.resolve(platform("PLATFORM_ADMIN")).bucket())
                .isEqualTo(AdminVisibility.resolve(platform("PLATFORM_MANAGER")).bucket());
    }

    @Test
    void theBucketIsTheSortedAllowedSectionKeysJoinedWithPlus() {
        String expected = Arrays.stream(AdminSection.values()).map(AdminSection::key).sorted()
                .collect(Collectors.joining("+"));

        assertThat(AdminVisibility.resolve(platform("PLATFORM_ADMIN")).bucket()).isEqualTo(expected);
        assertThat(OverviewConstants.CACHE_BUCKET_SEPARATOR).isEqualTo("+");
    }

    @Test
    void theBucketIsDeterministicAcrossCallsAndRoleOrder() {
        String first = AdminVisibility.resolve(platform("PLATFORM_ADMIN")).bucket();

        assertThat(AdminVisibility.resolve(platform("PLATFORM_ADMIN")).bucket()).isEqualTo(first);
        assertThat(AdminVisibility.resolve(platform("ACADEMIC_STAFF", "PLATFORM_MANAGER")).bucket()).isEqualTo(first);
        assertThat(AdminVisibility.resolve(platform("PLATFORM_MANAGER", "ACADEMIC_STAFF")).bucket()).isEqualTo(first);
    }

    @Test
    void theBucketNeverContainsARawRoleName() {
        for (String role : List.of("PLATFORM_ADMIN", "PLATFORM_MANAGER", "ACADEMIC_MANAGER", "ACADEMIC_STAFF")) {
            assertThat(AdminVisibility.resolve(platform(role)).bucket()).doesNotContain("PLATFORM")
                    .doesNotContain("ACADEMIC").doesNotContain(":");
        }
    }

    @Test
    void aPlatformRoleCarryingATenantIdHasAnInvalidScopeAndSeesNothing() {
        for (String role : List.of("PLATFORM_ADMIN", "PLATFORM_MANAGER")) {
            AdminVisibility visibility = AdminVisibility.resolve(new CurrentUser(USER, TENANT, List.of(role)));

            assertThat(Arrays.stream(AdminSection.values())).noneMatch(visibility::allows);
            assertThat(visibility.bucket()).isEqualTo("none");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOST_ADMIN", "PROCTOR", "EXAMINER", "STUDENT"})
    void tenantRolesSeeNothing(String role) {
        AdminVisibility withTenant = AdminVisibility.resolve(new CurrentUser(USER, TENANT, List.of(role)));
        AdminVisibility withoutTenant = AdminVisibility.resolve(platform(role));

        assertThat(Arrays.stream(AdminSection.values())).noneMatch(withTenant::allows).noneMatch(withoutTenant::allows);
        assertThat(withTenant.bucket()).isEqualTo("none");
        assertThat(withoutTenant.bucket()).isEqualTo("none");
    }

    @Test
    void aCallerWithoutRolesOrWithoutAUserIdSeesNothing() {
        assertThat(AdminVisibility.resolve(platform()).bucket()).isEqualTo("none");
        assertThat(AdminVisibility.resolve(new CurrentUser(null, null, List.of("PLATFORM_ADMIN"))).bucket())
                .isEqualTo("none");
        assertThat(AdminVisibility.resolve(null).bucket()).isEqualTo("none");
    }

    @Test
    void mixingPlatformAndTenantRolesIsAnInvalidScopeAndSeesNothing() {
        AdminVisibility visibility = AdminVisibility.resolve(platform("PLATFORM_ADMIN", "STUDENT"));

        assertThat(visibility.bucket()).isEqualTo("none");
    }
}
