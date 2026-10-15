package com.pte.billing.internal.service;

import com.pte.billing.domain.PlatformSetting;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.constant.PlatformSettingCatalog;
import com.pte.billing.internal.dto.request.PlatformSettingBatchRequest;
import com.pte.billing.internal.dto.request.PlatformSettingChangeRequest;
import com.pte.billing.internal.dto.request.PlatformSettingRequest;
import com.pte.billing.internal.dto.response.PlatformSettingResponse;
import com.pte.billing.internal.exception.PlatformSettingInvalidException;
import com.pte.billing.internal.exception.PlatformSettingNotFoundException;
import com.pte.billing.internal.exception.PlatformSettingValidationException;
import com.pte.billing.internal.mapper.PlatformSettingMapper;
import com.pte.billing.internal.repository.PlatformSettingRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Cached reads and administrator-managed values for platform-wide settings. */
@Service
public class PlatformSettingService {

    private final PlatformSettingRepository platformSettingRepository;
    private final AuditLogService auditLogService;
    /**
     * Process-local cache keyed by setting key; writes evict, restart clears it,
     * and failures are never cached. The monolith has one write path through
     * this service, so no background refresh or TTL is needed for these rarely
     * changing values.
     */
    private final ConcurrentMap<String, String> valueCache = new ConcurrentHashMap<>();

    public PlatformSettingService(PlatformSettingRepository platformSettingRepository) {
        this(platformSettingRepository, null);
    }

    @Autowired
    public PlatformSettingService(PlatformSettingRepository platformSettingRepository,
            AuditLogService auditLogService) {
        this.platformSettingRepository = platformSettingRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public String getValue(String key) {
        return valueCache.computeIfAbsent(key, this::loadValue);
    }

    @Transactional(readOnly = true)
    public int getInteger(String key) {
        String value = getValue(key);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new NumberFormatException("negative value");
            }
            PlatformSettingCatalog.Definition definition = PlatformSettingCatalog.find(key);
            if (definition != null && !withinRange(parsed, definition)) {
                throw new NumberFormatException("value outside catalog range");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new PlatformSettingInvalidException(key);
        }
    }

    @Transactional(readOnly = true)
    public PlatformSettingResponse get(String key) {
        PlatformSettingCatalog.Definition definition = requireDefinition(key);
        PlatformSetting setting = platformSettingRepository.findByKey(key)
                .orElseThrow(() -> new PlatformSettingNotFoundException(key));
        valueCache.put(key, setting.getValue());
        return PlatformSettingMapper.toResponse(setting, definition);
    }

    @Transactional(readOnly = true)
    public List<PlatformSettingResponse> list() {
        return platformSettingRepository.findAllByOrderByKeyAsc().stream()
                .map(setting -> {
                    PlatformSettingCatalog.Definition definition = PlatformSettingCatalog.find(setting.getKey());
                    return definition == null ? null : PlatformSettingMapper.toResponse(setting, definition);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * The controller exposes this write only under its PLATFORM_ADMIN guard.
     * The cache is evicted after the database flush. Evicting instead of
     * populating before transaction commit prevents a rolled-back write from
     * leaking into subsequent reads; the next read reloads the committed value.
     */
    @Transactional
    public PlatformSettingResponse update(String key, PlatformSettingRequest request) {
        return update(key, request, null);
    }

    @Transactional
    public PlatformSettingResponse update(String key, PlatformSettingRequest request, CurrentUser caller) {
        PlatformSettingCatalog.Definition definition = requireEditableDefinition(key);
        if (request == null) {
            throw new PlatformSettingValidationException(BillingConstants.PLATFORM_SETTING_VALUE_INVALID);
        }
        PlatformSetting setting = platformSettingRepository.findByKey(key)
                .orElseThrow(() -> new PlatformSettingNotFoundException(key));
        String normalizedValue = validateValue(definition, request.value());
        String previousValue = setting.getValue();
        setting.setValue(normalizedValue);
        setting.setDescription(request.description());
        PlatformSetting saved = platformSettingRepository.saveAndFlush(setting);
        valueCache.remove(key);
        recordUpdate(caller, key, previousValue, normalizedValue);
        return PlatformSettingMapper.toResponse(saved, definition);
    }

    @Transactional
    public List<PlatformSettingResponse> updateBatch(PlatformSettingBatchRequest request, CurrentUser caller) {
        if (request == null || request.settings() == null || request.settings().isEmpty()) {
            throw new PlatformSettingValidationException(BillingConstants.PLATFORM_SETTING_BATCH_REQUIRED);
        }

        Map<String, PlatformSetting> settingsByKey = new LinkedHashMap<>();
        Map<String, String> valuesByKey = new LinkedHashMap<>();
        Map<String, PlatformSettingCatalog.Definition> definitionsByKey = new LinkedHashMap<>();
        for (PlatformSettingChangeRequest change : request.settings()) {
            if (change == null || change.key() == null || change.value() == null) {
                throw new PlatformSettingValidationException(BillingConstants.PLATFORM_SETTING_VALUE_INVALID);
            }
            String key = change.key().trim();
            if (settingsByKey.containsKey(key)) {
                throw new PlatformSettingValidationException(BillingConstants.PLATFORM_SETTING_BATCH_DUPLICATE);
            }
            PlatformSettingCatalog.Definition definition = requireEditableDefinition(key);
            PlatformSetting setting = platformSettingRepository.findByKey(key)
                    .orElseThrow(() -> new PlatformSettingNotFoundException(key));
            settingsByKey.put(key, setting);
            valuesByKey.put(key, validateValue(definition, change.value()));
            definitionsByKey.put(key, definition);
        }

        List<PlatformSetting> changedSettings = new ArrayList<>();
        Map<String, String> previousValues = new LinkedHashMap<>();
        for (Map.Entry<String, PlatformSetting> entry : settingsByKey.entrySet()) {
            String key = entry.getKey();
            PlatformSetting setting = entry.getValue();
            String nextValue = valuesByKey.get(key);
            if (!Objects.equals(setting.getValue(), nextValue)) {
                previousValues.put(key, setting.getValue());
                setting.setValue(nextValue);
                changedSettings.add(setting);
            }
        }

        if (!changedSettings.isEmpty()) {
            platformSettingRepository.saveAllAndFlush(changedSettings);
            changedSettings.forEach(setting -> valueCache.remove(setting.getKey()));
            changedSettings.forEach(setting -> recordUpdate(caller, setting.getKey(),
                    previousValues.get(setting.getKey()), setting.getValue()));
        }

        return settingsByKey.values().stream()
                .map(setting -> PlatformSettingMapper.toResponse(setting, definitionsByKey.get(setting.getKey())))
                .toList();
    }

    private String loadValue(String key) {
        return platformSettingRepository.findByKey(key)
                .map(PlatformSetting::getValue)
                .orElseThrow(() -> new PlatformSettingNotFoundException(key));
    }

    private PlatformSettingCatalog.Definition requireDefinition(String key) {
        PlatformSettingCatalog.Definition definition = PlatformSettingCatalog.find(key);
        if (definition == null) {
            throw new PlatformSettingNotFoundException(key);
        }
        return definition;
    }

    private PlatformSettingCatalog.Definition requireEditableDefinition(String key) {
        PlatformSettingCatalog.Definition definition = PlatformSettingCatalog.find(key);
        if (definition == null || !definition.editable()) {
            throw new PlatformSettingValidationException(BillingConstants.PLATFORM_SETTING_KEY_UNSUPPORTED);
        }
        return definition;
    }

    private String validateValue(PlatformSettingCatalog.Definition definition, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new PlatformSettingValidationException(BillingConstants.PLATFORM_SETTING_VALUE_INVALID);
        }
        String normalizedValue = rawValue.trim();
        if (!PlatformSettingCatalog.TYPE_INTEGER.equals(definition.type())
                && !PlatformSettingCatalog.TYPE_DURATION_HOURS.equals(definition.type())) {
            throw new PlatformSettingValidationException(BillingConstants.PLATFORM_SETTING_VALUE_INVALID);
        }
        try {
            int parsed = Integer.parseInt(normalizedValue);
            if (!withinRange(parsed, definition)) {
                throw new NumberFormatException("value outside catalog range");
            }
        } catch (NumberFormatException ex) {
            throw new PlatformSettingValidationException(BillingConstants.PLATFORM_SETTING_VALUE_INVALID);
        }
        return normalizedValue;
    }

    private boolean withinRange(int value, PlatformSettingCatalog.Definition definition) {
        return (definition.minimum() == null || value >= definition.minimum())
                && (definition.maximum() == null || value <= definition.maximum());
    }

    private void recordUpdate(CurrentUser caller, String key, String previousValue, String nextValue) {
        if (caller == null || auditLogService == null || Objects.equals(previousValue, nextValue)) {
            return;
        }
        auditLogService.record(caller,
                BillingConstants.PLATFORM_SETTING_AGGREGATE,
                key,
                BillingConstants.PLATFORM_SETTING_UPDATED,
                "Updated platform setting %s from %s to %s".formatted(key, previousValue, nextValue));
    }
}
