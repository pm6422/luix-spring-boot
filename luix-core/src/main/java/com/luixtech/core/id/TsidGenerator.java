package com.luixtech.core.id;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Thread-safe, zero-dependency 64-bit numeric TSID (Time-Sorted Unique Identifier) generator.
 *
 * <p>Bit layout (64 bits, always non-negative):
 * <ul>
 *   <li>42 bits: milliseconds since {@code 2020-01-01T00:00:00Z} (~139 years)</li>
 *   <li>10 bits: node identifier ({@code 0..1023})</li>
 *   <li>12 bits: per-millisecond monotonic counter ({@code 0..4095})</li>
 * </ul>
 *
 * <p>Bit-compatible with {@code com.github.f4b6a3:tsid-creator}'s default 10-bit node configuration.
 */
public final class TsidGenerator {

    /** Custom epoch: 2020-01-01T00:00:00Z in epoch milliseconds. */
    public static final long TSID_EPOCH = 1_577_836_800_000L;

    public static final int NODE_BITS = 10;
    public static final int COUNTER_BITS = 12;
    public static final int MAX_NODE_ID = (1 << NODE_BITS) - 1;

    private static final int TIMESTAMP_SHIFT = NODE_BITS + COUNTER_BITS;
    private static final long COUNTER_MASK = (1L << COUNTER_BITS) - 1;

    private static final TsidGenerator DEFAULT = new TsidGenerator(new SecureRandom().nextInt(MAX_NODE_ID + 1));

    private final long nodeId;
    private final Object lock = new Object();
    private long lastTimestamp = -1L;
    private long counter = 0L;

    /**
     * Creates a generator pinned to the specified node identifier ({@code 0..1023}).
     *
     * @param nodeId node identifier in range {@code [0, 1023]}
     */
    public TsidGenerator(int nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId must be between 0 and " + MAX_NODE_ID + ", got: " + nodeId);
        }
        this.nodeId = nodeId;
    }

    /**
     * Returns the process-wide default generator instance initialized with a cryptographically random node ID.
     */
    public static TsidGenerator defaultInstance() {
        return DEFAULT;
    }

    /**
     * Generates the next 64-bit numeric TSID using the default generator.
     */
    public static long nextId() {
        return DEFAULT.generate();
    }

    /**
     * Generates the next numeric TSID formatted as a base-10 string using the default generator.
     */
    public static String nextIdString() {
        return Long.toString(DEFAULT.generate());
    }

    /**
     * Returns {@code currentId} if non-null and non-blank; otherwise generates a new TSID string.
     */
    public static String ensureId(String currentId) {
        if (currentId != null && !currentId.isBlank()) {
            return currentId;
        }
        return nextIdString();
    }

    /**
     * Extracts the embedded UTC timestamp from a 64-bit TSID value.
     */
    public static Instant extractInstant(long tsid) {
        long millisSinceEpoch = tsid >>> TIMESTAMP_SHIFT;
        return Instant.ofEpochMilli(TSID_EPOCH + millisSinceEpoch);
    }

    /**
     * Returns this generator's 10-bit node identifier.
     */
    public int nodeId() {
        return (int) nodeId;
    }

    /**
     * Generates the next strictly monotonic 64-bit TSID from this instance.
     */
    public long generate() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            if (now <= lastTimestamp) {
                counter = (counter + 1) & COUNTER_MASK;
                if (counter == 0L) {
                    lastTimestamp++;
                }
                now = lastTimestamp;
            } else {
                lastTimestamp = now;
                counter = 0L;
            }
            return ((now - TSID_EPOCH) << TIMESTAMP_SHIFT) | (nodeId << COUNTER_BITS) | counter;
        }
    }
}
