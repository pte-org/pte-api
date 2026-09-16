package com.pte;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the module boundaries plan.md's FR-02/FR-02b require: every business
 * module's internals stay behind {@code internal/}, and cross-module access only
 * goes through what a module exposes at its own package root. Fails the build the
 * moment a later phase adds a shortcut (e.g. {@code attempt} reaching straight into
 * {@code scoring.internal.ScoringJobRepository}) instead of going through a public
 * service.
 */
class ModuleStructureTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(PteApplication.class);

    @Test
    void modules_have_no_boundary_violations() {
        MODULES.verify();
    }

    @Test
    void all_twelve_business_modules_plus_shared_are_detected() {
        // getName() is deprecated since Modulith 1.3 in favor of getIdentifier().
        // "shared" is included on purpose: it is a real, detected module (declared
        // OPEN rather than absent — see shared/package-info.java), not one of the
        // 12 business modules. Listing it here keeps this assertion honest about
        // what ApplicationModules.of() actually returns, rather than silently
        // filtering it out.
        assertThat(MODULES.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder(
                        "identity", "tenancy", "enrollment", "itembank", "assessment", "session",
                        "attempt", "scoring", "proctoring", "reporting", "media", "notification",
                        "scoretemplate", "shared");
        // "session" is now implemented (Phase 06); attempt/scoring/proctoring/
        // reporting/notification still exist only as empty package-info.java
        // scaffolds from Phase 01 until their own phase ports real code.
    }

    /** Sinh sơ đồ PlantUML vào target/spring-modulith-docs — dùng cho báo cáo đồ án. */
    @Test
    void writes_module_diagram() {
        new Documenter(MODULES).writeModulesAsPlantUml();
    }
}
