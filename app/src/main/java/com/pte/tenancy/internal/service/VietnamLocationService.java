package com.pte.tenancy.internal.service;

import com.pte.tenancy.internal.dto.response.LocationOptionResponse;
import com.pte.tenancy.internal.exception.InvalidLocationSelectionException;
import com.pte.tenancy.internal.config.VietnamLocationProperties;
import com.pte.tenancy.internal.exception.LocationCatalogUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the provider boundary for Vietnam administrative locations. The UI only
 * talks to the host API, while this service caches the provider catalog and
 * validates parent-child selections before they are persisted.
 */
@Service
public class VietnamLocationService {

    private static final String PROVINCES_PATH = "/p";
    private static final String WARDS_PATH = "/p/{provinceCode}";

    private final RestClient restClient;
    private final VietnamLocationProperties properties;
    private final JsonMapper jsonMapper;
    private final Map<String, CachedOptions> wardCache = new ConcurrentHashMap<>();
    private volatile CachedOptions provinceCache;
    private final Clock clock;

    public VietnamLocationService(@Qualifier("vietnamLocationRestClient") RestClient restClient,
            VietnamLocationProperties properties, JsonMapper jsonMapper,
            Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public List<LocationOptionResponse> listProvinces() {
        CachedOptions current = provinceCache;
        if (isFresh(current)) {
            return current.options();
        }
        synchronized (this) {
            current = provinceCache;
            if (isFresh(current)) {
                return current.options();
            }
            List<LocationOptionResponse> options = parseOptions(fetch(PROVINCES_PATH), false);
            provinceCache = new CachedOptions(options, clock.millis());
            return options;
        }
    }

    public List<LocationOptionResponse> listWards(String provinceCode) {
        String normalizedProvinceCode = normalizeRequired(provinceCode);
        LocationOptionResponse province = find(listProvinces(), normalizedProvinceCode);
        if (province == null) {
            throw new InvalidLocationSelectionException();
        }
        return cachedWards(province.code());
    }

    public ResolvedLocation resolve(String provinceCode, String wardCode) {
        String normalizedProvinceCode = normalizeOptional(provinceCode);
        String normalizedWardCode = normalizeOptional(wardCode);
        if (normalizedProvinceCode == null && normalizedWardCode == null) {
            return ResolvedLocation.empty();
        }
        if (normalizedProvinceCode == null) {
            throw new InvalidLocationSelectionException();
        }

        LocationOptionResponse province = find(listProvinces(), normalizedProvinceCode);
        if (province == null) {
            throw new InvalidLocationSelectionException();
        }
        if (normalizedWardCode == null) {
            return new ResolvedLocation(province.code(), province.name(), null, null);
        }

        LocationOptionResponse ward = find(cachedWards(province.code()), normalizedWardCode);
        if (ward == null) {
            throw new InvalidLocationSelectionException();
        }
        return new ResolvedLocation(province.code(), province.name(), ward.code(), ward.name());
    }

    private List<LocationOptionResponse> cachedWards(String provinceCode) {
        CachedOptions current = wardCache.get(provinceCode);
        if (isFresh(current)) {
            return current.options();
        }
        synchronized (wardCache) {
            current = wardCache.get(provinceCode);
            if (isFresh(current)) {
                return current.options();
            }
            List<LocationOptionResponse> options = parseOptions(fetchWards(provinceCode), true);
            wardCache.put(provinceCode, new CachedOptions(options, clock.millis()));
            return options;
        }
    }

    private String fetch(String path) {
        try {
            String body = restClient.get().uri(path).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                throw new LocationCatalogUnavailableException(null);
            }
            return body;
        } catch (RestClientException ex) {
            throw new LocationCatalogUnavailableException(ex);
        }
    }

    private String fetchWards(String provinceCode) {
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(WARDS_PATH)
                            .queryParam("depth", 2)
                            .build(provinceCode))
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new LocationCatalogUnavailableException(null);
            }
            return body;
        } catch (RestClientException ex) {
            throw new LocationCatalogUnavailableException(ex);
        }
    }

    private List<LocationOptionResponse> parseOptions(String body, boolean nestedWards) {
        try {
            JsonNode root = jsonMapper.readTree(body);
            JsonNode optionsNode = nestedWards ? root.path("wards") : root;
            if (!optionsNode.isArray()) {
                throw new IllegalStateException("Location catalog response has no option array");
            }
            List<LocationOptionResponse> options = new java.util.ArrayList<>();
            for (JsonNode option : optionsNode) {
                String code = option.path("code").asText(null);
                String name = option.path("name").asText(null);
                String unitType = option.path("division_type").asText(null);
                if (code == null || code.isBlank() || name == null || name.isBlank()) {
                    throw new IllegalStateException("Location catalog option is incomplete");
                }
                options.add(new LocationOptionResponse(code, name, unitType));
            }
            if (options.isEmpty()) {
                throw new IllegalStateException("Location catalog response is empty");
            }
            return List.copyOf(options);
        } catch (LocationCatalogUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new LocationCatalogUnavailableException(ex);
        }
    }

    private boolean isFresh(CachedOptions cached) {
        return cached != null
                && clock.millis() - cached.loadedAtMs() < properties.getCacheTtlMs();
    }

    private static LocationOptionResponse find(List<LocationOptionResponse> options, String code) {
        return options.stream().filter(option -> option.code().equals(code)).findFirst().orElse(null);
    }

    private static String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new InvalidLocationSelectionException();
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record CachedOptions(List<LocationOptionResponse> options, long loadedAtMs) {
    }

    public record ResolvedLocation(
            String provinceCode,
            String provinceName,
            String wardCode,
            String wardName) {

        public static ResolvedLocation empty() {
            return new ResolvedLocation(null, null, null, null);
        }
    }
}
