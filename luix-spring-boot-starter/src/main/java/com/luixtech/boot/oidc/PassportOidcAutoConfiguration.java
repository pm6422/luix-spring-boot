package com.luixtech.boot.oidc;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;

/**
 * Auto-configuration for Logto / Luixtech Passport OIDC ID token decoding and RFC 9068 {@code at+jwt} validation.
 *
 * <p>All beans are guarded by {@link ConditionalOnClass} and {@link ConditionalOnMissingBean},
 * so applications only activate what is on their classpath and can freely override any bean.
 */
@AutoConfiguration
@EnableConfigurationProperties(LuixOidcProperties.class)
@ConditionalOnProperty(prefix = "luix.oidc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PassportOidcAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({OidcIdTokenDecoderFactory.class, ClientRegistration.class, SignatureAlgorithm.class})
    static class OidcClientDecoderConfiguration {

        @Bean
        @ConditionalOnMissingBean(JwtDecoderFactory.class)
        JwtDecoderFactory<ClientRegistration> luixIdTokenDecoderFactory(LuixOidcProperties properties) {
            SignatureAlgorithm algorithm = SignatureAlgorithm.from(properties.getIdTokenJwsAlgorithm());
            if (algorithm == null) {
                algorithm = SignatureAlgorithm.ES384;
            }
            return PassportOidcSupport.idTokenDecoderFactory(algorithm);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({JwtTypeValidator.class, Jwt.class})
    @ConditionalOnProperty(prefix = "luix.oidc", name = "allow-at-jwt-type", havingValue = "true", matchIfMissing = true)
    static class ResourceServerJwtTypeConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "jwtTypeValidator")
        OAuth2TokenValidator<Jwt> jwtTypeValidator() {
            return PassportOidcSupport.jwtTypeValidator();
        }
    }
}
