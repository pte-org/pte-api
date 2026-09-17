package com.pte.billing.internal.service;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicenseCodeExpirationServiceTest {

    @Mock
    private LicenseCodeRepository licenseCodeRepository;

    @Test
    void expireDueCodes_marksOnlyIssuedExpiredCodes() {
        LicenseCodeExpirationService service = new LicenseCodeExpirationService(licenseCodeRepository);
        when(licenseCodeRepository.markExpired(org.mockito.ArgumentMatchers.any(), eq(LicenseCodeStatus.ISSUED),
                eq(LicenseCodeStatus.EXPIRED))).thenReturn(1);

        int expired = service.expireDueCodes();

        assertThat(expired).isEqualTo(1);
    }
}
