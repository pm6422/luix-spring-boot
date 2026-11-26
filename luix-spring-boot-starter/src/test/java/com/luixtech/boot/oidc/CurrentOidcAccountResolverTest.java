package com.luixtech.boot.oidc;

import com.luixtech.boot.oidc.PassportOidcUserServiceTest.InMemoryAccountStore;
import com.luixtech.boot.oidc.PassportOidcUserServiceTest.TestAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentOidcAccountResolverTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsCachedAccountFromRequestAttributeWithoutStoreLookup() {
        InMemoryAccountStore emptyStore = new InMemoryAccountStore();
        CurrentOidcAccountResolver<TestAccount> resolver = new CurrentOidcAccountResolver<>(emptyStore, TestAccount.class);
        TestAccount cached = new TestAccount("u-cached", "sub-1", "a@luixtech.com", "Alice", true, List.of("ROLE_USER"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(OidcUserSessionSyncFilter.CURRENT_ACCOUNT_ATTR, cached);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(resolver.getCurrentAccount()).contains(cached);
        assertThat(resolver.getCurrentUserId()).contains("u-cached");
        assertThat(resolver.requireCurrentUserId()).isEqualTo("u-cached");
    }

    @Test
    void fallsBackToSecurityContextSubjectAndEmailLookup() {
        InMemoryAccountStore store = new InMemoryAccountStore();
        TestAccount saved = new TestAccount("u-2", "old-sub", "bob@luixtech.com", "Bob", true, List.of("ROLE_ADMIN"));
        store.save(saved);

        CurrentOidcAccountResolver<TestAccount> resolver = new CurrentOidcAccountResolver<>(store, TestAccount.class);
        OidcUser principal = PassportOidcUserServiceTest.oidcUser("new-sub", "BOB@luixtech.com", "Bob");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(principal, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), "passport"));

        assertThat(resolver.getCurrentAccount()).contains(saved);
        assertThat(resolver.requireCurrentUserId()).isEqualTo("u-2");
    }

    @Test
    void throwsWhenNoAuthenticatedUserIsFound() {
        CurrentOidcAccountResolver<TestAccount> resolver =
                new CurrentOidcAccountResolver<>(new InMemoryAccountStore(), TestAccount.class);

        assertThat(resolver.getCurrentAccount()).isEmpty();
        assertThatThrownBy(resolver::requireCurrentUserId)
                .isInstanceOf(IllegalStateException.class);
    }
}
