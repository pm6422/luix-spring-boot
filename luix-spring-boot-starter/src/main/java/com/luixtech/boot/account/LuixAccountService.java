package com.luixtech.boot.account;

import com.luixtech.boot.oidc.CurrentOidcAccountResolver;
import com.luixtech.boot.oidc.OidcAccountStore;
import com.luixtech.boot.oidc.OidcUserClaims;
import com.luixtech.boot.oidc.OidcUserSessionSyncFilter;
import com.luixtech.boot.oidc.PassportOidcSupport;
import com.luixtech.boot.oidc.PassportOidcUserService;
import com.luixtech.boot.user.AdminUserGuard;
import com.luixtech.core.time.TimeZoneCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Unified account service implementing {@link OidcAccountStore} for {@link LuixAppUser},
 * handling OIDC synchronization, current user resolution, display preferences (timezone &amp; time format),
 * and guarded administrative user management.
 */
public class LuixAccountService implements OidcAccountStore<LuixAppUser> {

    public static final String DEFAULT_TIME_FORMAT = "24H";
    private static final Set<String> SUPPORTED_TIME_FORMATS = Set.of("24H", "12H", "ISO");

    private final LuixAppUserRepository repository;
    private final LuixAccountProperties properties;
    private final CurrentOidcAccountResolver<LuixAppUser> accountResolver;

    public LuixAccountService(LuixAppUserRepository repository, LuixAccountProperties properties) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.accountResolver = new CurrentOidcAccountResolver<>(this, LuixAppUser.class);
    }

    // =========================================================================
    // OidcAccountStore SPI
    // =========================================================================

    @Override
    public Optional<LuixAppUser> findBySubject(String subject) {
        return repository.findByOidcSub(subject);
    }

    @Override
    public Optional<LuixAppUser> findByEmailIgnoreCase(String email) {
        return repository.findByEmailIgnoreCase(email);
    }

    @Override
    public long countUsers() {
        return repository.count();
    }

    @Override
    public LuixAppUser createNewAccount(OidcUserClaims claims, boolean firstUser, Instant loginAt) {
        LuixAppUser user = new LuixAppUser();
        user.setOidcSub(claims.subject());
        user.setEmail(claims.email());
        user.setDisplayName(claims.displayName());
        user.setPictureUrl(claims.picture());
        user.setEnabled(true);
        user.setTimeFormatId(DEFAULT_TIME_FORMAT);
        user.setLastLoginAt(loginAt);

        Set<String> initialRoles = new LinkedHashSet<>();
        if (firstUser) {
            initialRoles.add(properties.getAdminRole());
        } else {
            initialRoles.add(properties.getDefaultRole());
        }
        user.setRoles(initialRoles);
        return user;
    }

    @Override
    public void updateExistingAccount(LuixAppUser account, OidcUserClaims claims, Instant loginAt) {
        if (claims.subject() != null && !claims.subject().isBlank()) {
            account.setOidcSub(claims.subject());
        }
        if (claims.email() != null && !claims.email().isBlank()) {
            account.setEmail(claims.email());
        }
        if (claims.displayName() != null && !claims.displayName().isBlank()) {
            account.setDisplayName(claims.displayName());
        }
        if (claims.picture() != null && !claims.picture().isBlank()) {
            account.setPictureUrl(claims.picture());
        }
        account.setLastLoginAt(loginAt);
    }

    @Override
    @Transactional
    public LuixAppUser save(LuixAppUser account) {
        return repository.save(account);
    }

    /**
     * Creates a non-bean {@link OidcUserSessionSyncFilter} ready to be plugged directly into
     * {@code HttpSecurity#addFilterBefore(..., AuthorizationFilter.class)}.
     */
    public OidcUserSessionSyncFilter<LuixAppUser> createSessionSyncFilter(
            PassportOidcUserService<LuixAppUser> oidcUserService) {
        return new OidcUserSessionSyncFilter<>(
                this,
                oidcUserService,
                OidcUserSessionSyncFilter.hybridDisabledHandler(
                        properties.getApiPathPrefix(),
                        properties.getDisabledRedirectUrl()));
    }

    // =========================================================================
    // Current User & Display Preferences
    // =========================================================================

    public Optional<LuixAppUser> getCurrentUser() {
        return accountResolver.getCurrentAccount();
    }

    public Optional<LuixAppUser> resolveCurrentUser(HttpServletRequest request, Authentication authentication) {
        return accountResolver.getCurrentAccount(request, authentication);
    }

    public Optional<String> getCurrentUserId() {
        return accountResolver.getCurrentUserId();
    }

    public String requireCurrentUserId() {
        return accountResolver.requireCurrentUserId();
    }

    public ZoneId resolveEffectiveZone(HttpServletRequest request, Authentication authentication) {
        Optional<LuixAppUser> userOpt = resolveCurrentUser(request, authentication);
        if (userOpt.isPresent()) {
            Optional<ZoneId> zone = TimeZoneCatalog.parseValidZoneId(userOpt.get().getTimeZoneId());
            if (zone.isPresent()) {
                return zone.get();
            }
        }
        return ZoneId.systemDefault();
    }

    @Transactional
    public UserPreferences updatePreferences(Authentication authentication,
                                             String timeZoneId,
                                             String timeFormatId) {
        String sub = PassportOidcSupport.resolveSubject(authentication);
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("Authenticated user not found");
        }
        String normalizedTz = normalizeTimezoneInput(timeZoneId);
        String normalizedFormat = normalizeTimeFormat(timeFormatId);

        LuixAppUser user = repository.findByOidcSub(sub)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + sub));
        user.setTimeZoneId(normalizedTz);
        user.setTimeFormatId(normalizedFormat);
        repository.save(user);
        return new UserPreferences(normalizedTz, normalizedFormat);
    }

    // =========================================================================
    // Administrative User Management
    // =========================================================================

    public List<LuixAppUser> listUsers() {
        return repository.findAllOrderByCreatedAtAsc();
    }

    public List<LuixAppUser> listUsersByRole(String role) {
        return repository.findAllByRole(role);
    }

    public List<String> getAvailableRoles() {
        return List.copyOf(properties.getAvailableRoles());
    }

    public boolean isSingleRole() {
        return properties.isSingleRole();
    }

    public Optional<LuixAppUser> findByIdOrEmail(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        Optional<LuixAppUser> byId = repository.findById(trimmed);
        return byId.isPresent() ? byId : repository.findByEmailIgnoreCase(trimmed);
    }

    @Transactional
    public LuixAppUser updateUser(Authentication actor,
                                  String identifier,
                                  Collection<String> targetRoles,
                                  Boolean targetEnabled) {
        LuixAppUser target = findByIdOrEmail(identifier)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + identifier));
        String actingSubject = PassportOidcSupport.resolveSubject(actor);
        String adminRole = properties.getAdminRole();

        Set<String> validatedRoles = null;
        if (targetRoles != null) {
            validatedRoles = validateAndNormalizeRoles(targetRoles);
            AdminUserGuard.assertCanUpdateAuthorities(
                    target,
                    validatedRoles,
                    actingSubject,
                    adminRole,
                    () -> repository.countEnabledByRole(adminRole));
        }

        if (targetEnabled != null) {
            AdminUserGuard.assertCanSetEnabled(
                    target,
                    targetEnabled,
                    actingSubject,
                    adminRole,
                    () -> repository.countEnabledByRole(adminRole));
        }

        if (validatedRoles != null) {
            target.setRoles(validatedRoles);
        }
        if (targetEnabled != null) {
            target.setEnabled(targetEnabled);
        }

        return repository.save(target);
    }

    @Transactional
    public void deleteUser(Authentication actor, String identifier) {
        LuixAppUser target = findByIdOrEmail(identifier)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + identifier));
        String actingSubject = PassportOidcSupport.resolveSubject(actor);
        String adminRole = properties.getAdminRole();

        AdminUserGuard.assertCanDelete(
                target,
                actingSubject,
                adminRole,
                () -> repository.countEnabledByRole(adminRole));
        repository.deleteById(target.getId());
    }

    @Transactional
    public int batchUpdateEnabled(Authentication actor, Collection<String> identifiers, boolean enabled) {
        Set<String> uniqueIds = normalizeBatchIdentifiers(identifiers);
        String actingSubject = PassportOidcSupport.resolveSubject(actor);
        String adminRole = properties.getAdminRole();
        int updated = 0;

        for (String identifier : uniqueIds) {
            LuixAppUser target = findByIdOrEmail(identifier)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + identifier));
            AdminUserGuard.assertCanSetEnabled(
                    target,
                    enabled,
                    actingSubject,
                    adminRole,
                    () -> repository.countEnabledByRole(adminRole));
            if (target.isEnabled() != enabled) {
                target.setEnabled(enabled);
                repository.save(target);
            }
            updated++;
        }
        return updated;
    }

    @Transactional
    public int batchDeleteUsers(Authentication actor, Collection<String> identifiers) {
        Set<String> uniqueIds = normalizeBatchIdentifiers(identifiers);
        String actingSubject = PassportOidcSupport.resolveSubject(actor);
        String adminRole = properties.getAdminRole();
        int deleted = 0;

        for (String identifier : uniqueIds) {
            LuixAppUser target = findByIdOrEmail(identifier)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + identifier));
            AdminUserGuard.assertCanDelete(
                    target,
                    actingSubject,
                    adminRole,
                    () -> repository.countEnabledByRole(adminRole));
            repository.deleteById(target.getId());
            deleted++;
        }
        return deleted;
    }

    private static Set<String> normalizeBatchIdentifiers(Collection<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            throw new IllegalArgumentException("At least one user identifier is required");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : identifiers) {
            if (raw != null && !raw.isBlank()) {
                unique.add(raw.trim());
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("At least one user identifier is required");
        }
        return unique;
    }

    // =========================================================================
    // Normalization Helpers
    // =========================================================================

    public static String normalizeTimeFormat(String timeFormatId) {
        if (timeFormatId == null || timeFormatId.isBlank()) {
            return DEFAULT_TIME_FORMAT;
        }
        String upper = timeFormatId.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_TIME_FORMATS.contains(upper) ? upper : DEFAULT_TIME_FORMAT;
    }

    private String normalizeTimezoneInput(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return null;
        }
        String trimmed = timezone.trim();
        Optional<ZoneId> zone = TimeZoneCatalog.parseValidZoneId(trimmed);
        if (zone.isEmpty()) {
            throw new IllegalArgumentException("Unsupported timezone: " + trimmed);
        }
        return zone.get().getId();
    }

    private Set<String> validateAndNormalizeRoles(Collection<String> requestedRoles) {
        Set<String> allowed = new LinkedHashSet<>(properties.getAvailableRoles());
        Set<String> normalized = new LinkedHashSet<>();
        for (String r : requestedRoles) {
            if (r == null || r.isBlank()) {
                continue;
            }
            String trimmed = r.trim();
            if (!allowed.isEmpty() && !allowed.contains(trimmed)) {
                throw new IllegalArgumentException("Unsupported role: " + trimmed);
            }
            normalized.add(trimmed);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one valid role is required");
        }
        if (properties.isSingleRole() && normalized.size() > 1) {
            throw new IllegalArgumentException("Only a single role is permitted per user");
        }
        return normalized;
    }

    public record UserPreferences(String timeZoneId, String timeFormatId) {
    }
}
