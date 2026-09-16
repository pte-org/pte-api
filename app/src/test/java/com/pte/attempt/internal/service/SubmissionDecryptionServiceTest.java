package com.pte.attempt.internal.service;

import com.pte.attempt.internal.dto.request.EncryptedSubmissionRequest;
import com.pte.attempt.internal.exception.SubmissionDecryptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ported from services/exam-delivery's own SubmissionDecryptionServiceTest —
 * same coverage, new package. Covers RSA-OAEP unwrapping, AES-GCM decryption,
 * and error handling for tampered/malformed submissions.
 */
@DisplayName("SubmissionDecryptionService")
class SubmissionDecryptionServiceTest {

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

    private SubmissionDecryptionService decryptionService;
    private PrivateKey testPrivateKey;
    private PublicKey testPublicKey;

    @BeforeEach
    void setUp() throws Exception {
        String privateKeyPEM = TEST_PRIVATE_KEY_PEM
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decodedKey = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
        testPrivateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec);

        String publicKeyPEM = TEST_PUBLIC_KEY_PEM
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decodedPublicKey = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(decodedPublicKey);
        testPublicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);

        decryptionService = new SubmissionDecryptionService();
    }

    @Test
    @DisplayName("valid encryption round-trip: encrypt with public key, decrypt with private key, plaintext matches")
    void validRoundTripEncryptionDecryption() throws Exception {
        String originalPlaintext = "The quick brown fox jumps over the lazy dog";
        UUID pinnedItemPublicId = UUID.randomUUID();

        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey aesKey = keyGen.generateKey();

        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[12];
        random.nextBytes(iv);

        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
        byte[] ciphertext = aesCipher.doFinal(originalPlaintext.getBytes());

        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        OAEPParameterSpec oaepSpec = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT
        );
        rsaCipher.init(Cipher.WRAP_MODE, testPublicKey, oaepSpec);
        byte[] wrappedKey = rsaCipher.wrap(aesKey);

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId,
            Base64.getEncoder().encodeToString(wrappedKey),
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(ciphertext)
        );

        String decryptedPlaintext = decryptionService.decrypt(request, testPrivateKey);

        assertThat(decryptedPlaintext).isEqualTo(originalPlaintext);
    }

    @Test
    @DisplayName("tampered ciphertext is rejected with SubmissionDecryptionException")
    void tamperedCiphertextRejection() throws Exception {
        String originalPlaintext = "Secret answer";
        UUID pinnedItemPublicId = UUID.randomUUID();

        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey aesKey = keyGen.generateKey();

        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[12];
        random.nextBytes(iv);

        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
        byte[] ciphertext = aesCipher.doFinal(originalPlaintext.getBytes());

        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        OAEPParameterSpec oaepSpec = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT
        );
        rsaCipher.init(Cipher.WRAP_MODE, testPublicKey, oaepSpec);
        byte[] wrappedKey = rsaCipher.wrap(aesKey);

        ciphertext[0] ^= 0x01;

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId,
            Base64.getEncoder().encodeToString(wrappedKey),
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(ciphertext)
        );

        assertThatThrownBy(() -> decryptionService.decrypt(request, testPrivateKey))
            .isInstanceOf(SubmissionDecryptionException.class);
    }

    @Test
    @DisplayName("tampered wrapped key is rejected with SubmissionDecryptionException")
    void tamperedWrappedKeyRejection() throws Exception {
        String originalPlaintext = "Secret answer";
        UUID pinnedItemPublicId = UUID.randomUUID();

        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey aesKey = keyGen.generateKey();

        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[12];
        random.nextBytes(iv);

        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
        byte[] ciphertext = aesCipher.doFinal(originalPlaintext.getBytes());

        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        OAEPParameterSpec oaepSpec = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT
        );
        rsaCipher.init(Cipher.WRAP_MODE, testPublicKey, oaepSpec);
        byte[] wrappedKey = rsaCipher.wrap(aesKey);

        wrappedKey[0] ^= 0x01;

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId,
            Base64.getEncoder().encodeToString(wrappedKey),
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(ciphertext)
        );

        assertThatThrownBy(() -> decryptionService.decrypt(request, testPrivateKey))
            .isInstanceOf(SubmissionDecryptionException.class);
    }

    @Test
    @DisplayName("invalid IV length (not 12 bytes) is rejected before attempting decryption")
    void invalidIvLengthRejection() throws Exception {
        UUID pinnedItemPublicId = UUID.randomUUID();

        byte[] invalidIv = new byte[16];
        new SecureRandom().nextBytes(invalidIv);

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId,
            Base64.getEncoder().encodeToString(new byte[256]),
            Base64.getEncoder().encodeToString(invalidIv),
            Base64.getEncoder().encodeToString(new byte[32])
        );

        assertThatThrownBy(() -> decryptionService.decrypt(request, testPrivateKey))
            .isInstanceOf(SubmissionDecryptionException.class);
    }

    @Test
    @DisplayName("malformed Base64 in wrapped key is caught and wrapped as SubmissionDecryptionException")
    void malformedBase64WrappedKeyRejection() {
        UUID pinnedItemPublicId = UUID.randomUUID();

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId,
            "not-valid-base64!!!!",
            Base64.getEncoder().encodeToString(new byte[12]),
            Base64.getEncoder().encodeToString(new byte[32])
        );

        assertThatThrownBy(() -> decryptionService.decrypt(request, testPrivateKey))
            .isInstanceOf(SubmissionDecryptionException.class);
    }

    @Test
    @DisplayName("malformed Base64 in IV is caught and wrapped as SubmissionDecryptionException")
    void malformedBase64IvRejection() {
        UUID pinnedItemPublicId = UUID.randomUUID();

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId,
            Base64.getEncoder().encodeToString(new byte[256]),
            "not-valid-base64!!!!",
            Base64.getEncoder().encodeToString(new byte[32])
        );

        assertThatThrownBy(() -> decryptionService.decrypt(request, testPrivateKey))
            .isInstanceOf(SubmissionDecryptionException.class);
    }

    @Test
    @DisplayName("malformed Base64 in ciphertext is caught and wrapped as SubmissionDecryptionException")
    void malformedBase64CiphertextRejection() {
        UUID pinnedItemPublicId = UUID.randomUUID();

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId,
            Base64.getEncoder().encodeToString(new byte[256]),
            Base64.getEncoder().encodeToString(new byte[12]),
            "not-valid-base64!!!!"
        );

        assertThatThrownBy(() -> decryptionService.decrypt(request, testPrivateKey))
            .isInstanceOf(SubmissionDecryptionException.class);
    }

    @Test
    @DisplayName("empty or blank Base64 strings are rejected as SubmissionDecryptionException")
    void emptyBase64RejectionWrappedKey() {
        UUID pinnedItemPublicId = UUID.randomUUID();

        EncryptedSubmissionRequest request = new EncryptedSubmissionRequest(
            pinnedItemPublicId,
            "",
            Base64.getEncoder().encodeToString(new byte[12]),
            Base64.getEncoder().encodeToString(new byte[32])
        );

        assertThatThrownBy(() -> decryptionService.decrypt(request, testPrivateKey))
            .isInstanceOf(SubmissionDecryptionException.class);
    }
}
