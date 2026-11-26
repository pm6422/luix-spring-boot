package com.luixtech.boot.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * Runs side-effects (e.g. Redis publishes, SSE broadcasts, in-memory scheduler refreshes)
 * only after the surrounding Spring database transaction successfully commits.
 * If no transaction synchronization is active, executes immediately.
 */
public final class TransactionAfterCommit {

    private TransactionAfterCommit() {
    }

    public static void run(Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
