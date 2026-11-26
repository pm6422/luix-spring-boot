package com.luixtech.boot.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for default {@code RestClient.Builder} HTTP timeouts.
 */
@ConfigurationProperties(prefix = "luix.http")
public class LuixHttpProperties {

    /** Whether to auto-configure a prototype {@code RestClient.Builder} with bounded timeouts. */
    private boolean enabled = true;

    /** TCP connection timeout for outbound HTTP requests. */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** Socket read timeout for outbound HTTP requests. */
    private Duration readTimeout = Duration.ofSeconds(20);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
