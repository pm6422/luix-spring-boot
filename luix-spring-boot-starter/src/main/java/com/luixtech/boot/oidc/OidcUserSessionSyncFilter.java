package com.luixtech.boot.oidc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps active HTTP sessions synchronized with live database account state (enabled flag and authorities).
 *
 * <p>Role promotions/demotions and account disables take effect immediately on the next request without
 * forcing re-login, and the resolved account is cached on {@link HttpServletRequest} under
 * {@link #CURRENT_ACCOUNT_ATTR} so downstream controllers/services can avoid redundant lookups.
 *
 * @param <U> application user type implementing {@link OidcAccount}
 */
public class OidcUserSessionSyncFilter<U extends OidcAccount> extends OncePerRequestFilter {

    public static final String CURRENT_ACCOUNT_ATTR =
            OidcUserSessionSyncFilter.class.getName() + ".CURRENT_ACCOUNT";

    private static final Logger log = LoggerFactory.getLogger(OidcUserSessionSyncFilter.class);

    private final OidcAccountStore<U> accountStore;
    private final PassportOidcUserService<U> oidcUserService;
    private final DisabledAccountHandler<U> disabledAccountHandler;

    public OidcUserSessionSyncFilter(OidcAccountStore<U> accountStore,
                                     PassportOidcUserService<U> oidcUserService,
                                     DisabledAccountHandler<U> disabledAccountHandler) {
        this.accountStore = Objects.requireNonNull(accountStore, "accountStore must not be null");
        this.oidcUserService = Objects.requireNonNull(oidcUserService, "oidcUserService must not be null");
        this.disabledAccountHandler = Objects.requireNonNull(disabledAccountHandler, "disabledAccountHandler must not be null");
    }

    /**
     * Retrieves the {@link OidcAccount} resolved by this filter for the current request, if present.
     */
    public static <T> Optional<T> getCurrentAccount(HttpServletRequest request, Class<T> type) {
        if (request == null || type == null) {
            return Optional.empty();
        }
        Object attr = request.getAttribute(CURRENT_ACCOUNT_ATTR);
        if (type.isInstance(attr)) {
            return Optional.of(type.cast(attr));
        }
        return Optional.empty();
    }

    /**
     * Handler responding with HTTP 401 JSON when a disabled user's session is revoked (suited for REST SPAs).
     */
    public static <U extends OidcAccount> DisabledAccountHandler<U> jsonDisabledHandler() {
        return (request, response, account) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"account_disabled\",\"message\":\"User account is disabled\"}");
        };
    }

    /**
     * Handler redirecting browser/HTMX requests when a disabled user's session is revoked (suited for SSR/HTMX).
     */
    public static <U extends OidcAccount> DisabledAccountHandler<U> redirectDisabledHandler(String redirectUrl) {
        Objects.requireNonNull(redirectUrl, "redirectUrl must not be null");
        return (request, response, account) -> {
            if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
                response.setHeader("HX-Redirect", redirectUrl);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                response.sendRedirect(redirectUrl);
            }
        };
    }

    /**
     * Hybrid handler returning JSON 401 for paths starting with {@code apiPrefix} (e.g. {@code "/api/"})
     * and redirecting browser/HTMX requests otherwise.
     */
    public static <U extends OidcAccount> DisabledAccountHandler<U> hybridDisabledHandler(
            String apiPrefix, String redirectUrl) {
        Objects.requireNonNull(apiPrefix, "apiPrefix must not be null");
        DisabledAccountHandler<U> jsonHandler = jsonDisabledHandler();
        DisabledAccountHandler<U> redirectHandler = redirectDisabledHandler(redirectUrl);
        return (request, response, account) -> {
            String uri = request.getRequestURI();
            if (uri != null && uri.startsWith(apiPrefix)) {
                jsonHandler.onDisabled(request, response, account);
            } else {
                redirectHandler.onDisabled(request, response, account);
            }
        };
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof OAuth2AuthenticationToken oauth2Token
                && oauth2Token.isAuthenticated()
                && oauth2Token.getPrincipal() instanceof OidcUser oidcUser) {

            String sub = oidcUser.getSubject();
            if (sub != null && !sub.isBlank()) {
                U account = accountStore.findBySubject(sub)
                        .orElseGet(() -> oidcUserService.syncUserOnLogin(oidcUser));

                if (!account.isEnabled()) {
                    log.info("Revoking session for disabled user sub={} email={}",
                            account.getOidcSub(), account.getEmail());
                    SecurityContextHolder.clearContext();
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.invalidate();
                    }
                    disabledAccountHandler.onDisabled(request, response, account);
                    return;
                }

                request.setAttribute(CURRENT_ACCOUNT_ATTR, account);
                syncAuthoritiesIfChanged(request, oauth2Token, oidcUser, account);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void syncAuthoritiesIfChanged(HttpServletRequest request,
                                          OAuth2AuthenticationToken oauth2Token,
                                          OidcUser oidcUser,
                                          U account) {
        Set<String> expected = new LinkedHashSet<>();
        if (account.getAuthorities() != null) {
            for (String authority : account.getAuthorities()) {
                if (authority != null && !authority.isBlank()) {
                    expected.add(authority.trim());
                }
            }
        }

        Set<String> current = new LinkedHashSet<>();
        for (GrantedAuthority authority : oauth2Token.getAuthorities()) {
            if (authority != null && authority.getAuthority() != null) {
                current.add(authority.getAuthority());
            }
        }

        if (expected.equals(current)) {
            return;
        }

        List<GrantedAuthority> refreshedAuthorities = PassportOidcUserService.toGrantedAuthorities(expected);
        OidcUser refreshedPrincipal = new DefaultOidcUser(
                refreshedAuthorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "sub");
        OAuth2AuthenticationToken refreshedAuth = new OAuth2AuthenticationToken(
                refreshedPrincipal,
                refreshedAuthorities,
                oauth2Token.getAuthorizedClientRegistrationId());
        refreshedAuth.setDetails(oauth2Token.getDetails());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(refreshedAuth);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }
    }

    /**
     * Callback invoked after clearing the security context and invalidating the session of a disabled account.
     */
    @FunctionalInterface
    public interface DisabledAccountHandler<U extends OidcAccount> {
        void onDisabled(HttpServletRequest request, HttpServletResponse response, U account) throws IOException;
    }
}
