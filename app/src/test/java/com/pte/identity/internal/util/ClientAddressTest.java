package com.pte.identity.internal.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nginx appends the real address to X-Forwarded-For, so everything before the last entry is client-supplied and
 * must never decide who a request came from.
 */
class ClientAddressTest {

    @Test
    void usesTheSocketAddressWhenThereIsNoForwardedHeader() {
        assertThat(ClientAddress.resolve(null, "10.0.0.5")).isEqualTo("10.0.0.5");
        assertThat(ClientAddress.resolve("   ", "10.0.0.5")).isEqualTo("10.0.0.5");
    }

    @Test
    void usesTheOnlyEntry() {
        assertThat(ClientAddress.resolve("198.51.100.7", "10.0.0.5")).isEqualTo("198.51.100.7");
    }

    @Test
    void ignoresEntriesAClientCouldHaveForged() {
        assertThat(ClientAddress.resolve("203.0.113.1, 198.51.100.7", "10.0.0.5")).isEqualTo("198.51.100.7");
        assertThat(ClientAddress.resolve("1.1.1.1,2.2.2.2,198.51.100.7", "10.0.0.5")).isEqualTo("198.51.100.7");
    }

    @Test
    void trimsWhitespaceAndSkipsTrailingBlankEntries() {
        assertThat(ClientAddress.resolve("  198.51.100.7  ", "10.0.0.5")).isEqualTo("198.51.100.7");
        assertThat(ClientAddress.resolve("198.51.100.7, ,", "10.0.0.5")).isEqualTo("198.51.100.7");
    }

    @Test
    void fallsBackToTheSocketAddressWhenEveryEntryIsBlank() {
        assertThat(ClientAddress.resolve(" , ", "10.0.0.5")).isEqualTo("10.0.0.5");
    }
}
