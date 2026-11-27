package com.luixtech.boot.account;

import com.luixtech.core.time.TimeZoneCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared REST controller for current account lookup, display preference updates (timezone &amp; time format),
 * and supported timezone catalog.
 */
@RestController
public class LuixAccountController {

    private final LuixAccountService accountService;
    private final LuixAccountProperties properties;
    private final List<LuixAccountResponseCustomizer> responseCustomizers;

    public LuixAccountController(LuixAccountService accountService,
                                 LuixAccountProperties properties,
                                 List<LuixAccountResponseCustomizer> responseCustomizers) {
        this.accountService = accountService;
        this.properties = properties;
        this.responseCustomizers = responseCustomizers != null ? List.copyOf(responseCustomizers) : List.of();
    }

    @GetMapping("/open-api/accounts/user")
    public Map<String, Object> getCurrentUser(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Optional<LuixAppUser> userOpt = accountService.resolveCurrentUser(request, authentication);
        if (userOpt.isEmpty()) {
            return null;
        }
        LuixAppUser user = userOpt.get();
        List<String> roleIds = List.copyOf(user.getAuthorities());
        boolean isAdmin = user.hasAuthority(properties.getAdminRole());
        String displayName = (user.getDisplayName() != null && !user.getDisplayName().isBlank())
                ? user.getDisplayName()
                : user.getEmail();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", user.getId());
        payload.put("username", displayName);
        payload.put("email", user.getEmail());
        payload.put("name", displayName);
        payload.put("picture", user.getPictureUrl());
        payload.put("timeZoneId", (user.getTimeZoneId() != null && !user.getTimeZoneId().isBlank()) ? user.getTimeZoneId() : null);
        payload.put("timeFormatId", LuixAccountService.normalizeTimeFormat(user.getTimeFormatId()));
        payload.put("enabled", user.isEnabled());
        payload.put("roleIds", roleIds);
        payload.put("isAuthenticated", true);
        payload.put("isAdmin", isAdmin);

        for (LuixAccountResponseCustomizer customizer : responseCustomizers) {
            customizer.customize(user, payload);
        }
        return payload;
    }

    @PutMapping("/api/accounts/user")
    public ResponseEntity<LuixAccountService.UserPreferences> updateCurrentUser(
            @RequestBody AccountPreferencesRequest body,
            Authentication authentication) {
        LuixAccountService.UserPreferences saved = accountService.updatePreferences(
                authentication,
                body != null ? body.timeZoneId() : null,
                body != null ? body.timeFormatId() : null);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/api/accounts/all-supported-time-zones")
    public ResponseEntity<List<Map<String, String>>> findSupportedTimezones() {
        List<Map<String, String>> timezones = TimeZoneCatalog.listSupportedTimeZones().stream()
                .map(opt -> Map.of("dictCode", opt.id(), "dictName", opt.label()))
                .toList();
        return ResponseEntity.ok(timezones);
    }

    public record AccountPreferencesRequest(
            String timeZoneId,
            String timeFormatId
    ) {
    }
}
