package com.luixtech.boot.web.spa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;

/**
 * Opt-in auto-configuration serving embedded SPA assets and falling back non-API, extensionless
 * client-side routes to {@code index.html}.
 *
 * <p>Enable via {@code luix.spa.enabled=true}.
 */
@AutoConfiguration
@EnableConfigurationProperties(LuixSpaProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnProperty(prefix = "luix.spa", name = "enabled", havingValue = "true")
public class LuixSpaAutoConfiguration {

    @Bean
    WebMvcConfigurer luixSpaWebMvcConfigurer(LuixSpaProperties properties) {
        String[] locations = properties.getLocations().toArray(String[]::new);
        List<String> excludedPrefixes = List.copyOf(properties.getExcludedPrefixes());
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/**")
                        .addResourceLocations(locations)
                        .resourceChain(true)
                        .addResolver(new PathResourceResolver() {
                            @Override
                            protected Resource getResource(String resourcePath, Resource location) throws IOException {
                                Resource requested = super.getResource(resourcePath, location);
                                if (requested != null) {
                                    return requested;
                                }
                                if (!LuixSpaFallback.isSpaFallbackPath(resourcePath, excludedPrefixes)) {
                                    return null;
                                }
                                Resource index = location.createRelative("index.html");
                                return (index.exists() && index.isReadable()) ? index : null;
                            }
                        });
            }
        };
    }
}
