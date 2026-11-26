package com.luixtech.boot.oidc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Logto / Luixtech Passport OIDC auto-configuration.
 */
@ConfigurationProperties(prefix = "luix.oidc")
public class LuixOidcProperties {

    /** Whether Passport OIDC helper beans should be auto-configured. */
    private boolean enabled = true;

    /** JWS signature algorithm expected on OIDC ID tokens (default {@code ES384} for Logto). */
    private String idTokenJwsAlgorithm = "ES384";

    /** Whether to auto-configure a {@code JwtTypeValidator} accepting {@code at+jwt} and {@code JWT}. */
    private boolean allowAtJwtType = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIdTokenJwsAlgorithm() {
        return idTokenJwsAlgorithm;
    }

    public void setIdTokenJwsAlgorithm(String idTokenJwsAlgorithm) {
        this.idTokenJwsAlgorithm = idTokenJwsAlgorithm;
    }

    public boolean isAllowAtJwtType() {
        return allowAtJwtType;
    }

    public void setAllowAtJwtType(boolean allowAtJwtType) {
        this.allowAtJwtType = allowAtJwtType;
    }
}
