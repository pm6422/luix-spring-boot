package com.luixtech.boot;

import com.luixtech.boot.banner.StartupBannerAutoConfiguration;
import com.luixtech.boot.banner.StartupBannerLogger;
import com.luixtech.boot.http.RestClientTimeoutAutoConfiguration;
import com.luixtech.boot.oidc.PassportOidcAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LuixAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    StartupBannerAutoConfiguration.class,
                    RestClientTimeoutAutoConfiguration.class,
                    PassportOidcAutoConfiguration.class));

    @Test
    void registersAllAutoConfiguredBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(StartupBannerLogger.class);
            assertThat(context).hasSingleBean(RestClient.Builder.class);
            assertThat(context).hasSingleBean(JwtDecoderFactory.class);
            assertThat(context).hasBean("jwtTypeValidator");

            // RestClient.Builder must be prototype-scoped
            RestClient.Builder b1 = context.getBean(RestClient.Builder.class);
            RestClient.Builder b2 = context.getBean(RestClient.Builder.class);
            assertThat(b1).isNotSameAs(b2);

            // jwtTypeValidator accepts at+jwt, JWT, and absent typ
            @SuppressWarnings("unchecked")
            OAuth2TokenValidator<Jwt> validator = (OAuth2TokenValidator<Jwt>) context.getBean("jwtTypeValidator");
            Jwt atJwt = new Jwt("tok", Instant.now(), Instant.now().plusSeconds(60),
                    Map.of("alg", "ES384", "typ", "at+jwt"), Map.of("sub", "u1"));
            assertThat(validator.validate(atJwt).hasErrors()).isFalse();
        });
    }

    @Test
    void respectsDisableProperties() {
        contextRunner
                .withPropertyValues(
                        "luix.banner.enabled=false",
                        "luix.http.enabled=false",
                        "luix.oidc.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(StartupBannerLogger.class);
                    assertThat(context).doesNotHaveBean(RestClient.Builder.class);
                    assertThat(context).doesNotHaveBean(JwtDecoderFactory.class);
                    assertThat(context).doesNotHaveBean("jwtTypeValidator");
                });
    }
}
