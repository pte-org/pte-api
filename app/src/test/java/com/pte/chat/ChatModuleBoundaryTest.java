package com.pte.chat;

import com.pte.PteApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 boundary contract of the chat module: it may only lean on the public
 * root of {@code practice} (the entitlement gate) and on {@code shared}; nothing
 * may depend on chat, and {@code practice} must never depend back on it.
 */
class ChatModuleBoundaryTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(PteApplication.class);
    private static final String CHAT = "chat";

    @Test
    void chat_module_exists() {
        assertThat(MODULES.getModuleByName(CHAT)).isPresent();
    }

    @Test
    void chat_depends_only_on_practice_and_shared() {
        ApplicationModule chat = MODULES.getModuleByName(CHAT).orElseThrow();

        assertThat(dependencyNames(chat)).isSubsetOf("practice", "shared");
    }

    @Test
    void no_other_module_depends_on_chat() {
        Set<String> dependents = MODULES.stream()
                .filter(module -> !CHAT.equals(module.getIdentifier().toString()))
                .filter(module -> dependencyNames(module).contains(CHAT))
                .map(module -> module.getIdentifier().toString())
                .collect(Collectors.toSet());

        assertThat(dependents).isEmpty();
    }

    @Test
    void practice_has_no_dependency_on_chat() {
        ApplicationModule practice = MODULES.getModuleByName("practice").orElseThrow();

        assertThat(dependencyNames(practice)).doesNotContain(CHAT);
    }

    private static Set<String> dependencyNames(ApplicationModule module) {
        return module.getDirectDependencies(MODULES).uniqueModules()
                .map(dependency -> dependency.getIdentifier().toString())
                .collect(Collectors.toSet());
    }
}
