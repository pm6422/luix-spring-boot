package com.luixtech.boot.user;

import java.util.Objects;

/**
 * Exception thrown by {@link AdminUserGuard} when an administrative user mutation violates safety invariants.
 */
public class AdminUserGuardException extends IllegalArgumentException {

    private final Reason reason;

    public AdminUserGuardException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        INVALID_ROLE,
        CANNOT_DEMOTE_SELF,
        CANNOT_DISABLE_SELF,
        CANNOT_DELETE_SELF,
        LAST_ACTIVE_ADMIN
    }
}
