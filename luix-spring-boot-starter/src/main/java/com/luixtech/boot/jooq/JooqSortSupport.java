package com.luixtech.boot.jooq;

import org.jooq.Field;
import org.jooq.OrderField;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Safe whitelist-based translation from Spring Data {@link Sort} requests to jOOQ {@link OrderField} clauses.
 */
public final class JooqSortSupport {

    private JooqSortSupport() {
    }

    /**
     * Resolves user-supplied {@link Sort} orders against an explicit whitelist of allowed jOOQ fields.
     * Unknown properties are ignored; if no valid sort order remains, {@code defaultOrder} is used.
     */
    public static List<OrderField<?>> resolveOrderFields(
            Sort sort,
            Map<String, Field<?>> allowedFields,
            OrderField<?>... defaultOrder) {
        List<OrderField<?>> orderFields = new ArrayList<>();
        if (sort != null && sort.isSorted() && allowedFields != null) {
            for (Sort.Order order : sort) {
                Field<?> field = allowedFields.get(order.getProperty());
                if (field != null) {
                    orderFields.add(order.isAscending() ? field.asc() : field.desc());
                }
            }
        }
        if (orderFields.isEmpty() && defaultOrder != null) {
            orderFields.addAll(Arrays.asList(defaultOrder));
        }
        return orderFields;
    }
}
