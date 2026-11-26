package com.luixtech.boot.web;

import com.luixtech.boot.user.AdminUserGuardException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Reusable base class for {@code @RestControllerAdvice} global exception handlers.
 *
 * <p>Applications subclass this with {@code @RestControllerAdvice} (optionally scoped via
 * {@code basePackages} or {@code assignableTypes}) to get consistent {@link ApiError} JSON responses
 * while freely adding domain-specific {@code @ExceptionHandler} methods.
 */
public abstract class LuixRestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LuixRestExceptionHandler.class);

    @ExceptionHandler(AdminUserGuardException.class)
    public ResponseEntity<ApiError> handleAdminUserGuardException(AdminUserGuardException ex) {
        log.warn("Admin user guard rejected operation [{}]: {}", ex.getReason(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ex.getReason().name(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("INVALID_ARGUMENT", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("DATA_CONFLICT", "Data conflict"));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiError> handleAccessDeniedException(Exception ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("ACCESS_DENIED", "Access denied"));
    }
}
