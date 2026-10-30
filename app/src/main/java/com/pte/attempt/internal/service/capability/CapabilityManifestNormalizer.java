package com.pte.attempt.internal.service.capability;

import com.pte.attempt.internal.dto.request.ClientCapabilityManifest;
import com.pte.attempt.internal.exception.CapabilityManifestInvalidException;
import com.pte.itembank.SemanticVersion;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Validates client manifests and stored fingerprints and reduces them to the
 * release-owned capability allowlist. Pure: no lookups, no side effects.
 */
class CapabilityManifestNormalizer {

    private static final Pattern CAPABILITY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*(?:@[1-9][0-9]*)?");
    private static final int MAX_FINGERPRINT_LENGTH = 2048;

    private final Set<String> allowlistedCapabilities;

    CapabilityManifestNormalizer() {
        this.allowlistedCapabilities = TaskRuntimeProfileRegistry.all().stream()
                .map(TaskRuntimeProfileDescriptor::requiredClientCapabilities)
                .flatMap(Collection::stream)
                .map(this::canonicalCapability)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    NormalizedCapabilities normalize(ClientCapabilityManifest manifest) {
        if (manifest == null || manifest.capabilities() == null) {
            return new NormalizedCapabilities(Set.of(), "", null, List.of());
        }
        Set<String> normalized = new TreeSet<>();
        for (String raw : manifest.capabilities()) {
            if (raw == null) {
                throw new CapabilityManifestInvalidException();
            }
            String value = raw.trim().toUpperCase(Locale.ROOT);
            if (!CAPABILITY_PATTERN.matcher(value).matches()) {
                throw new CapabilityManifestInvalidException();
            }
            String canonical = canonicalCapability(value);
            // Unknown client capabilities are ignored, never persisted or
            // interpreted as executable server configuration.
            if (allowlistedCapabilities.contains(canonical)) {
                normalized.add(canonical);
            }
        }
        String appVersion = manifest.appVersion();
        if (appVersion != null) {
            try {
                SemanticVersion.parse(appVersion);
            } catch (RuntimeException ex) {
                throw new CapabilityManifestInvalidException();
            }
        }
        List<ClientCapabilityManifest.SupportedRuntimeContract> contracts = manifest.supportedContracts() == null
                ? List.of() : manifest.supportedContracts();
        for (ClientCapabilityManifest.SupportedRuntimeContract contract : contracts) {
            if (contract == null || contract.screenKey() == null || contract.screenKey().isBlank()
                    || contract.contractVersion() == null || contract.contractVersion() < 1
                    || contract.answerSchemaVersion() == null || contract.answerSchemaVersion() < 1
                    || contract.scoringProfileVersion() == null || contract.scoringProfileVersion() < 1) {
                throw new CapabilityManifestInvalidException();
            }
        }
        return new NormalizedCapabilities(Set.copyOf(normalized), fingerprint(normalized), appVersion, contracts);
    }

    NormalizedCapabilities normalizeFingerprint(String fingerprint) {
        if (isBlank(fingerprint)) {
            return new NormalizedCapabilities(Set.of(), "", null, List.of());
        }
        if (fingerprint.length() > MAX_FINGERPRINT_LENGTH) {
            throw new CapabilityManifestInvalidException();
        }
        Set<String> normalized = new TreeSet<>();
        for (String raw : fingerprint.split(",")) {
            if (!CAPABILITY_PATTERN.matcher(raw).matches() || !allowlistedCapabilities.contains(raw)) {
                throw new CapabilityManifestInvalidException();
            }
            normalized.add(raw);
        }
        return new NormalizedCapabilities(Set.copyOf(normalized), fingerprint(normalized), null, List.of());
    }

    String canonicalCapability(String raw) {
        return raw.contains("@") ? raw : raw + "@1";
    }

    String displayCapability(String canonical) {
        return canonical.endsWith("@1") ? canonical.substring(0, canonical.length() - 2) : canonical;
    }

    private String fingerprint(Set<String> values) {
        String fingerprint = String.join(",", values.stream().sorted(Comparator.naturalOrder()).toList());
        if (fingerprint.length() > MAX_FINGERPRINT_LENGTH) {
            throw new CapabilityManifestInvalidException();
        }
        return fingerprint;
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
