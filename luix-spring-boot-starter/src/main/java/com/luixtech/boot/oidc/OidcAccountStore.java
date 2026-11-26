package com.luixtech.boot.oidc;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence SPI implemented by each application to map its own user table/repository
 * to {@link PassportOidcUserService} and {@link OidcUserSessionSyncFilter}.
 *
 * @param <U> application user type implementing {@link OidcAccount}
 */
public interface OidcAccountStore<U extends OidcAccount> {

    /**
     * Finds a user by their stable OIDC {@code sub} identifier.
     */
    Optional<U> findBySubject(String subject);

    /**
     * Fallback lookup by email address (case-insensitive) when no user matches {@code subject}.
     */
    Optional<U> findByEmailIgnoreCase(String email);

    /**
     * Returns total persisted user count (used to detect first-user admin bootstrapping).
     */
    long countUsers();

    /**
     * Creates a new (unsaved) account populated from normalized OIDC claims.
     *
     * @param claims    normalized claims from the OIDC provider
     * @param firstUser {@code true} when {@link #countUsers()} is {@code 0}
     * @param loginAt   timestamp of the current login
     */
    U createNewAccount(OidcUserClaims claims, boolean firstUser, Instant loginAt);

    /**
     * Updates an existing account with fresh OIDC claims and login timestamp.
     */
    void updateExistingAccount(U account, OidcUserClaims claims, Instant loginAt);

    /**
     * Persists a newly created or updated account and returns the saved instance.
     */
    U save(U account);
}
