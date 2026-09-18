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
 * Guards the public REST contract of the modular monolith.
 *
 * <p>Public controllers own the complete {@code /api/v1} prefix. Nginx is an
 * edge reverse proxy only and must forward that prefix unchanged. Controllers
 * explicitly marked as internal retain their private {@code /internal/**}
 * surface and are not part of the public route inventory.
 */
class RouteContractTest {

    private static final String BASE_PACKAGE = "com.pte";
    private static final String PUBLIC_PREFIX = "/api/v1";

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

    private static List<String> mappingValues(Class<?> type) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
        if (mapping == null || mapping.value().length == 0) {
            return List.of("");
        }
        return List.of(mapping.value());
    }

    private static List<String> methodMappingValues(Method method) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            return List.of();
        }
        return mapping.value().length == 0 ? List.of("") : List.of(mapping.value());
    }

    private static List<String> effectivePaths(Class<?> controller) {
        List<String> classPaths = mappingValues(controller);
        List<String> paths = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            List<String> methodPaths = methodMappingValues(method);
            if (methodPaths.isEmpty()) {
                continue;
            }
            for (String classPath : classPaths) {
                for (String methodPath : methodPaths) {
                    paths.add(join(classPath, methodPath));
                }
            }
        }
        return paths.isEmpty() ? classPaths : paths;
    }

    private static String join(String classPath, String methodPath) {
        if (classPath.isEmpty()) {
            return methodPath;
        }
        if (methodPath.isEmpty()) {
            return classPath;
        }
        return classPath.endsWith("/")
                ? classPath.substring(0, classPath.length() - 1) + methodPath
                : classPath + methodPath;
    }

    private static boolean isInternal(Class<?> controller) {
        return controller.getSimpleName().startsWith("Internal");
    }

    @Test
    void scannerFindsTheControllers() {
        assertThat(restControllers()).hasSizeGreaterThan(20);
    }

    @Test
    void everyPublicControllerOwnsTheVersionedApiPrefix() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> controller : restControllers()) {
            if (!isInternal(controller)) {
                for (String path : effectivePaths(controller)) {
                    if (!path.equals(PUBLIC_PREFIX) && !path.startsWith(PUBLIC_PREFIX + "/")) {
                        offenders.add(controller.getSimpleName() + " -> " + path);
                    }
                }
            }
        }

        assertThat(offenders)
                .as("Every public Spring MVC mapping must start with /api/v1; Nginx does not rewrite paths")
                .isEmpty();
    }

    @Test
    void publicApiDoesNotUseAdminAsAResourceNamespace() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> controller : restControllers()) {
            if (!isInternal(controller)) {
                for (String path : effectivePaths(controller)) {
                    if (path.equals("/admin") || path.startsWith("/admin/")) {
                        offenders.add(controller.getSimpleName() + " -> " + path);
                    }
                }
            }
        }

        assertThat(offenders).as("Authorization belongs in Spring Security, not an /admin API namespace").isEmpty();
    }

    @Test
    void internalControllersRemainOutsideThePublicContract() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> controller : restControllers()) {
            if (isInternal(controller)) {
                for (String path : effectivePaths(controller)) {
                    if (!path.equals("/internal") && !path.startsWith("/internal/")) {
                        offenders.add(controller.getSimpleName() + " -> " + path);
                    }
                }
            }
        }

        assertThat(offenders).as("Internal controllers must not become public API routes").isEmpty();
    }

    @Test
    void billingRoutesUseTheVersionedResourceContract() {
        Set<String> expected = Set.of(
                "/api/v1/applications",
                "/api/v1/applications/{publicId}/approval",
                "/api/v1/applications/{publicId}/rejection",
                "/api/v1/plans",
                "/api/v1/plans/{publicId}/activation",
                "/api/v1/orders",
                "/api/v1/subscriptions",
                "/api/v1/license-codes",
                "/api/v1/license-code-redemptions",
                "/api/v1/settings",
                "/api/v1/webhooks/payos");

        List<String> billingPaths = new ArrayList<>();
        for (Class<?> controller : restControllers()) {
            if (controller.getPackageName().startsWith("com.pte.billing")) {
                billingPaths.addAll(effectivePaths(controller));
            }
        }

        assertThat(billingPaths).containsAll(expected);
    }
}
