package com.aptis.modules.iam.domain.studentimport;

import com.aptis.modules.iam.domain.enums.UsernamePattern;

public record ImportUsernamePatternConfig(
        UsernamePattern type,
        String sourceColumn) {
}
