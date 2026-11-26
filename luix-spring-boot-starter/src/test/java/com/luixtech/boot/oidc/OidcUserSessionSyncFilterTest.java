package com.luixtech.boot.oidc;

import com.luixtech.boot.oidc.PassportOidcUserServiceTest.InMemoryAccountStore;
import com.luixtech.boot.oidc.PassportOidcUserServiceTest.TestAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OidcUserSessionSyncFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshesAuthoritiesInSecurityContextAndSessionAndCachesAccountOnRequest() throws Exception {
        InMemoryAccountStore store = new InMemoryAccountStore();
        TestAccount account = new TestAccount("u-1", "sub-1", "admin@luixtech.com", "Admin", true, List.of("ROLE_ADMIN", "ROLE_USER"));
        store.save(account);

        PassportOidcUserService<TestAccount> oidcService = new PassportOidcUserService<>(store);
        OidcUserSessionSyncFilter<TestAccount> filter = new OidcUserSessionSyncFilter<>(
                store, oidcService, OidcUserSessionSyncFilter.jsonDisabledHandler());

        OidcUser stalePrincipal = PassportOidcUserServiceTest.oidcUser("sub-1", "admin@luixtech.com", "Admin");
        OAuth2AuthenticationToken staleToken = new OAuth2AuthenticationToken(
                stalePrincipal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "passport");
        SecurityContextHolder.getContext().setAuthentication(staleToken);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(OidcUserSessionSyncFilter.getCurrentAccount(request, TestAccount.class)).contains(account);
        Authentication refreshed = SecurityContextHolder.getContext().getAuthentication();
        assertThat(refreshed.getAuthorities()).extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        SecurityContext sessionContext = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(sessionContext).isNotNull();
        assertThat(sessionContext.getAuthentication().getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void revokesSessionAndReturnsJson401ForDisabledAccount() throws Exception {
        InMemoryAccountStore store = new InMemoryAccountStore();
        store.save(new TestAccount("u-1", "sub-1", "disabled@luixtech.com", "Disabled", false, List.of("ROLE_USER")));

        PassportOidcUserService<TestAccount> oidcService = new PassportOidcUserService<>(store);
        OidcUserSessionSyncFilter<TestAccount> filter = new OidcUserSessionSyncFilter<>(
                store, oidcService, OidcUserSessionSyncFilter.hybridDisabledHandler("/api/", "/?error=account_disabled"));

        OidcUser principal = PassportOidcUserServiceTest.oidcUser("sub-1", "disabled@luixtech.com", "Disabled");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "passport"));

        MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/api/positions");
        MockHttpSession session = new MockHttpSession();
        apiRequest.setSession(session);
        MockHttpServletResponse apiResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(apiRequest, apiResponse, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(session.isInvalid()).isTrue();
        assertThat(apiResponse.getStatus()).isEqualTo(401);
        assertThat(apiResponse.getContentAsString()).contains("account_disabled");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void redirectsHtmxAndBrowserRequestsForDisabledAccount() throws Exception {
        InMemoryAccountStore store = new InMemoryAccountStore();
        store.save(new TestAccount("u-1", "sub-1", "disabled@luixtech.com", "Disabled", false, List.of("ROLE_VIEWER")));

        PassportOidcUserService<TestAccount> oidcService = new PassportOidcUserService<>(store);
        OidcUserSessionSyncFilter<TestAccount> filter = new OidcUserSessionSyncFilter<>(
                store, oidcService, OidcUserSessionSyncFilter.redirectDisabledHandler("/?error=account_disabled"));

        OidcUser principal = PassportOidcUserServiceTest.oidcUser("sub-1", "disabled@luixtech.com", "Disabled");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(principal, List.of(new SimpleGrantedAuthority("ROLE_VIEWER")), "passport"));

        MockHttpServletRequest htmxReq = new MockHttpServletRequest("GET", "/jobs");
        htmxReq.addHeader("HX-Request", "true");
        MockHttpServletResponse htmxResp = new MockHttpServletResponse();
        filter.doFilter(htmxReq, htmxResp, new MockFilterChain());

        assertThat(htmxResp.getStatus()).isEqualTo(401);
        assertThat(htmxResp.getHeader("HX-Redirect")).isEqualTo("/?error=account_disabled");

        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(principal, List.of(new SimpleGrantedAuthority("ROLE_VIEWER")), "passport"));
        MockHttpServletRequest browserReq = new MockHttpServletRequest("GET", "/jobs");
        MockHttpServletResponse browserResp = new MockHttpServletResponse();
        filter.doFilter(browserReq, browserResp, new MockFilterChain());

        assertThat(browserResp.getRedirectedUrl()).isEqualTo("/?error=account_disabled");
    }
}
