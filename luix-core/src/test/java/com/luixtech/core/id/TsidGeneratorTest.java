package com.luixtech.core.id;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsidGeneratorTest {

    @Test
    void generatesStrictlyMonotonicUniqueNumericIds() {
        TsidGenerator generator = new TsidGenerator(42);
        Set<Long> seen = new HashSet<>(20_000);
        long prev = -1L;
        for (int i = 0; i < 20_000; i++) {
            long id = generator.generate();
            assertTrue(id > prev, "Expected strictly increasing TSID");
            assertTrue(seen.add(id), "Duplicate TSID generated: " + id);
            assertTrue(Long.toString(id).matches("\\d{18,19}"), "Unexpected numeric format: " + id);
            prev = id;
        }
        assertEquals(20_000, seen.size());
        assertEquals(42, generator.nodeId());
    }

    @Test
    void extractsApproximateUtcInstant() {
        Instant before = Instant.now().minusSeconds(1);
        long id = TsidGenerator.nextId();
        Instant extracted = TsidGenerator.extractInstant(id);
        Instant after = Instant.now().plusSeconds(2);

        assertFalse(extracted.isBefore(before));
        assertFalse(extracted.isAfter(after));
    }

    @Test
    void ensureIdPreservesExistingAndGeneratesWhenBlank() {
        assertEquals("existing-123", TsidGenerator.ensureId("existing-123"));
        String fromNull = TsidGenerator.ensureId(null);
        String fromBlank = TsidGenerator.ensureId("   ");
        assertTrue(fromNull.matches("\\d{18,19}"));
        assertTrue(fromBlank.matches("\\d{18,19}"));
    }

    @Test
    void rejectsOutOfRangeNodeId() {
        assertThrows(IllegalArgumentException.class, () -> new TsidGenerator(-1));
        assertThrows(IllegalArgumentException.class, () -> new TsidGenerator(TsidGenerator.MAX_NODE_ID + 1));
    }

    @Test
    void threadSafeUnderConcurrentLoad() throws Exception {
        TsidGenerator generator = new TsidGenerator(7);
        int threads = 8;
        int perThread = 2_500;
        Set<Long> ids = ConcurrentHashMap.newKeySet(threads * perThread);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            Callable<Void> task = () -> {
                for (int i = 0; i < perThread; i++) {
                    assertTrue(ids.add(generator.generate()));
                }
                return null;
            };
            var futures = new java.util.ArrayList<Future<Void>>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(task));
            }
            for (Future<Void> future : futures) {
                future.get();
            }
        }
        assertEquals(threads * perThread, ids.size());
    }
}
