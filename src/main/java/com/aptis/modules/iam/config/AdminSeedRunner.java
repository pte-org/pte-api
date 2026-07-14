package com.aptis.modules.iam.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.constant.IamMessageConstants;
import com.aptis.modules.iam.domain.Admin;
import com.aptis.modules.iam.repository.AdminRepository;
import com.aptis.modules.iam.interfaces.CredentialProvisioning;

@Component
public class AdminSeedRunner implements ApplicationRunner {

    private final AdminRepository adminRepository;
    private final CredentialProvisioning credentialService;
    private final Environment environment;

    public AdminSeedRunner(
            AdminRepository adminRepository,
            CredentialProvisioning credentialService,
            Environment environment) {
        this.adminRepository = adminRepository;
        this.credentialService = credentialService;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminRepository.count() > 0) {
            return;
        }

        String adminEmail = requireEnv(
                IamApiConstants.ADMIN_SEED_EMAIL_ENV,
                IamMessageConstants.ADMIN_SEED_EMAIL_MISSING);
        String adminPassword = requireEnv(
                IamApiConstants.ADMIN_SEED_PASSWORD_ENV,
                IamMessageConstants.ADMIN_SEED_PASSWORD_MISSING);

        adminRepository.save(Admin.create(
                adminEmail,
                IamApiConstants.SYSTEM_ADMIN_NAME,
                credentialService.hashPassword(adminPassword)));
    }

    private String requireEnv(String envName, String errorMessage) {
        String value = environment.getProperty(envName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(errorMessage);
        }
        return value;
    }
}
