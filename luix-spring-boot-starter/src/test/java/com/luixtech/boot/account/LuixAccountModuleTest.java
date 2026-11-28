package com.luixtech.boot.account;

import com.luixtech.boot.oidc.OidcUserClaims;
import com.luixtech.boot.oidc.PassportOidcUserService;
import com.luixtech.boot.user.AdminUserGuardException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LuixAccountModuleTest {

    private JdbcClient jdbcClient;
    private LuixAppUserRepository repository;
    private LuixAccountProperties properties;
    private LuixAccountService service;

    @BeforeEach
    void setUp() {
        String dbName = "luix_account_" + UUID.randomUUID().toString().replace("-", "");
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                true);
        jdbcClient = JdbcClient.create(dataSource);
        jdbcClient.sql("""
                CREATE TABLE app_user (
                    id VARCHAR(36) PRIMARY KEY,
                    oidc_sub VARCHAR(128) NOT NULL UNIQUE,
                    email VARCHAR(255),
                    display_name VARCHAR(128),
                    picture_url TEXT,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    time_zone_id VARCHAR(64),
                    time_format_id VARCHAR(16) DEFAULT '24H',
                    last_login_at TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """).update();
        jdbcClient.sql("""
                CREATE TABLE app_user_role (
                    user_id VARCHAR(36) NOT NULL,
                    role VARCHAR(64) NOT NULL,
                    PRIMARY KEY (user_id, role),
                    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
                )
                """).update();

        repository = new LuixAppUserRepository(jdbcClient);
        properties = new LuixAccountProperties();
        properties.setAvailableRoles(List.of("ROLE_ADMIN", "ROLE_USER", "ROLE_VIEWER"));
        properties.setDefaultRole("ROLE_VIEWER");
        service = new LuixAccountService(repository, properties);
    }

    @Test
    void firstUserBootstrapsAsAdminAndSecondGetsDefaultRole() {
        Instant now = Instant.now();
        LuixAppUser admin = service.save(service.createNewAccount(
                new OidcUserClaims("sub-admin", "admin@example.com", "Admin User", "https://img/a.png"),
                true,
                now));
        LuixAppUser viewer = service.save(service.createNewAccount(
                new OidcUserClaims("sub-viewer", "viewer@example.com", "Viewer User", null),
                false,
                now));

        assertThat(admin.getId()).isNotBlank();
        assertThat(admin.getAuthorities()).containsExactly("ROLE_ADMIN");
        assertThat(viewer.getAuthorities()).containsExactly("ROLE_VIEWER");
        assertThat(repository.count()).isEqualTo(2L);
        assertThat(repository.countEnabledByRole("ROLE_ADMIN")).isEqualTo(1L);

        List<LuixAppUser> all = service.listUsers();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getEmail()).isEqualTo("admin@example.com");
        assertThat(all.get(1).getAuthorities()).containsExactly("ROLE_VIEWER");
    }

    @Test
    void updatesPreferencesAndNormalizesTimezoneAndTimeFormat() {
        service.save(service.createNewAccount(
                new OidcUserClaims("sub-1", "u1@example.com", "User One", null),
                true,
                Instant.now()));

        Authentication auth = oidcAuth("sub-1", "u1@example.com");
        LuixAccountService.UserPreferences saved = service.updatePreferences(auth, "Asia/Shanghai", "12h");
        assertThat(saved.timeZoneId()).isEqualTo("Asia/Shanghai");
        assertThat(saved.timeFormatId()).isEqualTo("12H");

        LuixAccountService.UserPreferences resetToAuto = service.updatePreferences(auth, "  ", "INVALID");
        assertThat(resetToAuto.timeZoneId()).isNull();
        assertThat(resetToAuto.timeFormatId()).isEqualTo("24H");

        assertThatThrownBy(() -> service.updatePreferences(auth, "Mars/Olympus", "24H"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mars/Olympus");
    }

    @Test
    void enforcesAdminGuardsAndRoleValidation() {
        LuixAppUser admin = service.save(service.createNewAccount(
                new OidcUserClaims("sub-admin", "admin@example.com", "Admin", null),
                true,
                Instant.now()));
        LuixAppUser viewer = service.save(service.createNewAccount(
                new OidcUserClaims("sub-viewer", "viewer@example.com", "Viewer", null),
                false,
                Instant.now()));

        Authentication adminAuth = oidcAuth("sub-admin", "admin@example.com");

        // Self-lockout protections
        assertThatThrownBy(() -> service.updateUser(adminAuth, admin.getId(), Set.of("ROLE_VIEWER"), null))
                .isInstanceOf(AdminUserGuardException.class);
        assertThatThrownBy(() -> service.updateUser(adminAuth, admin.getId(), null, false))
                .isInstanceOf(AdminUserGuardException.class);
        assertThatThrownBy(() -> service.deleteUser(adminAuth, admin.getId()))
                .isInstanceOf(AdminUserGuardException.class);

        // Unsupported role rejected
        assertThatThrownBy(() -> service.updateUser(adminAuth, viewer.getId(), Set.of("ROLE_SUPER_HACKER"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported role");

        // Valid update by email & delete by ID
        LuixAppUser promoted = service.updateUser(adminAuth, "VIEWER@example.com", Set.of("ROLE_ADMIN", "ROLE_USER"), false);
        assertThat(promoted.getAuthorities()).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        assertThat(promoted.isEnabled()).isFalse();

        service.deleteUser(adminAuth, viewer.getId());
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    void accountControllerSupportsCustomizerAndExcludesAvatarUpload() {
        service.save(service.createNewAccount(
                new OidcUserClaims("sub-1", "u1@example.com", "Alice", "https://img/alice.png"),
                true,
                Instant.now()));

        LuixAccountResponseCustomizer customizer = (user, extra) -> extra.put("customFlag", "enabled-for-" + user.getOidcSub());
        LuixAccountController controller = new LuixAccountController(service, properties, List.of(customizer));

        Map<String, Object> body = controller.getCurrentUser(new MockHttpServletRequest(), oidcAuth("sub-1", "u1@example.com"));
        assertThat(body)
                .containsEntry("email", "u1@example.com")
                .containsEntry("name", "Alice")
                .containsEntry("picture", "https://img/alice.png")
                .containsEntry("timeFormatId", "24H")
                .containsEntry("isAuthenticated", true)
                .containsEntry("isAdmin", true)
                .containsEntry("customFlag", "enabled-for-sub-1");

        assertThat(controller.findSupportedTimezones().getBody()).isNotEmpty();
    }

    @Test
    void userManagementSupportsAvailableRolesAndBatchOperations() {
        LuixAppUser admin = service.save(service.createNewAccount(
                new OidcUserClaims("sub-admin", "admin@example.com", "Admin", null),
                true,
                Instant.now()));
        LuixAppUser u1 = service.save(service.createNewAccount(
                new OidcUserClaims("sub-u1", "u1@example.com", "User One", null),
                false,
                Instant.now()));
        LuixAppUser u2 = service.save(service.createNewAccount(
                new OidcUserClaims("sub-u2", "u2@example.com", "User Two", null),
                false,
                Instant.now()));

        LuixUserManagementController controller = new LuixUserManagementController(service);
        LuixUserManagementController.AvailableRolesResponse available = controller.availableRoles();
        assertThat(available.singleRole()).isFalse();
        assertThat(available.roles()).containsExactly(
                new LuixUserManagementController.RoleOption("ROLE_ADMIN", "ADMIN", "Admin"),
                new LuixUserManagementController.RoleOption("ROLE_USER", "USER", "User"),
                new LuixUserManagementController.RoleOption("ROLE_VIEWER", "VIEWER", "Viewer")
        );

        Authentication adminAuth = oidcAuth("sub-admin", "admin@example.com");

        // Self-disable in batch must be rejected
        assertThatThrownBy(() -> controller.batchUpdateStatus(
                new LuixUserManagementController.BatchStatusRequest(List.of(admin.getId(), u1.getId()), false),
                adminAuth))
                .isInstanceOf(AdminUserGuardException.class);

        // Batch disable u1 & u2
        LuixUserManagementController.BatchOperationResult disabled = controller.batchUpdateStatus(
                new LuixUserManagementController.BatchStatusRequest(List.of(u1.getId(), u2.getId()), false),
                adminAuth);
        assertThat(disabled.affectedCount()).isEqualTo(2);
        assertThat(repository.findById(u1.getId()).orElseThrow().isEnabled()).isFalse();
        assertThat(repository.findById(u2.getId()).orElseThrow().isEnabled()).isFalse();

        // Batch delete u1 & u2
        LuixUserManagementController.BatchOperationResult deleted = controller.batchDelete(
                new LuixUserManagementController.BatchDeleteRequest(List.of(u1.getId(), u2.getId())),
                adminAuth);
        assertThat(deleted.affectedCount()).isEqualTo(2);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    void autoConfigurationActivatesOnlyWhenExplicitlyEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LuixAccountAutoConfiguration.class))
                .withBean(JdbcClient.class, () -> jdbcClient);

        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(LuixAppUserRepository.class);
            assertThat(ctx).doesNotHaveBean(LuixAccountService.class);
        });

        runner.withPropertyValues(
                "luix.account.enabled=true",
                "luix.account.default-role=ROLE_VIEWER",
                "luix.account.available-roles=ROLE_ADMIN,ROLE_VIEWER"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(LuixAppUserRepository.class);
            assertThat(ctx).hasSingleBean(LuixAccountService.class);
            assertThat(ctx).hasSingleBean(PassportOidcUserService.class);
            assertThat(ctx).hasSingleBean(LuixAccountController.class);
            assertThat(ctx).hasSingleBean(LuixUserManagementController.class);
        });
    }

    private Authentication oidcAuth(String sub, String email) {
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", sub, "email", email));
        OidcUser principal = new DefaultOidcUser(List.of(), token, "sub");
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(principal);
        return auth;
    }
}
