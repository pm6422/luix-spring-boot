package com.luixtech.boot.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Auto-registers a default {@link LuixRestExceptionHandler} when no application-specific
 * {@link LuixRestExceptionHandler} subclass is defined.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestControllerAdvice.class)
@ConditionalOnProperty(prefix = "luix.web.exception-handler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LuixWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LuixRestExceptionHandler.class)
    LuixRestExceptionHandler luixDefaultRestExceptionHandler() {
        return new DefaultLuixRestExceptionHandler();
    }
}
