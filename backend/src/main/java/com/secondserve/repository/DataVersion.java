package com.secondserve.repository;

import java.util.concurrent.atomic.AtomicLong;

/** Monotonic counter bumped on every data change so stored allocation results can be flagged stale. */
public final class DataVersion {
    private final AtomicLong v = new AtomicLong();
    public long bump() { return v.incrementAndGet(); }
    public long get() { return v.get(); }
}
