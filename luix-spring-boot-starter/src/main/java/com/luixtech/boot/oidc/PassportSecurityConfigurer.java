package com.luixtech.boot.oidc;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reusable Spring Security 7 configurator wiring the standard Logto / Luixtech Passport OIDC login,
 * RP-initiated logout, live session sync filter, JSON/redirect exception handling, and CSRF rules
 * onto an {@link HttpSecurity} chain while leaving route authorization rules to the host application.
 */
public final class PassportSecurityConfigurer<U extends OidcAccount> {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OidcAccountStore<U> accountStore;
    private final PassportOidcUserService<U> oidcUserService;
    private final String appUrl;
    private final String[] apiPrefixes;
    private final String unauthenticatedRedirectUrl;
    private final String accessDeniedRedirectUrl;
    private final String disabledRedirectUrl;
    private final String failureErrorCode;
    private final Set<String> logoutPaths;
    private final String[] deleteCookies;
    private final boolean disableCsrf;

    private PassportSecurityConfigurer(Builder<U> builder) {
        this.clientRegistrationRepository = builder.clientRegistrationRepository;
        this.accountStore = builder.accountStore;
        this.oidcUserService = builder.oidcUserService;
        this.appUrl = normalizeBaseUrl(builder.appUrl);
        this.apiPrefixes = builder.apiPrefixes.toArray(String[]::new);
        this.unauthenticatedRedirectUrl = builder.unauthenticatedRedirectUrl != null
                ? builder.unauthenticatedRedirectUrl
                : "/";
        this.accessDeniedRedirectUrl = builder.accessDeniedRedirectUrl != null
                ? builder.accessDeniedRedirectUrl
                : "/";
        this.disabledRedirectUrl = builder.disabledRedirectUrl != null
                ? builder.disabledRedirectUrl
                : (this.appUrl + "/?error=account_disabled");
        this.failureErrorCode = (builder.failureErrorCode != null && !builder.failureErrorCode.isBlank())
                ? builder.failureErrorCode.trim()
                : "auth_failed";
        this.logoutPaths = Set.copyOf(builder.logoutPaths);
        this.deleteCookies = builder.deleteCookies.toArray(String[]::new);
        this.disableCsrf = builder.disableCsrf;
    }

    public static <U extends OidcAccount> Builder<U> builder(
            ClientRegistrationRepository clientRegistrationRepository,
            OidcAccountStore<U> accountStore,
            PassportOidcUserService<U> oidcUserService,
            String appUrl) {
        return new Builder<>(clientRegistrationRepository, accountStore, oidcUserService, appUrl);
    }

    /**
     * Applies CSRF, {@link OidcUserSessionSyncFilter}, JSON/redirect exception handling,
     * Passport OIDC login, and RP-initiated logout onto {@code http}.
     */
    public HttpSecurity apply(HttpSecurity http) throws Exception {
        Objects.requireNonNull(http, "http must not be null");

        String primaryApiPrefix = apiPrefixes.length > 0 ? apiPrefixes[0] : "/api/";
        OidcUserSessionSyncFilter<U> sessionSyncFilter = new OidcUserSessionSyncFilter<>(
                accountStore,
                oidcUserService,
                OidcUserSessionSyncFilter.hybridDisabledHandler(primaryApiPrefix, disabledRedirectUrl));

        if (disableCsrf) {
            http.csrf(csrf -> csrf.disable());
        } else {
            List<String> ignored = new ArrayList<>();
            for (String prefix : apiPrefixes) {
                String normalized = prefix.endsWith("/") ? prefix + "**" : prefix + "/**";
                ignored.add(normalized);
            }
            ignored.addAll(logoutPaths);
            String[] ignoredPatterns = ignored.toArray(String[]::new);
            http.csrf(csrf -> csrf.ignoringRequestMatchers(ignoredPatterns));
        }

        http
                .addFilterBefore(sessionSyncFilter, AuthorizationFilter.class)
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(PassportOidcSupport.jsonOrRedirectAuthenticationEntryPoint(
                                unauthenticatedRedirectUrl, apiPrefixes))
                        .accessDeniedHandler(PassportOidcSupport.jsonOrRedirectAccessDeniedHandler(
                                accessDeniedRedirectUrl, apiPrefixes)))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestResolver(
                                        PassportOidcSupport.promptForwardingAuthorizationRequestResolver(clientRegistrationRepository)))
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(oidcUserService))
                        .successHandler((request, response, authentication) -> response
                                .sendRedirect(appUrl + "/"))
                        .failureHandler(PassportOidcSupport.oidcFailureHandler(appUrl, failureErrorCode)))
                .logout(logout -> {
                    if (logoutPaths.size() > 1) {
                        List<RequestMatcher> matchers = new ArrayList<>();
                        for (String path : logoutPaths) {
                            matchers.add(PathPatternRequestMatcher.pathPattern(path));
                        }
                        logout.logoutRequestMatcher(new OrRequestMatcher(matchers));
                    }
                    logout
                            .logoutSuccessHandler(PassportOidcSupport.oidcLogoutSuccessHandler(
                                    clientRegistrationRepository,
                                    appUrl))
                            .invalidateHttpSession(true)
                            .deleteCookies(deleteCookies);
                });

        return http;
    }

    private static String normalizeBaseUrl(String appUrl) {
        if (appUrl == null || appUrl.isBlank()) {
            throw new IllegalArgumentException("appUrl must not be blank");
        }
        String trimmed = appUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static final class Builder<U extends OidcAccount> {

        private final ClientRegistrationRepository clientRegistrationRepository;
        private final OidcAccountStore<U> accountStore;
        private final PassportOidcUserService<U> oidcUserService;
        private final String appUrl;
        private final List<String> apiPrefixes = new ArrayList<>(List.of("/api/"));
        private final Set<String> logoutPaths = new LinkedHashSet<>(List.of("/logout"));
        private final List<String> deleteCookies = new ArrayList<>(List.of("SESSION", "JSESSIONID"));
        private String unauthenticatedRedirectUrl;
        private String accessDeniedRedirectUrl;
        private String disabledRedirectUrl;
        private String failureErrorCode = "auth_failed";
        private boolean disableCsrf = false;

        private Builder(ClientRegistrationRepository clientRegistrationRepository,
                        OidcAccountStore<U> accountStore,
                        PassportOidcUserService<U> oidcUserService,
                        String appUrl) {
            this.clientRegistrationRepository = Objects.requireNonNull(clientRegistrationRepository, "clientRegistrationRepository must not be null");
            this.accountStore = Objects.requireNonNull(accountStore, "accountStore must not be null");
            this.oidcUserService = Objects.requireNonNull(oidcUserService, "oidcUserService must not be null");
            this.appUrl = appUrl;
        }

        public Builder<U> apiPrefixes(String... prefixes) {
            this.apiPrefixes.clear();
            if (prefixes != null) {
                for (String p : prefixes) {
                    if (p != null && !p.isBlank()) {
                        this.apiPrefixes.add(p.trim());
                    }
                }
            }
            return this;
        }

        public Builder<U> unauthenticatedRedirectUrl(String url) {
            this.unauthenticatedRedirectUrl = url;
            return this;
        }

        public Builder<U> accessDeniedRedirectUrl(String url) {
            this.accessDeniedRedirectUrl = url;
            return this;
        }

        public Builder<U> disabledRedirectUrl(String url) {
            this.disabledRedirectUrl = url;
            return this;
        }

        public Builder<U> failureErrorCode(String errorCode) {
            this.failureErrorCode = errorCode;
            return this;
        }

        public Builder<U> extraLogoutPaths(String... paths) {
            if (paths != null) {
                for (String p : paths) {
                    if (p != null && !p.isBlank()) {
                        this.logoutPaths.add(p.trim());
                    }
                }
            }
            return this;
        }

        public Builder<U> deleteCookies(String... cookieNames) {
            this.deleteCookies.clear();
            if (cookieNames != null) {
                for (String c : cookieNames) {
                    if (c != null && !c.isBlank()) {
                        this.deleteCookies.add(c.trim());
                    }
                }
            }
            return this;
        }

        public Builder<U> disableCsrf(boolean disableCsrf) {
            this.disableCsrf = disableCsrf;
            return this;
        }

        public PassportSecurityConfigurer<U> build() {
            return new PassportSecurityConfigurer<>(this);
        }

        public HttpSecurity apply(HttpSecurity http) throws Exception {
            return build().apply(http);
        }
    }
}
