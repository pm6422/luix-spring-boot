package com.luixtech.core.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Zero-dependency helpers for supported IANA time zone catalogs, zone validation, and locale normalization.
 */
public final class TimeZoneCatalog {

    public static final List<String> COMMON_TIMEZONE_IDS = List.of(
            "UTC",
            "Asia/Shanghai",
            "Asia/Hong_Kong",
            "Asia/Taipei",
            "Asia/Singapore",
            "Asia/Tokyo",
            "Asia/Seoul",
            "Europe/London",
            "Europe/Berlin",
            "Europe/Paris",
            "Europe/Budapest",
            "America/New_York",
            "America/Chicago",
            "America/Los_Angeles",
            "Australia/Sydney"
    );

    private TimeZoneCatalog() {
    }

    /**
     * Lists all supported canonical IANA time zones (plus {@code UTC} and {@code GMT}), sorted by ID,
     * with offsets computed at the current instant.
     */
    public static List<TimeZoneOption> listSupportedTimeZones() {
        return listSupportedTimeZones(Instant.now());
    }

    /**
     * Lists all supported canonical IANA time zones with offsets computed at {@code referenceInstant}.
     */
    public static List<TimeZoneOption> listSupportedTimeZones(Instant referenceInstant) {
        Instant ref = referenceInstant != null ? referenceInstant : Instant.now();
        List<String> ids = new ArrayList<>(ZoneId.getAvailableZoneIds());
        Collections.sort(ids);

        List<TimeZoneOption> options = new ArrayList<>(ids.size());
        for (String id : ids) {
            if (!isCanonicalZoneId(id)) {
                continue;
            }
            ZoneId zoneId = ZoneId.of(id);
            ZoneOffset offset = zoneId.getRules().getOffset(ref);
            String formattedOffset = "Z".equals(offset.getId()) ? "+00:00" : offset.getId();
            options.add(new TimeZoneOption(id, id + " (" + formattedOffset + ")", formattedOffset));
        }
        return Collections.unmodifiableList(options);
    }

    /**
     * Parses a non-blank IANA time zone ID, returning {@link Optional#empty()} if blank or invalid.
     */
    public static Optional<ZoneId> parseValidZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ZoneId.of(timezone.trim()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Normalizes a locale tag to {@code "zh"} or {@code "en"} (defaulting to {@code "en"}).
     */
    public static String normalizeLocale(String lang) {
        if (lang != null && lang.trim().toLowerCase(Locale.ROOT).startsWith("zh")) {
            return "zh";
        }
        return "en";
    }

    private static boolean isCanonicalZoneId(String id) {
        if ("UTC".equals(id) || "GMT".equals(id)) {
            return true;
        }
        if (id.length() == 3) {
            return false;
        }
        return id.contains("/");
    }
}
