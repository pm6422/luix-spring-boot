package com.luixtech.boot.crypto;

import com.luixtech.core.crypto.AesGcmCrypto;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Opt-in auto-configuration exposing an {@link AesGcmCrypto} bean when {@code luix.crypto.encryption-key} is set.
 */
@AutoConfiguration
@EnableConfigurationProperties(LuixCryptoProperties.class)
@ConditionalOnClass(AesGcmCrypto.class)
@ConditionalOnProperty(prefix = "luix.crypto", name = "encryption-key")
public class LuixCryptoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AesGcmCrypto.class)
    AesGcmCrypto luixAesGcmCrypto(LuixCryptoProperties properties) {
        String key = properties.getEncryptionKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("luix.crypto.encryption-key must not be blank");
        }
        return properties.getEncoding() == LuixCryptoProperties.Encoding.URL_SAFE
                ? AesGcmCrypto.ofUrlSafeBase64(key)
                : AesGcmCrypto.ofStandardBase64(key);
    }
}
