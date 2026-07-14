package com.aptis.modules.iam.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.constant.IamMessageConstants;
import com.aptis.modules.iam.domain.Host;
import com.aptis.modules.iam.domain.Student;
import com.aptis.modules.iam.repository.HostRepository;
import com.aptis.modules.iam.repository.StudentRepository;
import com.aptis.modules.iam.interfaces.CredentialProvisioning;
import com.aptis.modules.tenancy.domain.Organization;
import com.aptis.modules.tenancy.repository.OrganizationRepository;

@Component
public class DemoAccountSeedRunner implements ApplicationRunner {

    private final CredentialProvisioning credentialService;
    private final Environment environment;
    private final HostRepository hostRepository;
    private final OrganizationRepository organizationRepository;
    private final StudentRepository studentRepository;

    public DemoAccountSeedRunner(
            CredentialProvisioning credentialService,
            Environment environment,
            HostRepository hostRepository,
            OrganizationRepository organizationRepository,
            StudentRepository studentRepository) {
        this.credentialService = credentialService;
        this.environment = environment;
        this.hostRepository = hostRepository;
        this.organizationRepository = organizationRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Host host = seedHostIfConfigured();
        seedStudentIfConfigured(host);
    }

    private Host seedHostIfConfigured() {
        String hostEmail = environment.getProperty(IamApiConstants.HOST_SEED_EMAIL_ENV);
        if (isBlank(hostEmail)) {
            return null;
        }

        return hostRepository.findByContactEmail(hostEmail)
                .orElseGet(() -> createSeedHost(hostEmail));
    }

    private Host createSeedHost(String hostEmail) {
        String hostCode = requireSeedEnv(IamApiConstants.HOST_SEED_CODE_ENV,
                IamMessageConstants.HOST_SEED_INCOMPLETE);
        String hostName = requireSeedEnv(IamApiConstants.HOST_SEED_NAME_ENV,
                IamMessageConstants.HOST_SEED_INCOMPLETE);
        String hostPassword = requireSeedEnv(IamApiConstants.HOST_SEED_PASSWORD_ENV,
                IamMessageConstants.HOST_SEED_INCOMPLETE);

        Organization organization = organizationRepository.save(Organization.create(
                requireSeedEnv(IamApiConstants.HOST_SEED_ORGANIZATION_NAME_ENV,
                        IamMessageConstants.HOST_SEED_INCOMPLETE),
                requireSeedEnv(IamApiConstants.HOST_SEED_ORGANIZATION_TYPE_ENV,
                        IamMessageConstants.HOST_SEED_INCOMPLETE),
                requireSeedEnv(IamApiConstants.HOST_SEED_ORGANIZATION_ADDRESS_ENV,
                        IamMessageConstants.HOST_SEED_INCOMPLETE),
                requireSeedEnv(IamApiConstants.HOST_SEED_REPRESENTATIVE_NAME_ENV,
                        IamMessageConstants.HOST_SEED_INCOMPLETE),
                hostEmail,
                requireSeedEnv(IamApiConstants.HOST_SEED_REPRESENTATIVE_PHONE_ENV,
                        IamMessageConstants.HOST_SEED_INCOMPLETE),
                environment.getProperty(IamApiConstants.HOST_SEED_CONTRACT_CODE_ENV),
                environment.getProperty(IamApiConstants.HOST_SEED_PACKAGE_NAME_ENV),
                parseInteger(environment.getProperty(IamApiConstants.HOST_SEED_STUDENT_LIMIT_ENV)),
                null,
                null));

        return hostRepository.save(Host.create(
                hostCode,
                hostName,
                organization.getId(),
                hostEmail,
                credentialService.hashPassword(hostPassword)));
    }

    private void seedStudentIfConfigured(Host host) {
        String username = environment.getProperty(IamApiConstants.STUDENT_SEED_USERNAME_ENV);
        if (isBlank(username)) {
            return;
        }
        if (host == null) {
            throw new IllegalStateException(IamMessageConstants.STUDENT_SEED_INCOMPLETE);
        }
        if (studentRepository.findByUsername(username).isPresent()) {
            return;
        }

        Student student = Student.create(
                username,
                host.getOrganizationId(),
                host.getId().toString(),
                requireSeedEnv(IamApiConstants.STUDENT_SEED_FULL_NAME_ENV,
                        IamMessageConstants.STUDENT_SEED_INCOMPLETE),
                environment.getProperty(IamApiConstants.STUDENT_SEED_CODE_ENV),
                environment.getProperty(IamApiConstants.STUDENT_SEED_CLASS_NAME_ENV),
                environment.getProperty(IamApiConstants.STUDENT_SEED_EMAIL_ENV),
                environment.getProperty(IamApiConstants.STUDENT_SEED_PHONE_ENV),
                credentialService.hashPassword(requireSeedEnv(
                        IamApiConstants.STUDENT_SEED_PASSWORD_ENV,
                        IamMessageConstants.STUDENT_SEED_INCOMPLETE)));
        studentRepository.save(student);
    }

    private String requireSeedEnv(String envName, String errorMessage) {
        String value = environment.getProperty(envName);
        if (isBlank(value)) {
            throw new IllegalStateException(errorMessage);
        }
        return value;
    }

    private Integer parseInteger(String value) {
        if (isBlank(value)) {
            return null;
        }
        return Integer.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
