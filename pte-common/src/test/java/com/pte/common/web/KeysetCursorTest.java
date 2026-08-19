package com.pte.common.web;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeysetCursorTest {

    @Test
    void encodeThenDecode_roundTripsInstantAndPublicId() {
        Instant updatedAt = Instant.now();
        UUID publicId = UUID.randomUUID();

        String encoded = KeysetCursor.encode(updatedAt, publicId);
        KeysetCursor.Cursor decoded = KeysetCursor.decode(encoded);

        assertThat(decoded).isNotNull();
        assertThat(decoded.updatedAt()).isEqualTo(updatedAt);
        assertThat(decoded.publicId()).isEqualTo(publicId);
    }

    @Test
    void decode_null_returnsNull() {
        assertThat(KeysetCursor.decode(null)).isNull();
    }

    @Test
    void decode_empty_returnsNull() {
        assertThat(KeysetCursor.decode("")).isNull();
    }

    @Test
    void decode_blank_returnsNull() {
        assertThat(KeysetCursor.decode("   ")).isNull();
    }

    @Test
    void decode_malformedCursor_withoutSeparator_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> KeysetCursor.decode("not-a-valid-cursor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed export cursor");
    }

    @Test
    void decode_malformedCursor_withInvalidInstantPortion_throwsException() {
        // Has a separator, but the timestamp portion isn't parseable — Instant.parse
        // throws DateTimeParseException, a RuntimeException, which is acceptable
        // failure behavior for a malformed cursor (not silently swallowed).
        assertThatThrownBy(() -> KeysetCursor.decode("not-an-instant_" + UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class);
    }
}
