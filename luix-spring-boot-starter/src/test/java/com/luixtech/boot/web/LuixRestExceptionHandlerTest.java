package com.luixtech.boot.web;

import com.luixtech.boot.user.AdminUserGuardException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class LuixRestExceptionHandlerTest {

    private final LuixRestExceptionHandler handler = new LuixRestExceptionHandler() {
    };

    @Test
    void mapsCommonExceptionsToStructuredApiError() {
        ResponseEntity<ApiError> guardResp = handler.handleAdminUserGuardException(
                new AdminUserGuardException(AdminUserGuardException.Reason.CANNOT_DELETE_SELF, "Cannot delete self"));
        assertThat(guardResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(guardResp.getBody()).isEqualTo(ApiError.of("CANNOT_DELETE_SELF", "Cannot delete self"));

        ResponseEntity<ApiError> argResp = handler.handleIllegalArgumentException(new IllegalArgumentException("Bad input"));
        assertThat(argResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(argResp.getBody()).isEqualTo(ApiError.of("INVALID_ARGUMENT", "Bad input"));

        ResponseEntity<ApiError> conflictResp = handler.handleDataIntegrityViolationException(
                new DataIntegrityViolationException("duplicate key"));
        assertThat(conflictResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflictResp.getBody()).isEqualTo(ApiError.of("DATA_CONFLICT", "Data conflict"));

        ResponseEntity<ApiError> deniedResp = handler.handleAccessDeniedException(new AccessDeniedException("denied"));
        assertThat(deniedResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deniedResp.getBody()).isEqualTo(ApiError.of("ACCESS_DENIED", "Access denied"));
    }
}
