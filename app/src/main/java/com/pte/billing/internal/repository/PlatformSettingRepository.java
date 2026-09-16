package com.pte.billing.internal.repository;

import com.pte.billing.domain.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, Long> {

    Optional<PlatformSetting> findByKey(String key);

    List<PlatformSetting> findAllByOrderByKeyAsc();
}
