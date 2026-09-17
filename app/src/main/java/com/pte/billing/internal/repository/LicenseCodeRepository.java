package com.pte.billing.internal.repository;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LicenseCodeRepository extends JpaRepository<LicenseCode, Long> {

    boolean existsByCode(String code);

    Optional<LicenseCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from LicenseCode code where code.code = :codeValue")
    Optional<LicenseCode> findByCodeForUpdate(@Param("codeValue") String codeValue);

    List<LicenseCode> findTop100ByOrderByIssuedAtDesc();

    /**
     * The redeem claim is one database operation so concurrent requests cannot
     * both observe ISSUED and activate the same code.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LicenseCode code
               set code.status = :redeemed,
                   code.redeemedByTenantId = :tenantId,
                   code.redeemedAt = :now
             where code.code = :codeValue
               and code.status = :issued
               and (code.codeExpiresAt is null or code.codeExpiresAt > :now)
            """)
    int markRedeemed(@Param("codeValue") String codeValue,
            @Param("tenantId") java.util.UUID tenantId,
            @Param("now") Instant now,
            @Param("issued") LicenseCodeStatus issued,
            @Param("redeemed") LicenseCodeStatus redeemed);

    @Modifying(flushAutomatically = true)
    @Query("""
            update LicenseCode code
               set code.status = :expired
             where code.status = :issued
               and code.codeExpiresAt is not null
               and code.codeExpiresAt <= :now
            """)
    int markExpired(@Param("now") Instant now,
            @Param("issued") LicenseCodeStatus issued,
            @Param("expired") LicenseCodeStatus expired);
}
