package com.aptis.modules.iam.service.studentimport;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.domain.enums.UsernamePattern;
import com.aptis.modules.iam.domain.studentimport.ImportUsernamePatternConfig;

@Service
public class UsernameGenerationService {

    private final UsernameNormalizer usernameNormalizer;

    public UsernameGenerationService(UsernameNormalizer usernameNormalizer) {
        this.usernameNormalizer = usernameNormalizer;
    }

    public UsernameBase generateBase(
            Map<String, String> originalRow,
            Map<String, String> fieldValues,
            ImportUsernamePatternConfig config,
            Long organizationId,
            int rowIndex) {
        if (config.type() == UsernamePattern.FROM_COLUMN) {
            return fromColumn(originalRow, config.sourceColumn(), organizationId, rowIndex);
        }
        if (config.type() == UsernamePattern.EMAIL_PREFIX) {
            return fromEmail(fieldValues.get("email"), organizationId, rowIndex);
        }
        return new UsernameBase(autoIncrementBase(organizationId, rowIndex), false);
    }

    private UsernameBase fromColumn(
            Map<String, String> originalRow,
            String sourceColumn,
            Long organizationId,
            int rowIndex) {
        String sourceValue = sourceColumn == null ? null : originalRow.get(sourceColumn);
        String base = usernameNormalizer.normalize(sourceValue);
        if (base.isBlank()) {
            return new UsernameBase(autoIncrementBase(organizationId, rowIndex), true);
        }
        return new UsernameBase(base, false);
    }

    private UsernameBase fromEmail(
            String email,
            Long organizationId,
            int rowIndex) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return new UsernameBase(autoIncrementBase(organizationId, rowIndex), true);
        }
        String prefix = email.substring(0, email.indexOf('@'));
        String base = usernameNormalizer.normalize(prefix);
        if (base.isBlank()) {
            return new UsernameBase(autoIncrementBase(organizationId, rowIndex), true);
        }
        return new UsernameBase(base, false);
    }

    private String autoIncrementBase(
            Long organizationId,
            int rowIndex) {
        return "%s_%s%d_%04d".formatted(
                IamApiConstants.USERNAME_AUTO_PREFIX,
                IamApiConstants.USERNAME_ORG_PREFIX,
                organizationId,
                rowIndex);
    }

    public record UsernameBase(String value, boolean fallbackUsed) {
    }
}
