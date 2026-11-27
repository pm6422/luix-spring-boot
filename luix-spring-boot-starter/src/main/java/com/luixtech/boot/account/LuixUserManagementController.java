package com.luixtech.boot.account;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
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
    public List<UserSummary> list() {
        return accountService.listUsers().stream()
                .map(UserSummary::from)
                .toList();
    }

    @PutMapping
    public UserSummary update(@RequestBody UpdateUserRequest request, Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String identifier = (request.id() != null && !request.id().isBlank()) ? request.id() : request.email();
        LuixAppUser updated = accountService.updateUser(
                authentication,
                identifier,
                request.roles(),
                request.enabled());
        return UserSummary.from(updated);
    }

    @DeleteMapping("/{identifier}")
    public ResponseEntity<Void> delete(@PathVariable String identifier, Authentication authentication) {
        accountService.deleteUser(authentication, identifier);
        return ResponseEntity.noContent().build();
    }

    public record UpdateUserRequest(
            String id,
            String email,
            Set<String> roles,
            Boolean enabled
    ) {
    }

    public record UserSummary(
            String id,
            String email,
            String name,
            String picture,
            List<String> roles,
            boolean enabled,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static UserSummary from(LuixAppUser user) {
            String displayName = (user.getDisplayName() != null && !user.getDisplayName().isBlank())
                    ? user.getDisplayName()
                    : user.getEmail();
            return new UserSummary(
                    user.getId(),
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
