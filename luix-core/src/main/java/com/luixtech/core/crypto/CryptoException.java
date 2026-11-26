package com.luixtech.core.crypto;

/**
 * Unchecked exception thrown when cryptographic initialization, encryption, or decryption fails.
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
