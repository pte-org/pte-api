package com.aptis.modules.iam.domain.studentimport;

public record CredentialRow(
        int rowNumber,
        String username,
        String plaintextPassword) {
}
