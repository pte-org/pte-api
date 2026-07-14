package com.aptis.modules.iam.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminTest {

    @Test
    void anemicEntityExposesGettersAndSetters() {
        Admin admin = new Admin();
        admin.setEmail("admin@aptis.com");
        admin.setName("Admin Name");

        assertThat(admin.getEmail()).isEqualTo("admin@aptis.com");
        assertThat(admin.getName()).isEqualTo("Admin Name");
    }
}
