package com.luixtech.core.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmCryptoTest {

    @Test
    void roundTripStandardBase64() {
        AesGcmCrypto crypto = AesGcmCrypto.ofStandardBase64("scheduler-master-key");
        String plaintext = "{\"token\":\"sk-live-中文🚀\"}";
        String c1 = crypto.encrypt(plaintext);
        String c2 = crypto.encrypt(plaintext);

        assertNotEquals(c1, c2, "Fresh random IV should produce different ciphertexts");
        assertEquals(plaintext, crypto.decrypt(c1));
        assertEquals(plaintext, crypto.decrypt(c2));
    }

    @Test
    void roundTripUrlSafeBase64() {
        AesGcmCrypto crypto = AesGcmCrypto.ofUrlSafeBase64("trading-master-key");
        String plaintext = "P@ssw0rd!中文";
        String encrypted = crypto.encrypt(plaintext);

        assertEquals(-1, encrypted.indexOf('+'));
        assertEquals(-1, encrypted.indexOf('/'));
        assertEquals(-1, encrypted.indexOf('='));
        assertEquals(plaintext, crypto.decrypt(encrypted));
    }

    @Test
    void handlesNullAndEmptyInputs() {
        AesGcmCrypto crypto = AesGcmCrypto.ofStandardBase64("secret");
        assertNull(crypto.encrypt(null));
        assertEquals("", crypto.encrypt(""));
        assertNull(crypto.decrypt(null));
        assertEquals("", crypto.decrypt(""));
        assertNull(crypto.decryptOrRaw(null));
        assertEquals("", crypto.decryptOrRaw(""));
    }

    @Test
    void decryptRejectsWrongKeyOrTamperedCiphertext() {
        AesGcmCrypto writer = AesGcmCrypto.ofStandardBase64("key-a");
        AesGcmCrypto reader = AesGcmCrypto.ofStandardBase64("key-b");
        String encrypted = writer.encrypt("sensitive");

        assertThrows(CryptoException.class, () -> reader.decrypt(encrypted));
        assertThrows(CryptoException.class, () -> writer.decrypt("not-valid-base64!!!"));
    }

    @Test
    void decryptOrRawFallsBackToPlaintext() {
        AesGcmCrypto crypto = AesGcmCrypto.ofUrlSafeBase64("key-a");
        assertEquals("legacy-plaintext-password", crypto.decryptOrRaw("legacy-plaintext-password"));
        String encrypted = crypto.encrypt("new-secret");
        assertEquals("new-secret", crypto.decryptOrRaw(encrypted));
    }

    @Test
    void rejectsBlankMasterSecret() {
        assertThrows(IllegalArgumentException.class, () -> AesGcmCrypto.ofStandardBase64("  "));
        assertThrows(IllegalArgumentException.class, () -> AesGcmCrypto.ofUrlSafeBase64(null));
    }
}
