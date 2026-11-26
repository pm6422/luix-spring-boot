package com.luixtech.boot.user;

import com.luixtech.boot.oidc.OidcAccount;
import com.luixtech.boot.user.AdminUserGuardException.Reason;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminUserGuardTest {

    @Test
    void rejectsEmptyOrBlankAuthorities() {
        OidcAccount target = account("u-2", "sub-2", true, List.of("ROLE_USER"));

        assertThatThrownBy(() -> AdminUserGuard.assertCanUpdateAuthorities(target, List.of(), "sub-1", () -> 2L))
                .isInstanceOf(AdminUserGuardException.class)
                .extracting(ex -> ((AdminUserGuardException) ex).getReason())
                .isEqualTo(Reason.INVALID_ROLE);
    }

    @Test
    void preventsSelfDemotionSelfDisableAndSelfDelete() {
        OidcAccount self = account("u-1", "sub-1", true, List.of("ROLE_ADMIN"));

        assertThatThrownBy(() -> AdminUserGuard.assertCanUpdateAuthorities(self, List.of("ROLE_USER"), "sub-1", () -> 2L))
                .isInstanceOf(AdminUserGuardException.class)
                .extracting(ex -> ((AdminUserGuardException) ex).getReason())
                .isEqualTo(Reason.CANNOT_DEMOTE_SELF);

        assertThatThrownBy(() -> AdminUserGuard.assertCanSetEnabled(self, false, "sub-1", () -> 2L))
                .isInstanceOf(AdminUserGuardException.class)
                .extracting(ex -> ((AdminUserGuardException) ex).getReason())
                .isEqualTo(Reason.CANNOT_DISABLE_SELF);

        assertThatThrownBy(() -> AdminUserGuard.assertCanDelete(self, "sub-1", () -> 2L))
                .isInstanceOf(AdminUserGuardException.class)
                .extracting(ex -> ((AdminUserGuardException) ex).getReason())
                .isEqualTo(Reason.CANNOT_DELETE_SELF);
    }

    @Test
    void preventsRemovingLastActiveAdminAndAllowsWhenAnotherAdminRemains() {
        OidcAccount otherAdmin = account("u-2", "sub-2", true, List.of("ROLE_ADMIN", "ROLE_USER"));

        assertThatThrownBy(() -> AdminUserGuard.assertCanUpdateAuthorities(otherAdmin, List.of("ROLE_USER"), "sub-1", () -> 1L))
                .isInstanceOf(AdminUserGuardException.class)
                .extracting(ex -> ((AdminUserGuardException) ex).getReason())
                .isEqualTo(Reason.LAST_ACTIVE_ADMIN);

        assertThatThrownBy(() -> AdminUserGuard.assertCanSetEnabled(otherAdmin, false, "sub-1", () -> 1L))
                .isInstanceOf(AdminUserGuardException.class)
                .extracting(ex -> ((AdminUserGuardException) ex).getReason())
                .isEqualTo(Reason.LAST_ACTIVE_ADMIN);

        assertThatThrownBy(() -> AdminUserGuard.assertCanDelete(otherAdmin, "sub-1", () -> 1L))
                .isInstanceOf(AdminUserGuardException.class)
                .extracting(ex -> ((AdminUserGuardException) ex).getReason())
                .isEqualTo(Reason.LAST_ACTIVE_ADMIN);

        assertThatCode(() -> AdminUserGuard.assertCanUpdateAuthorities(otherAdmin, List.of("ROLE_USER"), "sub-1", () -> 2L))
                .doesNotThrowAnyException();
        assertThatCode(() -> AdminUserGuard.assertCanSetEnabled(otherAdmin, false, "sub-1", () -> 2L))
                .doesNotThrowAnyException();
        assertThatCode(() -> AdminUserGuard.assertCanDelete(otherAdmin, "sub-1", () -> 2L))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsAdminCountQueryForNonAdminOrReEnableOperations() {
        OidcAccount regularUser = account("u-3", "sub-3", true, List.of("ROLE_USER"));
        OidcAccount disabledAdmin = account("u-4", "sub-4", false, List.of("ROLE_ADMIN"));
        AtomicBoolean countCalled = new AtomicBoolean(false);

        assertThatCode(() -> AdminUserGuard.assertCanSetEnabled(regularUser, false, "sub-1", () -> {
            countCalled.set(true);
            return 0L;
        })).doesNotThrowAnyException();

        assertThatCode(() -> AdminUserGuard.assertCanSetEnabled(disabledAdmin, true, "sub-1", () -> {
            countCalled.set(true);
            return 0L;
        })).doesNotThrowAnyException();

        assertThatCode(() -> AdminUserGuard.assertCanDelete(disabledAdmin, "sub-1", () -> {
            countCalled.set(true);
            return 0L;
        })).doesNotThrowAnyException();

        assertThat(countCalled).isFalse();
    }

    private static OidcAccount account(String id, String sub, boolean enabled, List<String> authorities) {
        return new OidcAccount() {
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
                return sub + "@luixtech.com";
            }

            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public Collection<String> getAuthorities() {
                return authorities;
            }
        };
    }
}
