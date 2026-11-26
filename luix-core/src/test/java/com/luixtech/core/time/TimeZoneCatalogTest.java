package com.luixtech.core.time;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeZoneCatalogTest {

    @Test
    void listsCanonicalZonesWithFormattedOffsets() {
        List<TimeZoneOption> options = TimeZoneCatalog.listSupportedTimeZones(Instant.parse("2026-01-15T12:00:00Z"));
        assertFalse(options.isEmpty());
        assertTrue(options.stream().anyMatch(opt ->
                "UTC".equals(opt.id()) && "+00:00".equals(opt.offset()) && "UTC (+00:00)".equals(opt.label())));
        assertTrue(options.stream().anyMatch(opt ->
                "Asia/Shanghai".equals(opt.id()) && "+08:00".equals(opt.offset()) && "Asia/Shanghai (+08:00)".equals(opt.label())));
        assertTrue(options.stream().noneMatch(opt -> "EST".equals(opt.id())));
    }

    @Test
    void parsesValidZoneIdsAndNormalizesLocales() {
        assertEquals(Optional.of(ZoneId.of("America/New_York")), TimeZoneCatalog.parseValidZoneId(" America/New_York "));
        assertEquals(Optional.empty(), TimeZoneCatalog.parseValidZoneId("Mars/Olympus"));
        assertEquals(Optional.empty(), TimeZoneCatalog.parseValidZoneId("   "));

        assertEquals("zh", TimeZoneCatalog.normalizeLocale("zh-CN"));
        assertEquals("zh", TimeZoneCatalog.normalizeLocale("ZH"));
        assertEquals("en", TimeZoneCatalog.normalizeLocale("en-US"));
        assertEquals("en", TimeZoneCatalog.normalizeLocale(null));
    }
}
