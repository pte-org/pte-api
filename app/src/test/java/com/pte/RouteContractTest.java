package com.pte;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the route contract the modulith collapse established (ADR-008):
 * every controller maps a BARE path, never one starting with {@code /api}.
 *
 * <p>The edge strips exactly one {@code /api} segment before proxying
 * ({@code deploy/api-routes.caddy}'s single {@code handle_path /api/*}), so a
 * controller that writes {@code /api} itself ends up needing {@code /api/api/...}
 * from a browser and 404s for every real caller. That failure is silent — no
 * startup error, no log line, just a dead endpoint — which is why it needs a
 * test rather than code review.
 *
 * <p>Static reflection over the classpath, no Spring context, same spirit as
 * {@link ModuleStructureTest}: fast, and it cannot be fooled by a context that
 * happens not to load the offending controller.
 */
class RouteContractTest {

    private static final String BASE_PACKAGE = "com.pte";

    private static List<Class<?>> restControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            String className = definition.getBeanClassName();
            if (className == null) {
                continue;
            }
            try {
                controllers.add(Class.forName(className));
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("Scanned a controller that cannot be loaded: " + className, ex);
            }
        }
        return controllers;
    }

    /**
     * Collects declared paths from the class-level mapping and from every
     * method mapping. {@code @GetMapping}/{@code @PostMapping}/etc. are all
     * meta-annotated with {@code @RequestMapping}, so
     * {@link AnnotatedElementUtils#findMergedAnnotation} resolves them
     * uniformly without listing each one.
     */
    private static List<String> declaredPaths(Class<?> controller) {
        List<String> paths = new ArrayList<>();

        // value() only — @RequestMapping declares path() as an @AliasFor of
        // value(), and findMergedAnnotation resolves the alias, so reading
        // both would just duplicate every entry.
        RequestMapping classMapping =
                AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        if (classMapping != null) {
            paths.addAll(List.of(classMapping.value()));
        }

        for (Method method : controller.getDeclaredMethods()) {
            RequestMapping methodMapping =
                    AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (methodMapping != null) {
                paths.addAll(List.of(methodMapping.value()));
            }
        }

        return paths;
    }

    @Test
    void scannerFindsTheControllers() {
        // Guards the reflection itself: a scanner that silently matches
        // nothing would make every other assertion here vacuously pass.
        assertThat(restControllers()).hasSizeGreaterThan(20);
    }

    @Test
    void noControllerDeclaresTheApiPrefixItself() {
        List<String> offenders = new ArrayList<>();

        for (Class<?> controller : restControllers()) {
            for (String path : declaredPaths(controller)) {
                if (path.equals("/api") || path.startsWith("/api/")) {
                    offenders.add(controller.getSimpleName() + " -> " + path);
                }
            }
        }

        assertThat(offenders)
                .as("""
                        These controllers declare the /api prefix the edge already strips \
                        (deploy/api-routes.caddy). Remove /api from the mapping — the browser \
                        still calls /api/<path>, Caddy strips it, and Spring must see the bare \
                        path. Leaving it produces a 404 with no error anywhere.""")
                .isEmpty();
    }

    @Test
    void everyControllerPathIsAbsolute() {
        List<String> offenders = new ArrayList<>();

        for (Class<?> controller : restControllers()) {
            for (String path : declaredPaths(controller)) {
                if (!path.isEmpty() && !path.startsWith("/")) {
                    offenders.add(controller.getSimpleName() + " -> " + path);
                }
            }
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void billingRoutesSitWhereTheFrontendExpectsThem() {
        // Pins the exact paths packages/api-client/src/requests/billing/*
        // targets (plans/quang-web-billing-integration Phase 2). Both sides
        // are plain strings with no shared type, so only a test keeps them
        // from drifting.
        Set<String> expected = Set.of(
                "/applications",
                "/admin/applications",
                "/plans",
                "/admin/plans",
                "/subscriptions",
                "/orders",
                "/license-codes",
                "/admin/license-codes",
                "/admin/settings",
                "/webhooks/payos");

        List<String> allBillingPaths = new ArrayList<>();
        for (Class<?> controller : restControllers()) {
            if (controller.getPackageName().startsWith("com.pte.billing")) {
                allBillingPaths.addAll(declaredPaths(controller));
            }
        }

        assertThat(allBillingPaths).containsAll(expected);
    }
}
