package com.luixtech.boot.jooq;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Null-safe type conversion helpers commonly needed when mapping jOOQ PostgreSQL records to domain POJOs.
 */
public final class JooqTypeSupport {

    private JooqTypeSupport() {
    }

    public static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static Instant toInstant(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
    }

    public static BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    public static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
