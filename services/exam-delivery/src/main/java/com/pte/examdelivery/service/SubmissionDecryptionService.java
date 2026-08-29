package com.pte.examdelivery.service;

import com.pte.examdelivery.domain.exception.SubmissionDecryptionException;
import com.pte.examdelivery.dto.request.EncryptedSubmissionRequest;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

/**
 * Unwraps the per-submission AES key (RSA-OAEP, SHA-256/MGF1-SHA256 explicit on both sides —
 * a mismatched MGF1 digest is the #1 cross-platform interop failure with the Dart client) and
 * decrypts the payload (AES-GCM, 128-bit tag, 96-bit IV). Any failure — malformed Base64, wrong
 * IV length, RSA unwrap failure, or GCM auth-tag mismatch (tampering) — is rejected as a
 * {@link SubmissionDecryptionException} (4xx) before the caller can persist anything; nothing
 * here is ever allowed to surface as an unmapped 500.
 */
@Service
public class SubmissionDecryptionService {

    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    public String decrypt(EncryptedSubmissionRequest request, PrivateKey privateKey) {
        try {
            byte[] wrappedKey = Base64.getDecoder().decode(request.wrappedKey());
            byte[] iv = Base64.getDecoder().decode(request.iv());
            byte[] ciphertext = Base64.getDecoder().decode(request.ciphertext());

            if (iv.length != GCM_IV_LENGTH_BYTES) {
                throw new SubmissionDecryptionException();
            }

            Key aesKey = unwrapAesKey(wrappedKey, privateKey);
            byte[] plaintext = decryptWithAesGcm(ciphertext, aesKey, iv);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | NullPointerException | InvalidKeyException
                | InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchPaddingException
                | IllegalBlockSizeException | BadPaddingException ex) {
            throw new SubmissionDecryptionException();
        }
    }

    private Key unwrapAesKey(byte[] wrappedKey, PrivateKey privateKey) throws NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.UNWRAP_MODE, privateKey, OAEP_SHA256);
        return cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);
    }

    private byte[] decryptWithAesGcm(byte[] ciphertext, Key aesKey, byte[] iv) throws NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
            IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        return cipher.doFinal(ciphertext);
    }
}
