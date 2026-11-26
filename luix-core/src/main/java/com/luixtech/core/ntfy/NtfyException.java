package com.luixtech.core.ntfy;

/**
 * Unchecked exception thrown when publishing a notification to an ntfy server fails.
 */
public class NtfyException extends RuntimeException {

    private final int statusCode;

    public NtfyException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public NtfyException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public NtfyException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Returns the HTTP status code returned by the ntfy server, or {@code -1} for transport/client errors.
     */
    public int statusCode() {
        return statusCode;
    }
}
