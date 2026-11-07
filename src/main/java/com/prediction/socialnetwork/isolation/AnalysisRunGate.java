package com.prediction.socialnetwork.isolation;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AnalysisRunGate {

    private static final AnalysisRunGate SHARED = new AnalysisRunGate();

    private final Semaphore semaphore = new Semaphore(1);

    public static AnalysisRunGate shared() {
        return SHARED;
    }

    public Lease tryAcquire() {
        return semaphore.tryAcquire() ? new Lease(semaphore) : null;
    }

    public static final class Lease implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }
}
