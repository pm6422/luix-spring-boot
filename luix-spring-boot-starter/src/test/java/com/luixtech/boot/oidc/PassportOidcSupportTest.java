package com.luixtech.boot.oidc;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PassportOidcSupportTest {

    @Test
    void forwardsPromptQueryParameterOnAuthorizationRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("passport")
                .clientId("cid")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://passport.luixtech.com/oidc/auth")
                .tokenUri("https://passport.luixtech.com/oidc/token")
                .build();
        OAuth2AuthorizationRequestResolver resolver = PassportOidcSupport.promptForwardingAuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(registration));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/passport");
        request.setServletPath("/oauth2/authorization/passport");
        request.setParameter("prompt", "login");

        OAuth2AuthorizationRequest resolved = resolver.resolve(request);
        assertThat(resolved).isNotNull();
        assertThat(resolved.getAdditionalParameters()).containsEntry("prompt", "login");
    }

    @Test
    void extractsNormalizedClaimsWithFallbacks() {
        DefaultOAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "sub-123", "email", "alice@luixtech.com"),
                "sub");

        OidcUserClaims claims = OidcUserClaims.from(user);
        assertThat(claims.subject()).isEqualTo("sub-123");
        assertThat(claims.email()).isEqualTo("alice@luixtech.com");
        assertThat(claims.displayName()).isEqualTo("alice");
        assertThat(claims.picture()).isNull();

        DefaultOAuth2User noEmail = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "sub-999"),
                "sub");
        OidcUserClaims fallbackClaims = OidcUserClaims.from(noEmail);
        assertThat(fallbackClaims.displayName()).isEqualTo("sub-999");
        assertThat(fallbackClaims.emailOrFallback("passport.luixtech.com"))
                .isEqualTo("sub-999@passport.luixtech.com");
    }
}
