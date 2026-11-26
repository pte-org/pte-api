package com.pte.identity.internal.dto;

import com.pte.identity.domain.User;
import com.pte.identity.internal.dto.response.UserDirectoryEntryResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDirectoryEntryResponseTest {

    private static User user(String fullName) {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername("seed.admin@test.local");
        user.setFullName(fullName);
        return user;
    }

    @Test
    void usesTheFullNameWhenTheAccountHasOne() {
        assertThat(UserDirectoryEntryResponse.from(user("Bootstrap Admin")).fullName()).isEqualTo("Bootstrap Admin");
    }

    @Test
    void fallsBackToTheUsernameWhenTheFullNameIsMissingOrBlank() {
        assertThat(UserDirectoryEntryResponse.from(user(null)).fullName()).isEqualTo("seed.admin@test.local");
        assertThat(UserDirectoryEntryResponse.from(user("  ")).fullName()).isEqualTo("seed.admin@test.local");
    }
}
