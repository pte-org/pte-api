package com.pte.attempt.internal.config;

import javax.crypto.Cipher;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ported from services/exam-delivery's own EncryptionKeyProviderTest — same
 * coverage, new package. Covers RSA keypair loading, PEM parsing,
 * normalization, and round-trip encryption/decryption.
 */
@DisplayName("EncryptionKeyProvider")
class EncryptionKeyProviderTest {

    private static final String TEST_PRIVATE_KEY_PEM =
        "-----BEGIN PRIVATE KEY-----\n" +
        "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCjU17HbSmwYJUh\n" +
        "uS0IYP+VjVjuTo2Z6+SHUzT1JjXgl9NHZoVY7fTx2uza+UZvrt0ZDMPRrGy0/ZDw\n" +
        "s47g2ePIUIt6vVGMqLcsXdk9nPy9nUN14yTYpRfVl9GnNNNM06iOqU2t1PDv3aCv\n" +
        "WZQUKNlPnc5ndHnzG9tUh4gNx0fMncXxHfKYs+aP3mCWpMIv1izhA6vUPJp4mddh\n" +
        "+DQwMsIov3RyzpAl6rTAa2G0mu5m8/10HblQiwIgrFVJKOEp9L+KW8PvRbt7UBnZ\n" +
        "KE1hv/PdPzNXP0mSmVpGcaJZ1UdJiyXmZqEZUm7SgbTcW4O0Z2nTg2LVlIqgcKco\n" +
        "nGuM1FThAgMBAAECggEAAVimPYMoYKjJpuobEWR1C+JS/C8lpLoCh6TpeP85OshO\n" +
        "mpI5TXtz6UfVa6xKCtDyd668uRxkdqEX1BubTQ4/veSf0zWW+9eOz7rX7+2MseqP\n" +
        "SiQix0ZEXB9m2Au39YxRuYgSzNDeYfBKByMALPK9oJXZtA2sRQs25Ydi2T/6HxEv\n" +
        "G9pZTsOvfKPwzQNV2LwdLMtFNBaKc9LYMZYdA7c3Q8dR/TfjZA2eSbh8mO01gVRX\n" +
        "7gfZx05Cqdkj7bL0DwMY6zMbGx9VPrYOsbkwFrj6q3pccUlEZefHEJLoLZZ1jtyA\n" +
        "cyMqSttsUrVoBuOVddbuxShAgwabiqM6ZevpuyrNAQKBgQDhLuJprBRUCNwYl19/\n" +
        "5O+QS9gU/oRoeVzkQgs3gsT7txBiRgC6YCbXx2tsrGqHp1G9cE/CiTu/4gFKweUk\n" +
        "QBLbzPRwmRkuu/8Fhb0PXOIPyy9k9o94tWMALABOII+A/dPHz5AfMu1Q06qgMNsT\n" +
        "VJ6zTWRFTQYr1z9nplFXApz1AQKBgQC5rVt9f1oxRYOmN84orzwDLWa1CbN/HI1W\n" +
        "rDZU6lT6y72LrYk0JbE4WGa+FoMYs+NYYDJ1Sko5O+cqxOzdpIScrgFt6fcfin+b\n" +
        "7i64AxZN4pHsqnu/5nkyxHYYtakpWR6Zt7X54p7JQZfeZ1u5q28JvIiQGJbeQzFh\n" +
        "FWtlD9X/4QKBgQCyzCxgpY7ajtJcAE2G2nvRgjdHrDPsVHkKkNohMIdCKsAql9Ta\n" +
        "J6CP8rr8ZgaMQbGojnX2TEsR99z4w+4yk2Gl/x4UDawuLH24n9FXUnP5AGdYbmHL\n" +
        "AZNjPozbfAUV8fVnkQqpiyFd7UNswH4i4izEx+2XDSqaQuYIYcnyIZQJAQKBgBud\n" +
        "Z9UDYtOfJhRLinLMlR1X3EWimrqV8YNpe28nptniAV/LwHMsA+6AX094I6lB39cd\n" +
        "/4MoOF/Iw/m9bgkM1dhDnNzPBjz/qTj0tKgTdXbB8rgDJwKEcZQWWgYUxoLqyT1N\n" +
        "cFgo1zrRcZfXQXIcyBfnnVvo/E80KRzqRZ2zPD2hAoGBAKGNszfhUSGDy3a2Kuk2\n" +
        "A6IHtPGc3qFmGAaMdD2Epk81QJTV4Fj5aG+X1OgQBgpHs6bhxd31PA0JBC3N3UwK\n" +
        "PERmc5bN2xYQO6m+m0v0LVAsKp2aZouEQ9Zzkiecpux4Vs5brkOuhx+1aH+Gvrpi\n" +
        "OF2BnNeJdY7PrykURblUIiKT\n" +
        "-----END PRIVATE KEY-----";

    private static final String TEST_PUBLIC_KEY_PEM =
        "-----BEGIN PUBLIC KEY-----\n" +
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAo1Nex20psGCVIbktCGD/\n" +
        "lY1Y7k6Nmevkh1M09SY14JfTR2aFWO308drs2vlGb67dGQzD0axstP2Q8LOO4Nnj\n" +
        "yFCLer1RjKi3LF3ZPZz8vZ1DdeMk2KUX1ZfRpzTTTNOojqlNrdTw792gr1mUFCjZ\n" +
        "T53OZ3R58xvbVIeIDcdHzJ3F8R3ymLPmj95glqTCL9Ys4QOr1DyaeJnXYfg0MDLC\n" +
        "KL90cs6QJeq0wGthtJruZvP9dB25UIsCIKxVSSjhKfS/ilvD70W7e1AZ2ShNYb/z\n" +
        "3T8zVz9JkplaRnGiWdVHSYsl5mahGVJu0oG03FuDtGdp04Ni1ZSKoHCnKJxrjNRU\n" +
        "4QIDAQAB\n" +
        "-----END PUBLIC KEY-----";

    private static final String TEST_PRIVATE_KEY_PEM_WITH_CRLF =
        "-----BEGIN PRIVATE KEY-----\r\n" +
        "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCjU17HbSmwYJUh\r\n" +
        "uS0IYP+VjVjuTo2Z6+SHUzT1JjXgl9NHZoVY7fTx2uza+UZvrt0ZDMPRrGy0/ZDw\r\n" +
        "s47g2ePIUIt6vVGMqLcsXdk9nPy9nUN14yTYpRfVl9GnNNNM06iOqU2t1PDv3aCv\r\n" +
        "WZQUKNlPnc5ndHnzG9tUh4gNx0fMncXxHfKYs+aP3mCWpMIv1izhA6vUPJp4mddh\r\n" +
        "+DQwMsIov3RyzpAl6rTAa2G0mu5m8/10HblQiwIgrFVJKOEp9L+KW8PvRbt7UBnZ\r\n" +
        "KE1hv/PdPzNXP0mSmVpGcaJZ1UdJiyXmZqEZUm7SgbTcW4O0Z2nTg2LVlIqgcKco\r\n" +
        "nGuM1FThAgMBAAECggEAAVimPYMoYKjJpuobEWR1C+JS/C8lpLoCh6TpeP85OshO\r\n" +
        "mpI5TXtz6UfVa6xKCtDyd668uRxkdqEX1BubTQ4/veSf0zWW+9eOz7rX7+2MseqP\r\n" +
        "SiQix0ZEXB9m2Au39YxRuYgSzNDeYfBKByMALPK9oJXZtA2sRQs25Ydi2T/6HxEv\r\n" +
        "G9pZTsOvfKPwzQNV2LwdLMtFNBaKc9LYMZYdA7c3Q8dR/TfjZA2eSbh8mO01gVRX\r\n" +
        "7gfZx05Cqdkj7bL0DwMY6zMbGx9VPrYOsbkwFrj6q3pccUlEZefHEJLoLZZ1jtyA\r\n" +
        "cyMqSttsUrVoBuOVddbuxShAgwabiqM6ZevpuyrNAQKBgQDhLuJprBRUCNwYl19/\r\n" +
        "5O+QS9gU/oRoeVzkQgs3gsT7txBiRgC6YCbXx2tsrGqHp1G9cE/CiTu/4gFKweUk\r\n" +
        "QBLbzPRwmRkuu/8Fhb0PXOIPyy9k9o94tWMALABOII+A/dPHz5AfMu1Q06qgMNsT\r\n" +
        "VJ6zTWRFTQYr1z9nplFXApz1AQKBgQC5rVt9f1oxRYOmN84orzwDLWa1CbN/HI1W\r\n" +
        "rDZU6lT6y72LrYk0JbE4WGa+FoMYs+NYYDJ1Sko5O+cqxOzdpIScrgFt6fcfin+b\r\n" +
        "7i64AxZN4pHsqnu/5nkyxHYYtakpWR6Zt7X54p7JQZfeZ1u5q28JvIiQGJbeQzFh\r\n" +
        "FWtlD9X/4QKBgQCyzCxgpY7ajtJcAE2G2nvRgjdHrDPsVHkKkNohMIdCKsAql9Ta\r\n" +
        "J6CP8rr8ZgaMQbGojnX2TEsR99z4w+4yk2Gl/x4UDawuLH24n9FXUnP5AGdYbmHL\r\n" +
        "AZNjPozbfAUV8fVnkQqpiyFd7UNswH4i4izEx+2XDSqaQuYIYcnyIZQJAQKBgBud\r\n" +
        "Z9UDYtOfJhRLinLMlR1X3EWimrqV8YNpe28nptniAV/LwHMsA+6AX094I6lB39cd\r\n" +
        "/4MoOF/Iw/m9bgkM1dhDnNzPBjz/qTj0tKgTdXbB8rgDJwKEcZQWWgYUxoLqyT1N\r\n" +
        "cFgo1zrRcZfXQXIcyBfnnVvo/E80KRzqRZ2zPD2hAoGBAKGNszfhUSGDy3a2Kuk2\r\n" +
        "A6IHtPGc3qFmGAaMdD2Epk81QJTV4Fj5aG+X1OgQBgpHs6bhxd31PA0JBC3N3UwK\r\n" +
        "PERmc5bN2xYQO6m+m0v0LVAsKp2aZouEQ9Zzkiecpux4Vs5brkOuhx+1aH+Gvrpi\r\n" +
        "OF2BnNeJdY7PrykURblUIiKT\r\n" +
        "-----END PRIVATE KEY-----";

    @Test
    @DisplayName("loads private key from PEM and parses successfully")
    void loadPrivateKeyFromPem() {
        EncryptionKeyProvider provider = new EncryptionKeyProvider(TEST_PRIVATE_KEY_PEM, TEST_PUBLIC_KEY_PEM, "PROD");
        PrivateKey privateKey = provider.getPrivateKey();

        assertThat(privateKey).isNotNull();
        assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    @DisplayName("loads public key from PEM and parses successfully")
    void loadPublicKeyFromPem() {
        EncryptionKeyProvider provider = new EncryptionKeyProvider(TEST_PRIVATE_KEY_PEM, TEST_PUBLIC_KEY_PEM, "PROD");
        PublicKey publicKey = provider.getPublicKey();

        assertThat(publicKey).isNotNull();
        assertThat(publicKey.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    @DisplayName("exposes public key as Base64-encoded X.509 SubjectPublicKeyInfo")
    void exposesPublicKeyAsBase64X509() {
        EncryptionKeyProvider provider = new EncryptionKeyProvider(TEST_PRIVATE_KEY_PEM, TEST_PUBLIC_KEY_PEM, "PROD");
        String base64PublicKey = provider.getPublicKeyBase64();

        assertThat(base64PublicKey).isNotNull();
        assertThat(base64PublicKey).isNotEmpty();
        assertThat(Base64.getDecoder().decode(base64PublicKey)).isNotEmpty();
    }

    @Test
    @DisplayName("round-trip: encrypt with public key, decrypt with private key")
    void roundTripEncryptionDecryption() throws Exception {
        EncryptionKeyProvider provider = new EncryptionKeyProvider(TEST_PRIVATE_KEY_PEM, TEST_PUBLIC_KEY_PEM, "PROD");

        String plaintext = "The quick brown fox jumps over the lazy dog";
        byte[] plaintextBytes = plaintext.getBytes();

        Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, provider.getPublicKey());
        byte[] encrypted = encryptCipher.doFinal(plaintextBytes);

        Cipher decryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        decryptCipher.init(Cipher.DECRYPT_MODE, provider.getPrivateKey());
        byte[] decrypted = decryptCipher.doFinal(encrypted);
        String decryptedText = new String(decrypted);

        assertThat(decryptedText).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("PEM normalization: parses PEM with CRLF line endings")
    void pemNormalizationHandlesCRLF() {
        EncryptionKeyProvider provider = new EncryptionKeyProvider(TEST_PRIVATE_KEY_PEM_WITH_CRLF, TEST_PUBLIC_KEY_PEM, "PROD");
        PrivateKey privateKey = provider.getPrivateKey();

        assertThat(privateKey).isNotNull();
        assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    @DisplayName("fails fast when private key PEM is missing in non-dev profile")
    void failsFastMissingKeyNonDevProfile() {
        assertThatThrownBy(() -> new EncryptionKeyProvider(null, TEST_PUBLIC_KEY_PEM, "PROD"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("encryption");
    }

    @Test
    @DisplayName("fails fast when public key PEM is missing in non-dev profile")
    void failsFastMissingPublicKeyNonDevProfile() {
        assertThatThrownBy(() -> new EncryptionKeyProvider(TEST_PRIVATE_KEY_PEM, null, "PROD"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("encryption");
    }

    @Test
    @DisplayName("generates ephemeral keypair when config is missing in dev profile")
    void generatesEphemeralKeyInDevProfile() {
        EncryptionKeyProvider provider = new EncryptionKeyProvider(null, null, "dev");

        assertThat(provider.getPrivateKey()).isNotNull();
        assertThat(provider.getPublicKey()).isNotNull();
        assertThat(provider.getPublicKeyBase64()).isNotNull();
    }
}
