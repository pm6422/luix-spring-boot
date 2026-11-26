package com.luixtech.boot.web;

/**
 * Standard JSON error payload returned by REST endpoints across Luixtech services.
 *
 * @param error   machine-readable error code (e.g. {@code CANNOT_DELETE_SELF}, {@code DATA_CONFLICT})
 * @param message human-readable error message
 */
public record ApiError(String error, String message) {

    public static ApiError of(String error, String message) {
        return new ApiError(error, message);
    }
}
