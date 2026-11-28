package com.luixtech.boot.web.spa;

import java.util.Collection;

/**
 * Path matching helper determining whether a missing static resource path should fall back to {@code index.html}.
 */
public final class LuixSpaFallback {

    private LuixSpaFallback() {
    }

    public static boolean isSpaFallbackPath(String resourcePath, Collection<String> excludedPrefixes) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return true;
        }
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        if (excludedPrefixes != null) {
            for (String prefix : excludedPrefixes) {
                if (prefix != null && !prefix.isEmpty()
                        && (normalized.equals(prefix) || normalized.startsWith(prefix))) {
                    return false;
                }
            }
        }
        String lastSegment = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;
        return !lastSegment.contains(".");
    }
}
