package com.secondserve.model;

import java.time.LocalDateTime;

/** A batch of prepared meal packs offered by a food business. Times are local (no time zone), user supplied. */
public record Donation(String batchId, String donorName, int meals, LocalDateTime availableFrom,
                       LocalDateTime safeUntil, String notes) {}
