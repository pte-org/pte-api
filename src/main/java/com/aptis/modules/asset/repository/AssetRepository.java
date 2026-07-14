package com.aptis.modules.asset.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aptis.modules.asset.domain.Asset;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findByPublicId(UUID publicId);
    Optional<Asset> findByPublicIdAndDeletedFalse(UUID publicId);
    java.util.List<Asset> findByPublicIdIn(java.util.List<UUID> publicIds);
}
