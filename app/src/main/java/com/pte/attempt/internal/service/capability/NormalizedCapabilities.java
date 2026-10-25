package com.pte.attempt.internal.service.capability;

import com.pte.attempt.internal.dto.request.ClientCapabilityManifest;

import java.util.List;
import java.util.Set;

/** A validated client manifest (or stored fingerprint) reduced to allowlisted canonical capabilities. */
record NormalizedCapabilities(Set<String> values, String fingerprint, String appVersion,
        List<ClientCapabilityManifest.SupportedRuntimeContract> supportedContracts) {
}
