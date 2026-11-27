package com.luixtech.boot.account;

import com.luixtech.boot.oidc.OidcAccountStore;
import com.luixtech.boot.oidc.PassportOidcUserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

/**
 * Opt-in auto-configuration for the unified {@code app_user} / {@code app_user_role} account module.
 *
 * <p>Enable via {@code luix.account.enabled=true} and include {@code classpath:luix/account/changelog.xml}
 * in your Liquibase master changelog (or migrate existing tables to {@code app_user} and {@code app_user_role}).
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration"
})
@EnableConfigurationProperties(LuixAccountProperties.class)
@ConditionalOnClass({JdbcClient.class, PassportOidcUserService.class})
@ConditionalOnProperty(prefix = "luix.account", name = "enabled", havingValue = "true")
public class LuixAccountAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcClient.class)
    @ConditionalOnMissingBean
    LuixAppUserRepository luixAppUserRepository(JdbcClient jdbcClient) {
        return new LuixAppUserRepository(jdbcClient);
    }

    @Bean
    @ConditionalOnBean(LuixAppUserRepository.class)
    @ConditionalOnMissingBean({LuixAccountService.class, OidcAccountStore.class})
    LuixAccountService luixAccountService(LuixAppUserRepository repository, LuixAccountProperties properties) {
        return new LuixAccountService(repository, properties);
    }

    @Bean
    @ConditionalOnBean(LuixAccountService.class)
    @ConditionalOnMissingBean(PassportOidcUserService.class)
    PassportOidcUserService<LuixAppUser> luixPassportOidcUserService(LuixAccountService accountService) {
        return new PassportOidcUserService<>(accountService);
    }

    @Bean
    @ConditionalOnBean(LuixAccountService.class)
    @ConditionalOnMissingBean
    LuixAccountController luixAccountController(LuixAccountService accountService,
                                                LuixAccountProperties properties,
                                                ObjectProvider<LuixAccountResponseCustomizer> customizersProvider) {
        List<LuixAccountResponseCustomizer> customizers = customizersProvider.orderedStream().toList();
        return new LuixAccountController(accountService, properties, customizers);
    }

    @Bean
    @ConditionalOnBean(LuixAccountService.class)
    @ConditionalOnMissingBean
    LuixUserManagementController luixUserManagementController(LuixAccountService accountService) {
        return new LuixUserManagementController(accountService);
    }
}
