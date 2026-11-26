package com.luixtech.core.time;

/**
 * Display option for a supported IANA time zone.
 *
 * @param id     canonical IANA zone ID (e.g. {@code "Asia/Shanghai"})
 * @param label  human-readable label including current UTC offset (e.g. {@code "Asia/Shanghai (+08:00)"})
 * @param offset current formatted UTC offset (e.g. {@code "+08:00"})
 */
public record TimeZoneOption(String id, String label, String offset) {
}
