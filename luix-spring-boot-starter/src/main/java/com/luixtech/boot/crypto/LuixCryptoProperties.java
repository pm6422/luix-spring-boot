package com.luixtech.boot.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for auto-configuring an {@link com.luixtech.core.crypto.AesGcmCrypto} bean.
 */
@ConfigurationProperties(prefix = "luix.crypto")
public class LuixCryptoProperties {

    /**
     * Master encryption key string (hashed via SHA-256 to derive the 256-bit AES key).
     */
    private String encryptionKey;

    /**
     * Base64 encoding variant used for serialized ciphertexts.
     */
    private Encoding encoding = Encoding.STANDARD;

    public enum Encoding {
        STANDARD,
        URL_SAFE
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public void setEncoding(Encoding encoding) {
        this.encoding = encoding != null ? encoding : Encoding.STANDARD;
    }
}
