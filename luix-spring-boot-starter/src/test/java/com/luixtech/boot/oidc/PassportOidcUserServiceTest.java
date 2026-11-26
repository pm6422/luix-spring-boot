package com.luixtech.boot.oidc;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PassportOidcUserServiceTest {

    @Test
    void grantsAdminToFirstUserAndStandardRoleToSecondUser() {
        InMemoryAccountStore store = new InMemoryAccountStore();
        AtomicBoolean txWrapped = new AtomicBoolean(false);
        PassportOidcUserService<TestAccount> service = new StubPassportOidcUserService(
                store,
                action -> {
                    txWrapped.set(true);
                    return action.get();
                },
                oidcUser("sub-1", "admin@luixtech.com", "Admin"));

        OidcUser first = service.loadUser(null);
        assertThat(txWrapped).isTrue();
        assertThat(first.getAuthorities()).extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN", "ROLE_USER");

        PassportOidcUserService<TestAccount> secondService = new StubPassportOidcUserService(
                store,
                action -> action.get(),
                oidcUser("sub-2", "member@luixtech.com", "Member"));
        OidcUser second = secondService.loadUser(null);
        assertThat(second.getAuthorities()).extracting(a -> a.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    void matchesExistingUserByEmailFallbackAndUpdatesSubject() {
        InMemoryAccountStore store = new InMemoryAccountStore();
        store.save(new TestAccount("u-1", "old-sub", "alice@luixtech.com", "Old Name", true, List.of("ROLE_ADMIN")));

        PassportOidcUserService<TestAccount> service = new StubPassportOidcUserService(
                store,
                action -> action.get(),
                oidcUser("new-sub", "ALICE@luixtech.com", "Alice New"));

        OidcUser loaded = service.loadUser(null);
        assertThat(loaded.getAuthorities()).extracting(a -> a.getAuthority()).containsExactly("ROLE_ADMIN");
        assertThat(store.findBySubject("new-sub")).isPresent();
        assertThat(store.countUsers()).isEqualTo(1L);
    }

    @Test
    void blocksDisabledUserWithAccountDisabledError() {
        InMemoryAccountStore store = new InMemoryAccountStore();
        store.save(new TestAccount("u-1", "sub-disabled", "blocked@luixtech.com", "Blocked", false, List.of("ROLE_USER")));

        PassportOidcUserService<TestAccount> service = new StubPassportOidcUserService(
                store,
                action -> action.get(),
                oidcUser("sub-disabled", "blocked@luixtech.com", "Blocked"));

        assertThatThrownBy(() -> service.loadUser(null))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode())
                        .isEqualTo(PassportOidcUserService.ACCOUNT_DISABLED_ERROR));
    }

    static OidcUser oidcUser(String sub, String email, String name) {
        Instant now = Instant.now();
        Map<String, Object> claims = Map.of("sub", sub, "email", email, "name", name);
        OidcIdToken token = new OidcIdToken("token-val", now, now.plusSeconds(3600), claims);
        return new DefaultOidcUser(List.of(), token, "sub");
    }

    static class StubPassportOidcUserService extends PassportOidcUserService<TestAccount> {
        private final OidcUser stubUser;

        StubPassportOidcUserService(OidcAccountStore<TestAccount> store,
                                    Function<Supplier<TestAccount>, TestAccount> tx,
                                    OidcUser stubUser) {
            super(store, tx);
            this.stubUser = stubUser;
        }

        @Override
        protected OidcUser delegateLoadUser(OidcUserRequest userRequest) {
            return stubUser;
        }
    }

    static class TestAccount implements OidcAccount {
        final String id;
        String sub;
        String email;
        String name;
        boolean enabled;
        List<String> authorities;

        TestAccount(String id, String sub, String email, String name, boolean enabled, List<String> authorities) {
            this.id = id;
            this.sub = sub;
            this.email = email;
            this.name = name;
            this.enabled = enabled;
            this.authorities = new ArrayList<>(authorities);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getOidcSub() {
            return sub;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public Collection<String> getAuthorities() {
            return authorities;
        }
    }

    static class InMemoryAccountStore implements OidcAccountStore<TestAccount> {
        private final Map<String, TestAccount> byId = new LinkedHashMap<>();

        @Override
        public Optional<TestAccount> findBySubject(String subject) {
            return byId.values().stream().filter(a -> subject.equals(a.getOidcSub())).findFirst();
        }

        @Override
        public Optional<TestAccount> findByEmailIgnoreCase(String email) {
            return byId.values().stream()
                    .filter(a -> a.getEmail() != null && a.getEmail().equalsIgnoreCase(email))
                    .findFirst();
        }

        @Override
        public long countUsers() {
            return byId.size();
        }

        @Override
        public TestAccount createNewAccount(OidcUserClaims claims, boolean firstUser, Instant loginAt) {
            List<String> roles = firstUser ? List.of("ROLE_ADMIN", "ROLE_USER") : List.of("ROLE_USER");
            return new TestAccount("u-" + (byId.size() + 1), claims.subject(), claims.email(), claims.displayName(), true, roles);
        }

        @Override
        public void updateExistingAccount(TestAccount account, OidcUserClaims claims, Instant loginAt) {
            account.sub = claims.subject();
            account.email = claims.email();
            account.name = claims.displayName();
        }

        @Override
        public TestAccount save(TestAccount account) {
            byId.put(account.getId(), account);
            return account;
        }
    }
}
