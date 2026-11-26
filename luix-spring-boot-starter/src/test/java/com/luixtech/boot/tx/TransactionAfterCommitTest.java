package com.luixtech.boot.tx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionAfterCommitTest {

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void executesImmediatelyWhenNoTransactionSynchronizationIsActive() {
        AtomicInteger counter = new AtomicInteger();
        TransactionAfterCommit.run(counter::incrementAndGet);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void defersExecutionUntilAfterCommitWhenSynchronizationIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger counter = new AtomicInteger();

        TransactionAfterCommit.run(counter::incrementAndGet);
        assertThat(counter.get()).isZero();

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        assertThat(counter.get()).isEqualTo(1);
    }
}
