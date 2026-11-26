package com.luixtech.boot.oidc;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Normalized user identity claims extracted from an OIDC / OAuth2 principal (such as Logto / Luixtech Passport).
 *
 * @param subject     stable OIDC {@code sub} identifier (never null or blank)
 * @param email       user email address, or {@code null} if absent
 * @param displayName resolved display name (falls back from {@code name} -> {@code preferred_username}/{@code username} -> email local-part -> {@code subject})
 * @param picture     avatar URL, or {@code null} if absent
 */
public record OidcUserClaims(String subject, String email, String displayName, String picture) {

    /**
     * Extracts normalized claims from an {@link OidcUser}.
     */
    public static OidcUserClaims from(OidcUser oidcUser) {
        String sub = firstNonBlank(oidcUser.getSubject(), oidcUser.getName());
        if (sub == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("missing_subject"), "OIDC user has no subject claim");
        }
        String email = trimToNull(oidcUser.getEmail());
        String displayName = firstNonBlank(
                oidcUser.getFullName(),
                oidcUser.getPreferredUsername(),
                oidcUser.getAttribute("username"),
                emailLocalPart(email),
                sub);
        String picture = trimToNull(oidcUser.getPicture());
        return new OidcUserClaims(sub, email, displayName, picture);
    }

    /**
     * Extracts normalized claims from a generic {@link OAuth2User}.
     */
    public static OidcUserClaims from(OAuth2User oauth2User) {
        if (oauth2User instanceof OidcUser oidcUser) {
            return from(oidcUser);
        }
        String sub = firstNonBlank(oauth2User.getAttribute("sub"), oauth2User.getName());
        if (sub == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("missing_subject"), "OAuth2 user has no subject claim");
        }
        String email = trimToNull(oauth2User.getAttribute("email"));
        String displayName = firstNonBlank(
                oauth2User.getAttribute("name"),
                oauth2User.getAttribute("preferred_username"),
                oauth2User.getAttribute("username"),
                emailLocalPart(email),
                sub);
        String picture = trimToNull(oauth2User.getAttribute("picture"));
        return new OidcUserClaims(sub, email, displayName, picture);
    }

    /**
     * Returns {@link #email()} if present, or synthesizes {@code <sub>@<fallbackDomain>} when absent.
     */
    public String emailOrFallback(String fallbackDomain) {
        if (email != null) {
            return email;
        }
        return subject + "@" + fallbackDomain;
    }

    private static String emailLocalPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : null;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            String trimmed = trimToNull(candidate);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
