package com.luixtech.boot.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the unified {@code app_user} / {@code app_user_role} account module.
 */
@ConfigurationProperties(prefix = "luix.account")
public class LuixAccountProperties {

    /**
     * Enables auto-configuration of {@link LuixAppUserRepository}, {@link LuixAccountService},
     * {@link LuixAccountController}, and {@link LuixUserManagementController}.
     */
    private boolean enabled = false;

    /**
     * Authority string treated as the administrator role (used for first-user bootstrap and admin guards).
     */
    private String adminRole = "ROLE_ADMIN";

    /**
     * Default authority assigned to newly provisioned non-admin users.
     */
    private String defaultRole = "ROLE_USER";

    /**
     * Allowed role authority strings that administrators may assign via user management endpoints.
     */
    private List<String> availableRoles = new ArrayList<>(List.of("ROLE_ADMIN", "ROLE_USER"));

    /**
     * When {@code true}, newly provisioned non-admin users are enabled immediately on first sign-in.
     * When {@code false}, only the first bootstrap administrator is enabled automatically while subsequent
     * new users start disabled until an administrator approves and enables them.
     */
    private boolean autoEnableNewUsers = false;

    /**
     * When {@code true}, each user is restricted to a single role (assigning a role replaces existing roles).
     */
    private boolean singleRole = false;

    /**
     * API path prefix that receives HTTP 403 JSON responses when a disabled user sends a request.
     */
    private String apiPathPrefix = "/api/";

    /**
     * Redirect target for browser requests when a user account has been disabled.
     */
    private String disabledRedirectUrl = "/?error=account_disabled";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdminRole() {
        return adminRole;
    }

    public void setAdminRole(String adminRole) {
        this.adminRole = adminRole;
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public void setDefaultRole(String defaultRole) {
        this.defaultRole = defaultRole;
    }

    public List<String> getAvailableRoles() {
        return availableRoles;
    }

    public void setAvailableRoles(List<String> availableRoles) {
        this.availableRoles = availableRoles != null ? new ArrayList<>(availableRoles) : new ArrayList<>();
    }

    public boolean isAutoEnableNewUsers() {
        return autoEnableNewUsers;
    }

    public void setAutoEnableNewUsers(boolean autoEnableNewUsers) {
        this.autoEnableNewUsers = autoEnableNewUsers;
    }

    public boolean isSingleRole() {
        return singleRole;
    }

    public void setSingleRole(boolean singleRole) {
        this.singleRole = singleRole;
    }

    public String getApiPathPrefix() {
        return apiPathPrefix;
    }

    public void setApiPathPrefix(String apiPathPrefix) {
        this.apiPathPrefix = apiPathPrefix;
    }

    public String getDisabledRedirectUrl() {
        return disabledRedirectUrl;
    }

    public void setDisabledRedirectUrl(String disabledRedirectUrl) {
        this.disabledRedirectUrl = disabledRedirectUrl;
    }
}
