package com.pte.billing.internal.service;

import com.pte.billing.domain.PlatformSetting;
import com.pte.billing.internal.dto.request.PlatformSettingRequest;
import com.pte.billing.internal.dto.response.PlatformSettingResponse;
import com.pte.billing.internal.exception.PlatformSettingInvalidException;
import com.pte.billing.internal.exception.PlatformSettingNotFoundException;
import com.pte.billing.internal.mapper.PlatformSettingMapper;
import com.pte.billing.internal.repository.PlatformSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Cached reads and administrator-managed values for platform-wide settings. */
@Service
public class PlatformSettingService {

    private final PlatformSettingRepository platformSettingRepository;
    /**
     * Process-local cache keyed by setting key; writes evict, restart clears it,
     * and failures are never cached. The monolith has one write path through
     * this service, so no background refresh or TTL is needed for these rarely
     * changing values.
     */
    private final ConcurrentMap<String, String> valueCache = new ConcurrentHashMap<>();

    public PlatformSettingService(PlatformSettingRepository platformSettingRepository) {
        this.platformSettingRepository = platformSettingRepository;
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
            return parsed;
        } catch (NumberFormatException ex) {
            throw new PlatformSettingInvalidException(key);
        }
    }

    @Transactional(readOnly = true)
    public PlatformSettingResponse get(String key) {
        PlatformSetting setting = platformSettingRepository.findByKey(key)
                .orElseThrow(() -> new PlatformSettingNotFoundException(key));
        valueCache.put(key, setting.getValue());
        return PlatformSettingMapper.toResponse(setting);
    }

    @Transactional(readOnly = true)
    public List<PlatformSettingResponse> list() {
        return platformSettingRepository.findAllByOrderByKeyAsc().stream()
                .map(PlatformSettingMapper::toResponse)
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
        PlatformSetting setting = platformSettingRepository.findByKey(key)
                .orElseThrow(() -> new PlatformSettingNotFoundException(key));
        setting.setValue(request.value());
        setting.setDescription(request.description());
        PlatformSetting saved = platformSettingRepository.saveAndFlush(setting);
        valueCache.remove(key);
        return PlatformSettingMapper.toResponse(saved);
    }

    private String loadValue(String key) {
        return platformSettingRepository.findByKey(key)
                .map(PlatformSetting::getValue)
                .orElseThrow(() -> new PlatformSettingNotFoundException(key));
    }
}
