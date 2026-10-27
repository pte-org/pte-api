package com.pte;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exports the REST + STOMP surface (method, path, roles, request/response DTO field names) used by the
 * entity-consolidation plan to prove function parity. It only writes a file when the
 * {@code inventory.out} system property names one:
 * {@code ./mvnw.cmd -pl app test -Dtest=ApiInventoryExportTest -Dinventory.out=<file>}.
 */
class ApiInventoryExportTest {

    private static final String OUTPUT_PROPERTY = "inventory.out";
    private static final String BASE_PACKAGE = "com.pte";
    private static final int DTO_FIELD_DEPTH = 2;

    @Test
    void exportsEveryControllerRouteAndStompDestination() throws IOException {
        List<Map<String, Object>> endpoints = new ArrayList<>();
        List<Map<String, Object>> stomp = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            collect(controller, endpoints, stomp);
        }
        endpoints.sort(Comparator.comparing((Map<String, Object> e) -> (String) e.get("path"))
                .thenComparing(e -> (String) e.get("method")));

        assertThat(endpoints).isNotEmpty();
        assertThat(endpoints).allSatisfy(endpoint -> assertThat(endpoint.get("path")).isNotNull());

        String out = System.getProperty(OUTPUT_PROPERTY);
        if (out != null && !out.isBlank()) {
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("endpointCount", endpoints.size());
            document.put("stompCount", stomp.size());
            document.put("endpoints", endpoints);
            document.put("stompDestinations", stomp);
            Path target = Path.of(out);
            Files.createDirectories(target.toAbsolutePath().getParent());
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(target.toFile(), document);
        }
    }

    private static List<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(org.springframework.stereotype.Controller.class));
        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                controllers.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(definition.getBeanClassName(), ex);
            }
        }
        controllers.sort(Comparator.comparing(Class::getName));
        return controllers;
    }

    private static void collect(Class<?> controller, List<Map<String, Object>> endpoints,
            List<Map<String, Object>> stomp) {
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        String[] basePaths = classMapping == null ? new String[] {""} : paths(classMapping);
        PreAuthorize classAuth = AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);
        for (Method method : controller.getDeclaredMethods()) {
            MessageMapping message = method.getAnnotation(MessageMapping.class);
            if (message != null) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("controller", controller.getSimpleName());
                entry.put("destinations", Arrays.asList(message.value()));
                stomp.add(entry);
            }
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            PreAuthorize auth = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            String roles = auth != null ? auth.value() : classAuth != null ? classAuth.value() : null;
            String[] methodPaths = paths(mapping);
            for (String base : basePaths) {
                for (String methodPath : methodPaths.length == 0 ? new String[] {""} : methodPaths) {
                    for (String httpMethod : httpMethods(mapping)) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("method", httpMethod);
                        entry.put("path", base + methodPath);
                        entry.put("controller", controller.getSimpleName());
                        entry.put("handler", method.getName());
                        entry.put("authorization", roles);
                        entry.put("requestBody", requestBody(method));
                        entry.put("response", describe(responseType(method.getGenericReturnType()), DTO_FIELD_DEPTH));
                        endpoints.add(entry);
                    }
                }
            }
        }
    }

    private static String[] paths(RequestMapping mapping) {
        return mapping.path().length > 0 ? mapping.path() : mapping.value();
    }

    private static List<String> httpMethods(RequestMapping mapping) {
        return mapping.method().length == 0 ? List.of("ANY")
                : Arrays.stream(mapping.method()).map(Enum::name).toList();
    }

    private static Object requestBody(Method method) {
        for (int i = 0; i < method.getParameterCount(); i++) {
            if (method.getParameters()[i].isAnnotationPresent(RequestBody.class)) {
                return describe(method.getGenericParameterTypes()[i], DTO_FIELD_DEPTH);
            }
        }
        return null;
    }

    /** Unwraps ResponseEntity / ApiResponse / List / Page wrappers down to the payload type. */
    private static Type responseType(Type type) {
        while (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            if (raw.getName().startsWith("java.util.") && !raw.getSimpleName().endsWith("Map")
                    || raw.getSimpleName().equals("ResponseEntity")
                    || raw.getSimpleName().equals("ApiResponse")
                    || raw.getSimpleName().equals("PagedResult")
                    || raw.getSimpleName().equals("Page")) {
                type = parameterized.getActualTypeArguments()[0];
            } else {
                break;
            }
        }
        return type;
    }

    private static Object describe(Type type, int depth) {
        Class<?> raw = type instanceof Class<?> c ? c
                : type instanceof ParameterizedType p ? (Class<?>) p.getRawType() : Object.class;
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("type", raw.getSimpleName());
        if (depth == 0 || raw.isPrimitive() || raw.getName().startsWith("java.") || raw.isEnum()) {
            return description;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        if (raw.isRecord()) {
            for (RecordComponent component : raw.getRecordComponents()) {
                fields.put(component.getName(), fieldType(component.getGenericType(), depth - 1));
            }
        } else {
            for (Field field : raw.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.put(field.getName(), fieldType(field.getGenericType(), depth - 1));
                }
            }
        }
        if (!fields.isEmpty()) {
            description.put("fields", new TreeSet<>(fields.keySet()).stream()
                    .collect(LinkedHashMap::new, (m, k) -> m.put(k, fields.get(k)), Map::putAll));
        }
        return description;
    }

    private static Object fieldType(Type type, int depth) {
        Type payload = responseType(type);
        Object nested = describe(payload, depth);
        return payload == type ? nested : Map.of("collectionOf", nested);
    }
}
