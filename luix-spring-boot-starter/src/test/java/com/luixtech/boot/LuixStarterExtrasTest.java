package com.luixtech.boot;

import com.luixtech.boot.crypto.LuixCryptoAutoConfiguration;
import com.luixtech.boot.web.LuixRestExceptionHandler;
import com.luixtech.boot.web.LuixWebAutoConfiguration;
import com.luixtech.boot.web.spa.LuixSpaAutoConfiguration;
import com.luixtech.boot.web.spa.LuixSpaFallback;
import com.luixtech.boot.web.spa.LuixSpaProperties;
import com.luixtech.core.crypto.AesGcmCrypto;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class LuixStarterExtrasTest {

    @Test
    void spaFallbackRulesExcludeApiAssetsAndFileExtensions() {
        assertThat(LuixSpaFallback.isSpaFallbackPath("", LuixSpaProperties.DEFAULT_EXCLUDED_PREFIXES)).isTrue();
        assertThat(LuixSpaFallback.isSpaFallbackPath("jobs", LuixSpaProperties.DEFAULT_EXCLUDED_PREFIXES)).isTrue();
        assertThat(LuixSpaFallback.isSpaFallbackPath("account/appearance", LuixSpaProperties.DEFAULT_EXCLUDED_PREFIXES)).isTrue();

        assertThat(LuixSpaFallback.isSpaFallbackPath("api/jobs", LuixSpaProperties.DEFAULT_EXCLUDED_PREFIXES)).isFalse();
        assertThat(LuixSpaFallback.isSpaFallbackPath("open-api/accounts/user", LuixSpaProperties.DEFAULT_EXCLUDED_PREFIXES)).isFalse();
        assertThat(LuixSpaFallback.isSpaFallbackPath("oauth2/authorization/passport", LuixSpaProperties.DEFAULT_EXCLUDED_PREFIXES)).isFalse();
        assertThat(LuixSpaFallback.isSpaFallbackPath("logout", LuixSpaProperties.DEFAULT_EXCLUDED_PREFIXES)).isFalse();
        assertThat(LuixSpaFallback.isSpaFallbackPath("management/health", LuixSpaProperties.DEFAULT_EXCLUDED_PREFIXES)).isFalse();
        assertThat(LuixSpaFallback.isSpaFallbackPath("assets/app.js", LuixSpaProperties.DEFAULT_EXCLUDED_PREFIXES)).isFalse();
        assertThat(LuixSpaFallback.isSpaFallbackPath("favicon.ico", LuixSpaProperties.DEFAULT_EXCLUDED_PREFIXES)).isFalse();
    }

    @Test
    void spaAutoConfigurationIsOptIn() {
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LuixSpaAutoConfiguration.class));

        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(WebMvcConfigurer.class));

        runner.withPropertyValues("luix.spa.enabled=true")
                .run(ctx -> assertThat(ctx).hasBean("luixSpaWebMvcConfigurer"));
    }

    @Test
    void webExceptionHandlerAutoConfiguresDefaultAndRespectsCustomBean() {
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LuixWebAutoConfiguration.class));

        runner.run(ctx -> assertThat(ctx).hasSingleBean(LuixRestExceptionHandler.class));

        runner.withPropertyValues("luix.web.exception-handler.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(LuixRestExceptionHandler.class));

        runner.withBean("customHandler", LuixRestExceptionHandler.class, () -> new LuixRestExceptionHandler() {})
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(LuixRestExceptionHandler.class);
                    assertThat(ctx).hasBean("customHandler");
                });
    }

    @Test
    void cryptoAutoConfigurationSupportsStandardAndUrlSafeEncodings() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LuixCryptoAutoConfiguration.class));

        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(AesGcmCrypto.class));

        runner.withPropertyValues("luix.crypto.encryption-key=MySecretKey2026!")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AesGcmCrypto.class);
                    AesGcmCrypto crypto = ctx.getBean(AesGcmCrypto.class);
                    String cipher = crypto.encrypt("hello-standard");
                    assertThat(crypto.decrypt(cipher)).isEqualTo("hello-standard");
                });

        runner.withPropertyValues(
                "luix.crypto.encryption-key=MySecretKey2026!",
                "luix.crypto.encoding=URL_SAFE"
        ).run(ctx -> {
            assertThat(ctx).hasSingleBean(AesGcmCrypto.class);
            AesGcmCrypto crypto = ctx.getBean(AesGcmCrypto.class);
            String cipher = crypto.encrypt("hello-url-safe");
            assertThat(cipher).doesNotContain("+").doesNotContain("/");
            assertThat(crypto.decryptOrRaw(cipher)).isEqualTo("hello-url-safe");
        });
    }
}
