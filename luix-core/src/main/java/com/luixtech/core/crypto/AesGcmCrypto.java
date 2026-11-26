package com.luixtech.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Thread-safe AES-256-GCM authenticated encryption helper.
 *
 * <p>Derives a 256-bit AES key via SHA-256 of the master secret and prepends a fresh 12-byte random IV
 * to each ciphertext payload with a 128-bit GCM authentication tag.
 */
public final class AesGcmCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    /**
     * Base64 encoding variant for serialized {@code IV || ciphertext || tag} payloads.
     */
    public enum Encoding {
        /** Standard Base64 with padding ({@link Base64#getEncoder()}). */
        STANDARD_BASE64,
        /** URL-safe Base64 without padding ({@link Base64#getUrlEncoder()}). */
        URL_SAFE_BASE64
    }

    private final SecretKey secretKey;
    private final Encoding encoding;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCrypto(String masterSecret, Encoding encoding) {
        if (masterSecret == null || masterSecret.isBlank()) {
            throw new IllegalArgumentException("masterSecret must not be blank");
        }
        this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
        this.secretKey = deriveAes256Key(masterSecret);
    }

    /**
     * Creates an instance using standard padded Base64 encoding.
     */
    public static AesGcmCrypto ofStandardBase64(String masterSecret) {
        return new AesGcmCrypto(masterSecret, Encoding.STANDARD_BASE64);
    }

    /**
     * Creates an instance using URL-safe unpadded Base64 encoding.
     */
    public static AesGcmCrypto ofUrlSafeBase64(String masterSecret) {
        return new AesGcmCrypto(masterSecret, Encoding.URL_SAFE_BASE64);
    }

    /**
     * Encrypts UTF-8 plaintext into {@code Base64(12-byte IV || ciphertext || 16-byte GCM tag)}.
     * Returns {@code null} or empty string unchanged.
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buffer.put(iv);
            buffer.put(cipherBytes);
            return encode(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Failed to encrypt payload", e);
        }
    }

    /**
     * Decrypts a payload produced by {@link #encrypt(String)}.
     *
     * @throws CryptoException if the payload is malformed, tampered with, or encrypted with a different key
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        try {
            byte[] combined = decode(cipherText);
            if (combined.length <= IV_LENGTH_BYTES) {
                throw new CryptoException("Encrypted payload is malformed");
            }

            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] encryptedData = new byte[buffer.remaining()];
            buffer.get(encryptedData);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(encryptedData);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (CryptoException e) {
            throw e;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new CryptoException("Failed to decrypt payload", e);
        }
    }

    /**
     * Attempts to decrypt {@code value}; returns {@code value} unchanged if it is not a valid ciphertext.
     * Useful when migrating legacy plaintext database columns transparently.
     */
    public String decryptOrRaw(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        try {
            return decrypt(value);
        } catch (CryptoException e) {
            return value;
        }
    }

    private String encode(byte[] data) {
        return switch (encoding) {
            case STANDARD_BASE64 -> Base64.getEncoder().encodeToString(data);
            case URL_SAFE_BASE64 -> Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        };
    }

    private byte[] decode(String encoded) {
        return switch (encoding) {
            case STANDARD_BASE64 -> Base64.getDecoder().decode(encoded);
            case URL_SAFE_BASE64 -> Base64.getUrlDecoder().decode(encoded);
        };
    }

    private static SecretKey deriveAes256Key(String masterSecret) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(masterSecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new CryptoException("SHA-256 algorithm is unavailable", e);
        }
    }
}
