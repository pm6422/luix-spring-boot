package com.luixtech.boot.user;

import com.luixtech.boot.oidc.OidcAccount;

import java.util.Collection;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Stateless administrative safety guard preventing self-lockout and removal of the last active administrator.
 *
 * <p>Works identically across single-role and multi-role applications via {@link OidcAccount#getAuthorities()}.
 */
public final class AdminUserGuard {

    public static final String DEFAULT_ADMIN_AUTHORITY = "ROLE_ADMIN";

    private AdminUserGuard() {
    }

    /**
     * Validates a role/authority update using {@link #DEFAULT_ADMIN_AUTHORITY}.
     */
    public static void assertCanUpdateAuthorities(OidcAccount target,
                                                  Collection<String> targetAuthorities,
                                                  String actingSubject,
                                                  LongSupplier activeAdminCountSupplier) {
        assertCanUpdateAuthorities(target, targetAuthorities, actingSubject, DEFAULT_ADMIN_AUTHORITY, activeAdminCountSupplier);
    }

    /**
     * Validates a role/authority update:
     * <ul>
     *   <li>{@code targetAuthorities} must contain at least one non-blank authority</li>
     *   <li>the acting admin cannot remove {@code adminAuthority} from their own account</li>
     *   <li>the last enabled admin cannot have {@code adminAuthority} removed</li>
     * </ul>
     */
    public static void assertCanUpdateAuthorities(OidcAccount target,
                                                  Collection<String> targetAuthorities,
                                                  String actingSubject,
                                                  String adminAuthority,
                                                  LongSupplier activeAdminCountSupplier) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(adminAuthority, "adminAuthority must not be null");
        Objects.requireNonNull(activeAdminCountSupplier, "activeAdminCountSupplier must not be null");

        if (targetAuthorities == null || targetAuthorities.stream().noneMatch(a -> a != null && !a.isBlank())) {
            throw new AdminUserGuardException(
                    AdminUserGuardException.Reason.INVALID_ROLE,
                    "At least one valid role is required");
        }

        boolean willHaveAdmin = targetAuthorities.contains(adminAuthority);
        if (isSameUser(target, actingSubject) && !willHaveAdmin) {
            throw new AdminUserGuardException(
                    AdminUserGuardException.Reason.CANNOT_DEMOTE_SELF,
                    "You cannot remove your own administrator role");
        }

        if (target.isEnabled() && target.hasAuthority(adminAuthority) && !willHaveAdmin
                && activeAdminCountSupplier.getAsLong() <= 1L) {
            throw new AdminUserGuardException(
                    AdminUserGuardException.Reason.LAST_ACTIVE_ADMIN,
                    "At least one enabled administrator account must remain");
        }
    }

    /**
     * Validates an enabled-state change using {@link #DEFAULT_ADMIN_AUTHORITY}.
     */
    public static void assertCanSetEnabled(OidcAccount target,
                                           boolean targetEnabled,
                                           String actingSubject,
                                           LongSupplier activeAdminCountSupplier) {
        assertCanSetEnabled(target, targetEnabled, actingSubject, DEFAULT_ADMIN_AUTHORITY, activeAdminCountSupplier);
    }

    /**
     * Validates an enabled-state change:
     * <ul>
     *   <li>the acting admin cannot disable their own account</li>
     *   <li>the last enabled admin cannot be disabled</li>
     * </ul>
     */
    public static void assertCanSetEnabled(OidcAccount target,
                                           boolean targetEnabled,
                                           String actingSubject,
                                           String adminAuthority,
                                           LongSupplier activeAdminCountSupplier) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(adminAuthority, "adminAuthority must not be null");
        Objects.requireNonNull(activeAdminCountSupplier, "activeAdminCountSupplier must not be null");

        if (!target.isEnabled() || targetEnabled) {
            return;
        }

        if (isSameUser(target, actingSubject)) {
            throw new AdminUserGuardException(
                    AdminUserGuardException.Reason.CANNOT_DISABLE_SELF,
                    "You cannot disable your own account");
        }

        if (target.hasAuthority(adminAuthority) && activeAdminCountSupplier.getAsLong() <= 1L) {
            throw new AdminUserGuardException(
                    AdminUserGuardException.Reason.LAST_ACTIVE_ADMIN,
                    "At least one enabled administrator account must remain");
        }
    }

    /**
     * Validates an account deletion using {@link #DEFAULT_ADMIN_AUTHORITY}.
     */
    public static void assertCanDelete(OidcAccount target,
                                       String actingSubject,
                                       LongSupplier activeAdminCountSupplier) {
        assertCanDelete(target, actingSubject, DEFAULT_ADMIN_AUTHORITY, activeAdminCountSupplier);
    }

    /**
     * Validates an account deletion:
     * <ul>
     *   <li>the acting admin cannot delete their own account</li>
     *   <li>the last enabled admin cannot be deleted</li>
     * </ul>
     */
    public static void assertCanDelete(OidcAccount target,
                                       String actingSubject,
                                       String adminAuthority,
                                       LongSupplier activeAdminCountSupplier) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(adminAuthority, "adminAuthority must not be null");
        Objects.requireNonNull(activeAdminCountSupplier, "activeAdminCountSupplier must not be null");

        if (isSameUser(target, actingSubject)) {
            throw new AdminUserGuardException(
                    AdminUserGuardException.Reason.CANNOT_DELETE_SELF,
                    "You cannot delete your own account");
        }

        if (target.isEnabled() && target.hasAuthority(adminAuthority)
                && activeAdminCountSupplier.getAsLong() <= 1L) {
            throw new AdminUserGuardException(
                    AdminUserGuardException.Reason.LAST_ACTIVE_ADMIN,
                    "At least one enabled administrator account must remain");
        }
    }

    private static boolean isSameUser(OidcAccount target, String actingSubject) {
        return actingSubject != null
                && !actingSubject.isBlank()
                && actingSubject.equals(target.getOidcSub());
    }
}
