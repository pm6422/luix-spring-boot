package com.luixtech.boot.account;

import java.util.Map;

/**
 * Optional SPI allowing host applications to append application-specific fields
 * (such as webhook tokens or custom flags) to the {@code GET /open-api/accounts/user} response.
 */
@FunctionalInterface
public interface LuixAccountResponseCustomizer {

    /**
     * Populates additional top-level JSON attributes for the given authenticated user.
     *
     * @param user        resolved current user
     * @param extraFields mutable map merged into the account response payload
     */
    void customize(LuixAppUser user, Map<String, Object> extraFields);
}
