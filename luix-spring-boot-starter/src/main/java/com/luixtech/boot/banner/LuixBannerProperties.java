package com.luixtech.boot.banner;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the application startup summary banner logger.
 */
@ConfigurationProperties(prefix = "luix.banner")
public class LuixBannerProperties {

    /** Whether to log the startup runtime summary banner on {@code ApplicationReadyEvent}. */
    private boolean enabled = true;

    /** Classpath location of an optional custom banner template. */
    private String templateLocation = "config/banner-app.txt";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTemplateLocation() {
        return templateLocation;
    }

    public void setTemplateLocation(String templateLocation) {
        this.templateLocation = templateLocation;
    }
}
