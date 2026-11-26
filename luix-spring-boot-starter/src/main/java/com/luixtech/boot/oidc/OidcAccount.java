package com.luixtech.boot.oidc;

import java.util.Collection;

/**
 * Minimal identity contract implemented by application user entities to integrate with
 * {@link PassportOidcUserService}, {@link OidcUserSessionSyncFilter}, and {@link com.luixtech.boot.user.AdminUserGuard}
 * without coupling {@code luix-spring-boot} to any specific database schema.
 */
public interface OidcAccount {

    /** Primary identifier of the user in the application database. */
    String getId();

    /** Stable OIDC {@code sub} identifier (e.g. Passport / Logto subject). */
    String getOidcSub();

    /** User email address, or {@code null} if absent. */
    String getEmail();

    /** Whether the account is permitted to sign in and access the application. */
    boolean isEnabled();

    /**
     * Spring Security authority strings granted to this account (e.g. {@code ROLE_ADMIN}, {@code ROLE_USER}, {@code ROLE_VIEWER}).
     */
    Collection<String> getAuthorities();

    /**
     * Returns {@code true} if {@link #getAuthorities()} contains the given authority string.
     */
    default boolean hasAuthority(String authority) {
        if (authority == null) {
            return false;
        }
        Collection<String> authorities = getAuthorities();
        return authorities != null && authorities.contains(authority);
    }
}
