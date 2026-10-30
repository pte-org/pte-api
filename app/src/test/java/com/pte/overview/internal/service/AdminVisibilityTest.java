package com.pte.overview.internal.service;

import com.pte.overview.internal.service.admin.AdminSection;
import com.pte.overview.internal.service.admin.AdminVisibility;
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

    /**
     * The authoritative table of the phase files (section -> required permission). Phase 4 added REVENUE
     * (commercial read); QUESTION_BANK has no operation, it is open to any valid platform caller (see
     * {@link #expected}).
     */
    private static final Map<AdminSection, PlatformOperation> REQUIRED = new EnumMap<>(Map.of(
            AdminSection.TENANTS, PlatformOperation.TENANT_READ,
            AdminSection.LEARNERS, PlatformOperation.TENANT_READ,
            AdminSection.RECENT_TENANTS, PlatformOperation.TENANT_READ,
            AdminSection.APPLICATIONS, PlatformOperation.APPLICATION_READ,
            AdminSection.LICENSES, PlatformOperation.LICENSE_MASKED_READ,
            AdminSection.SUBSCRIPTIONS, PlatformOperation.COMMERCIAL_READ,
            AdminSection.PLANS, PlatformOperation.PLAN_READ_ALL,
            AdminSection.SUPPORT, PlatformOperation.SUPPORT_READ,
            AdminSection.REVENUE, PlatformOperation.COMMERCIAL_READ));

    /** Phase 4 contract: every one of the 10 section keys, sorted, joined with '+'. */
    private static final String ALL_SECTIONS_BUCKET =
            "applications+learners+licenses+plans+questionBank+recentTenants+revenue+subscriptions+support+tenants";

    /** Phase 4 contract: academic callers (and the legacy author alias) see only the question bank. */
    private static final String ACADEMIC_BUCKET = "questionBank";

    private static CurrentUser platform(String... roles) {
        return new CurrentUser(USER, null, List.of(roles));
    }

    /** Expectation derived from the real policy: an operation per section, or "valid platform caller" for QUESTION_BANK. */
    private static boolean expected(AdminSection section, CurrentUser caller) {
        if (section == AdminSection.QUESTION_BANK) {
            return PlatformOperationPolicy.isPlatformCaller(caller);
        }
        return PlatformOperationPolicy.can(caller, REQUIRED.get(section));
    }

    @Test
    void theTableCoversEverySectionExactlyOnce() {
        // 9 sections need an operation; the question bank is the only one gated by "valid platform caller".
        assertThat(REQUIRED.keySet()).doesNotContain(AdminSection.QUESTION_BANK)
                .containsExactlyInAnyOrder(Arrays.stream(AdminSection.values())
                        .filter(section -> section != AdminSection.QUESTION_BANK).toArray(AdminSection[]::new));
        assertThat(AdminSection.values()).hasSize(10);
        assertThat(Arrays.stream(AdminSection.values()).map(AdminSection::key))
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder("tenants", "learners", "recentTenants", "applications", "licenses",
                        "subscriptions", "plans", "support", "revenue", "questionBank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_MANAGER", "ACADEMIC_MANAGER", "ACADEMIC_STAFF",
            "PLATFORM_AUTHOR"})
    void everySectionFollowsTheOperationPolicyForEveryPlatformRole(String role) {
        CurrentUser caller = platform(role);
        AdminVisibility visibility = AdminVisibility.resolve(caller);

        for (AdminSection section : AdminSection.values()) {
            boolean expected = expected(section, caller);
            assertThat(visibility.allows(section)).as("%s for %s", section, role).isEqualTo(expected);
            assertThat(section.isAllowedFor(caller)).as("%s.isAllowedFor %s", section, role).isEqualTo(expected);
        }
    }

    @Test
    void revenueRequiresCommercialReadAndTheQuestionBankOnlyAValidPlatformCaller() {
        assertThat(AdminSection.REVENUE.key()).isEqualTo("revenue");
        assertThat(AdminSection.QUESTION_BANK.key()).isEqualTo("questionBank");
        // PLATFORM_ADMIN / PLATFORM_MANAGER hold COMMERCIAL_READ, the academic roles do not.
        assertThat(PlatformOperationPolicy.can(platform("PLATFORM_ADMIN"), PlatformOperation.COMMERCIAL_READ)).isTrue();
        assertThat(PlatformOperationPolicy.can(platform("PLATFORM_MANAGER"), PlatformOperation.COMMERCIAL_READ)).isTrue();
        for (String academic : List.of("ACADEMIC_MANAGER", "ACADEMIC_STAFF", "PLATFORM_AUTHOR")) {
            assertThat(PlatformOperationPolicy.can(platform(academic), PlatformOperation.COMMERCIAL_READ))
                    .as("COMMERCIAL_READ for %s", academic).isFalse();
            assertThat(AdminSection.REVENUE.isAllowedFor(platform(academic))).as("REVENUE for %s", academic).isFalse();
            assertThat(AdminSection.QUESTION_BANK.isAllowedFor(platform(academic))).as("QUESTION_BANK for %s", academic)
                    .isTrue();
        }
    }

    @Test
    void platformAdminSeesEverySection() {
        AdminVisibility visibility = AdminVisibility.resolve(platform("PLATFORM_ADMIN"));

        assertThat(Arrays.stream(AdminSection.values())).allMatch(visibility::allows);
        assertThat(visibility.bucket()).isEqualTo(ALL_SECTIONS_BUCKET);
    }

    @Test
    void platformManagerSeesEverySectionBecauseTheManagerPolicyDelegatesEveryRequiredOperation() {
        AdminVisibility visibility = AdminVisibility.resolve(platform("PLATFORM_MANAGER"));

        assertThat(Arrays.stream(AdminSection.values())).allMatch(visibility::allows);
        assertThat(visibility.bucket()).isEqualTo(ALL_SECTIONS_BUCKET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACADEMIC_MANAGER", "ACADEMIC_STAFF", "PLATFORM_AUTHOR"})
    void academicRolesAndTheLegacyAuthorAliasSeeOnlyTheQuestionBankSection(String role) {
        AdminVisibility visibility = AdminVisibility.resolve(platform(role));

        // Phase 4 contract change: the bucket was "none" (nothing visible), it is now the question bank key.
        assertThat(visibility.allows(AdminSection.QUESTION_BANK)).isTrue();
        assertThat(Arrays.stream(AdminSection.values()).filter(section -> section != AdminSection.QUESTION_BANK))
                .noneMatch(visibility::allows);
        assertThat(visibility.bucket()).isEqualTo(ACADEMIC_BUCKET).isNotEqualTo(OverviewConstants.CACHE_BUCKET_NONE);
    }

    @Test
    void theAcademicBucketAndTheOperationsBucketAreDifferentSoPayloadsCannotCrossOver() {
        String operations = AdminVisibility.resolve(platform("PLATFORM_ADMIN")).bucket();

        for (String role : List.of("ACADEMIC_MANAGER", "ACADEMIC_STAFF", "PLATFORM_AUTHOR")) {
            String academic = AdminVisibility.resolve(platform(role)).bucket();
            assertThat(academic).as(role).isNotEqualTo(operations).isEqualTo("questionBank");
            // A shared prefix/suffix confusion would let a key collide: the academic bucket is a strict subset.
            assertThat(operations.split("\\+")).contains(academic);
            assertThat(operations).isNotEqualTo(academic);
        }
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
