package com.luixtech.boot.account;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared administrative user management REST controller ({@code /api/users}).
 */
@RestController
@RequestMapping("/api/users")
public class LuixUserManagementController {

    private final LuixAccountService accountService;

    public LuixUserManagementController(LuixAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<UserSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String roleIds,
            @RequestParam(required = false) Boolean enabled) {
        String term = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;
        String rawRoleFilter = StringUtils.hasText(roleIds) ? roleIds : role;
        Set<String> filterRoles = parseRoleFilter(rawRoleFilter);

        return accountService.listUsers().stream()
                .filter(u -> term == null || matchesSearch(u, term))
                .filter(u -> filterRoles.isEmpty() || u.getAuthorities().stream().anyMatch(filterRoles::contains))
                .filter(u -> enabled == null || u.isEnabled() == enabled)
                .map(UserSummary::from)
                .toList();
    }

    @GetMapping("/{identifier}")
    public ResponseEntity<UserSummary> getByIdOrEmail(@PathVariable String identifier) {
        return accountService.findByIdOrEmail(identifier)
                .map(UserSummary::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    public UserSummary update(@RequestBody UpdateUserRequest request, Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String identifier = StringUtils.hasText(request.id()) ? request.id() : request.email();
        return updateByIdOrEmail(identifier, request, authentication);
    }

    @PutMapping("/{identifier}")
    public UserSummary updateByIdOrEmail(@PathVariable String identifier,
                                         @RequestBody UpdateUserRequest request,
                                         Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        Collection<String> targetRoles = resolveTargetRoles(request);
        LuixAppUser updated = accountService.updateUser(
                authentication,
                identifier,
                targetRoles,
                request.enabled());
        return UserSummary.from(updated);
    }

    @DeleteMapping("/{identifier}")
    public ResponseEntity<Void> delete(@PathVariable String identifier, Authentication authentication) {
        accountService.deleteUser(authentication, identifier);
        return ResponseEntity.noContent().build();
    }

    private static boolean matchesSearch(LuixAppUser user, String lowerTerm) {
        return (user.getEmail() != null && user.getEmail().toLowerCase(Locale.ROOT).contains(lowerTerm))
                || (user.getDisplayName() != null && user.getDisplayName().toLowerCase(Locale.ROOT).contains(lowerTerm));
    }

    private static Set<String> parseRoleFilter(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : StringUtils.tokenizeToStringArray(raw, ",")) {
            String normalized = normalizeRoleToken(token);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static Collection<String> resolveTargetRoles(UpdateUserRequest request) {
        if (request.roleIds() != null) {
            return normalizeRoles(request.roleIds());
        }
        if (request.roles() != null) {
            return normalizeRoles(request.roles());
        }
        if (StringUtils.hasText(request.role())) {
            String normalized = normalizeRoleToken(request.role());
            return normalized != null ? Set.of(normalized) : Set.of();
        }
        return null;
    }

    private static Set<String> normalizeRoles(Collection<String> rawRoles) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : rawRoles) {
            String role = normalizeRoleToken(raw);
            if (role != null) {
                normalized.add(role);
            }
        }
        return normalized;
    }

    private static String normalizeRoleToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }

    public record UpdateUserRequest(
            String id,
            String email,
            String role,
            Set<String> roleIds,
            Set<String> roles,
            Boolean enabled
    ) {
    }

    public record UserSummary(
            String id,
            String username,
            String email,
            String name,
            String picture,
            List<String> roleIds,
            boolean enabled,
            Instant lastSignInAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static UserSummary from(LuixAppUser user) {
            String displayName = (user.getDisplayName() != null && !user.getDisplayName().isBlank())
                    ? user.getDisplayName()
                    : user.getEmail();
            return new UserSummary(
                    user.getId(),
                    displayName,
                    user.getEmail(),
                    displayName,
                    user.getPictureUrl(),
                    List.copyOf(user.getAuthorities()),
                    user.isEnabled(),
                    user.getLastLoginAt(),
                    user.getCreatedAt(),
                    user.getUpdatedAt()
            );
        }
    }
}
