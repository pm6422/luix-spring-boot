package com.luixtech.boot.web.spa;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for serving an embedded Single Page Application (SPA) with client-side route fallback.
 */
@ConfigurationProperties(prefix = "luix.spa")
public class LuixSpaProperties {

    public static final List<String> DEFAULT_LOCATIONS = List.of("file:./static/", "classpath:/static/");

    public static final List<String> DEFAULT_EXCLUDED_PREFIXES = List.of(
            "api/", "open-api/", "oauth2/", "login/", "logout",
            "actuator/", "management/", "swagger-ui/", "v3/", "api-docs/",
            "assets/", "error"
    );

    /**
     * Enables the SPA static resource handler and {@code index.html} fallback resolver.
     */
    private boolean enabled = false;

    /**
     * Ordered resource locations searched for static assets and {@code index.html}.
     */
    private List<String> locations = new ArrayList<>(DEFAULT_LOCATIONS);

    /**
     * Path prefixes that must never fall back to {@code index.html}.
     */
    private List<String> excludedPrefixes = new ArrayList<>(DEFAULT_EXCLUDED_PREFIXES);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = (locations != null && !locations.isEmpty())
                ? new ArrayList<>(locations)
                : new ArrayList<>(DEFAULT_LOCATIONS);
    }

    public List<String> getExcludedPrefixes() {
        return excludedPrefixes;
    }

    public void setExcludedPrefixes(List<String> excludedPrefixes) {
        this.excludedPrefixes = (excludedPrefixes != null)
                ? new ArrayList<>(excludedPrefixes)
                : new ArrayList<>(DEFAULT_EXCLUDED_PREFIXES);
    }
}
