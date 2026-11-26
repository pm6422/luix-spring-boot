package com.luixtech.boot.banner;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration for {@link StartupBannerLogger}.
 */
@AutoConfiguration
@EnableConfigurationProperties(LuixBannerProperties.class)
@ConditionalOnProperty(prefix = "luix.banner", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StartupBannerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StartupBannerLogger startupBannerLogger(Environment env, LuixBannerProperties properties) {
        return new StartupBannerLogger(env, properties);
    }
}
