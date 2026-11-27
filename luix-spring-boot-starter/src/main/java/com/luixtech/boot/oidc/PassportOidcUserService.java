package com.luixtech.boot.oidc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reusable {@link OidcUserService} for Logto / Luixtech Passport applications.
 *
 * <p>Synchronizes OIDC claims into the application's {@link OidcAccountStore}, automatically flags the
 * very first user on an empty table, blocks disabled accounts with error code {@value #ACCOUNT_DISABLED_ERROR},
 * and maps database authorities onto the returned {@link DefaultOidcUser}.
 *
 * @param <U> application user type implementing {@link OidcAccount}
 */
public class PassportOidcUserService<U extends OidcAccount> extends OidcUserService {

    public static final String ACCOUNT_DISABLED_ERROR = "account_disabled";

    private static final Logger log = LoggerFactory.getLogger(PassportOidcUserService.class);

    private final OidcAccountStore<U> accountStore;
    private final Function<Supplier<U>, U> transactionExecutor;

    /**
     * Creates a service executing account synchronization directly on the calling thread.
     */
    public PassportOidcUserService(OidcAccountStore<U> accountStore) {
        this(accountStore, action -> action.get());
    }

    /**
     * Creates a service wrapping {@link #syncUserOnLogin(OidcUser)} in a custom executor
     * (for example, {@code action -> transactionTemplate.execute(status -> action.get())}).
     */
    public PassportOidcUserService(OidcAccountStore<U> accountStore,
                                   Function<Supplier<U>, U> transactionExecutor) {
        this.accountStore = Objects.requireNonNull(accountStore, "accountStore must not be null");
        this.transactionExecutor = Objects.requireNonNull(transactionExecutor, "transactionExecutor must not be null");
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegateLoadUser(userRequest);
        U account = syncUserOnLogin(oidcUser);
        if (!account.isEnabled()) {
            log.warn("Blocked sign-in for disabled user sub={} email={}", account.getOidcSub(), account.getEmail());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(ACCOUNT_DISABLED_ERROR),
                    "User account is disabled");
        }

        List<GrantedAuthority> authorities = toGrantedAuthorities(account.getAuthorities());
        String nameAttr = PassportOidcSupport.resolveUserNameAttributeName(userRequest);
        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), nameAttr);
    }

    /**
     * Synchronizes an {@link OidcUser} into the backing {@link OidcAccountStore}.
     */
    public U syncUserOnLogin(OidcUser oidcUser) {
        OidcUserClaims claims = OidcUserClaims.from(oidcUser);
        Instant now = Instant.now();

        return transactionExecutor.apply(() -> {
            U account = accountStore.findBySubject(claims.subject())
                    .or(() -> claims.email() != null
                            ? accountStore.findByEmailIgnoreCase(claims.email())
                            : Optional.empty())
                    .orElse(null);

            if (account == null) {
                boolean firstUser = accountStore.countUsers() == 0;
                account = accountStore.createNewAccount(claims, firstUser, now);
                log.info("Registered new OIDC user sub={} email={} firstUser={}",
                        claims.subject(), claims.email(), firstUser);
            } else {
                accountStore.updateExistingAccount(account, claims, now);
            }

            return accountStore.save(account);
        });
    }

    /**
     * Hook for delegating to {@link OidcUserService#loadUser(OidcUserRequest)} (overridable in tests).
     */
    protected OidcUser delegateLoadUser(OidcUserRequest userRequest) {
        return super.loadUser(userRequest);
    }

    static List<GrantedAuthority> toGrantedAuthorities(Collection<String> rawAuthorities) {
        if (rawAuthorities == null || rawAuthorities.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String authority : rawAuthorities) {
            if (authority != null && !authority.isBlank()) {
                normalized.add(authority.trim());
            }
        }
        return normalized.stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}
