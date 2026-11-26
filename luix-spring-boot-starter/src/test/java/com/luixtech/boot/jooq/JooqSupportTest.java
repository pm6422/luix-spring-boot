package com.luixtech.boot.jooq;

import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JooqSupportTest {

    @Test
    void convertsInstantAndNumericTypesSafely() {
        Instant now = Instant.parse("2026-11-26T15:00:00Z");
        OffsetDateTime odt = JooqTypeSupport.toOffsetDateTime(now);
        assertThat(odt).isEqualTo(OffsetDateTime.of(2026, 11, 26, 15, 0, 0, 0, ZoneOffset.UTC));
        assertThat(JooqTypeSupport.toInstant(odt)).isEqualTo(now);
        assertThat(JooqTypeSupport.toOffsetDateTime(null)).isNull();
        assertThat(JooqTypeSupport.toInstant(null)).isNull();

        assertThat(JooqTypeSupport.toBigDecimal(12.34)).isEqualByComparingTo(new BigDecimal("12.34"));
        assertThat(JooqTypeSupport.toDouble(new BigDecimal("12.34"))).isEqualTo(12.34);
        assertThat(JooqTypeSupport.toBigDecimal(null)).isNull();
        assertThat(JooqTypeSupport.toDouble(null)).isNull();
    }

    @Test
    void resolvesWhitelistedSortFieldsAndFallsBackToDefault() {
        Field<String> symbol = DSL.field("symbol", String.class);
        Field<Instant> createdAt = DSL.field("created_at", Instant.class);
        Map<String, Field<?>> allowed = Map.of("symbol", symbol, "createdAt", createdAt);

        Sort sort = Sort.by(Sort.Order.asc("symbol"), Sort.Order.desc("sql_injection_field"));
        List<OrderField<?>> orders = JooqSortSupport.resolveOrderFields(sort, allowed, createdAt.desc());
        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().toString()).contains("symbol").contains("asc");

        List<OrderField<?>> fallback = JooqSortSupport.resolveOrderFields(
                Sort.by("unknown"), allowed, createdAt.desc());
        assertThat(fallback).hasSize(1);
        assertThat(fallback.getFirst().toString()).contains("created_at").contains("desc");
    }
}
