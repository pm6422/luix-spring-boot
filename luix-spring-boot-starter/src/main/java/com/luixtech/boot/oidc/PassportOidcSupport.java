package com.luixtech.boot.oidc;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reusable Spring Security 7 helpers for Logto / Luixtech Passport OIDC clients and resource servers.
 */
public final class PassportOidcSupport {

    public static final String DEFAULT_AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";

    private PassportOidcSupport() {
    }

    /**
     * Creates an {@link OidcIdTokenDecoderFactory} configured for {@link SignatureAlgorithm#ES384}
     * (the default ID token signing algorithm used by Logto / Luixtech Passport).
     */
    public static JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        return idTokenDecoderFactory(SignatureAlgorithm.ES384);
    }

    /**
     * Creates an {@link OidcIdTokenDecoderFactory} pinned to the given JWS {@link SignatureAlgorithm}.
     */
    public static JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(SignatureAlgorithm algorithm) {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwsAlgorithmResolver(clientRegistration -> algorithm);
        return factory;
    }

    /**
     * Creates a {@link JwtTypeValidator} accepting both RFC 9068 {@code at+jwt} (Logto M2M access tokens)
     * and standard {@code JWT} tokens (as well as tokens without a {@code typ} header).
     */
    public static OAuth2TokenValidator<Jwt> jwtTypeValidator() {
        JwtTypeValidator validator = new JwtTypeValidator("at+jwt", "JWT");
        validator.setAllowEmpty(true);
        return validator;
    }

    /**
     * Creates an {@link OAuth2AuthorizationRequestResolver} that forwards the {@code prompt} query parameter
     * (e.g. {@code ?prompt=login} or {@code ?prompt=consent}) to the OIDC authorization endpoint.
     */
    public static OAuth2AuthorizationRequestResolver promptForwardingAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        return promptForwardingAuthorizationRequestResolver(
                clientRegistrationRepository,
                DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    }

    /**
     * Creates a {@code prompt}-forwarding {@link OAuth2AuthorizationRequestResolver} using a custom base URI.
     */
    public static OAuth2AuthorizationRequestResolver promptForwardingAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            String authorizationRequestBaseUri) {
        Objects.requireNonNull(clientRegistrationRepository, "clientRegistrationRepository must not be null");
        DefaultOAuth2AuthorizationRequestResolver delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                authorizationRequestBaseUri);
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return appendPrompt(delegate.resolve(request), request);
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return appendPrompt(delegate.resolve(request, clientRegistrationId), request);
            }

            private OAuth2AuthorizationRequest appendPrompt(OAuth2AuthorizationRequest req, HttpServletRequest request) {
                if (req == null) {
                    return null;
                }
                String prompt = request.getParameter("prompt");
                if (prompt != null && !prompt.isBlank()) {
                    Map<String, Object> additionalParameters = new HashMap<>(req.getAdditionalParameters());
                    additionalParameters.put("prompt", prompt.trim());
                    return OAuth2AuthorizationRequest.from(req)
                            .additionalParameters(additionalParameters)
                            .build();
                }
                return req;
            }
        };
    }

    /**
     * Creates an {@link OidcClientInitiatedLogoutSuccessHandler} configured to redirect back to {@code appBaseUrl}
     * (normalized with a trailing slash) after RP-initiated OIDC logout.
     */
    public static LogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository,
            String appBaseUrl) {
        Objects.requireNonNull(clientRegistrationRepository, "clientRegistrationRepository must not be null");
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            throw new IllegalArgumentException("appBaseUrl must not be blank");
        }
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        String trimmed = appBaseUrl.trim();
        String targetUrl = trimmed.endsWith("/") ? trimmed : trimmed + "/";
        handler.setPostLogoutRedirectUri(targetUrl);
        handler.setDefaultTargetUrl(targetUrl);
        return handler;
    }

    /**
     * Resolves the configured OIDC user-name attribute name from {@code userRequest}, falling back to {@code "sub"}.
     */
    public static String resolveUserNameAttributeName(OidcUserRequest userRequest) {
        if (userRequest != null
                && userRequest.getClientRegistration() != null
                && userRequest.getClientRegistration().getProviderDetails() != null
                && userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint() != null) {
            String nameAttr = userRequest.getClientRegistration()
                    .getProviderDetails()
                    .getUserInfoEndpoint()
                    .getUserNameAttributeName();
            if (nameAttr != null && !nameAttr.isBlank()) {
                return nameAttr;
            }
        }
        return "sub";
    }
}
