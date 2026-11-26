package com.luixtech.boot.oidc;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the current authenticated {@link OidcAccount} and its database user ID.
 *
 * <p>Prefers the account cached on the active {@link HttpServletRequest} by {@link OidcUserSessionSyncFilter}
 * (zero extra database queries per request), falling back to {@link OidcAccountStore} lookup by OIDC subject
 * and case-insensitive email.
 *
 * @param <U> application user type implementing {@link OidcAccount}
 */
public class CurrentOidcAccountResolver<U extends OidcAccount> {

    private final OidcAccountStore<U> accountStore;
    private final Class<U> accountType;

    public CurrentOidcAccountResolver(OidcAccountStore<U> accountStore, Class<U> accountType) {
        this.accountStore = Objects.requireNonNull(accountStore, "accountStore must not be null");
        this.accountType = Objects.requireNonNull(accountType, "accountType must not be null");
    }

    /**
     * Resolves the current account using {@link RequestContextHolder} and {@link SecurityContextHolder}.
     */
    public Optional<U> getCurrentAccount() {
        HttpServletRequest request = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            request = attrs.getRequest();
        }
        return getCurrentAccount(request, SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Resolves the current account from an explicit {@link HttpServletRequest} and {@link Authentication}.
     */
    public Optional<U> getCurrentAccount(HttpServletRequest request, Authentication authentication) {
        Optional<U> cached = OidcUserSessionSyncFilter.getCurrentAccount(request, accountType);
        if (cached.isPresent()) {
            return cached;
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        String subject = PassportOidcSupport.resolveSubject(authentication);
        String email = (authentication.getPrincipal() instanceof OAuth2User oauth2User)
                ? oauth2User.getAttribute("email")
                : null;

        Optional<U> bySub = (subject != null && !subject.isBlank())
                ? accountStore.findBySubject(subject)
                : Optional.empty();
        if (bySub.isPresent()) {
            return bySub;
        }
        if (email != null && !email.isBlank()) {
            return accountStore.findByEmailIgnoreCase(email);
        }
        return Optional.empty();
    }

    /**
     * Returns the current authenticated account's database ID, if present.
     */
    public Optional<String> getCurrentUserId() {
        return getCurrentAccount().map(account -> account.getId());
    }

    /**
     * Returns the current authenticated account's database ID or throws {@link IllegalStateException}.
     */
    public String requireCurrentUserId() {
        return getCurrentUserId().orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
    }
}
