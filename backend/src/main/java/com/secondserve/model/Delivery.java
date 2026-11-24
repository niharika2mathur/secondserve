package com.secondserve.model;

/**
 * One committed hand-over of meals from a donor batch to an NGO (a row of a published dispatch plan).
 * status: DISPATCHED -> PICKED_UP (donor confirms hand-over) -> DELIVERED (NGO confirms receipt) or FAILED (cancelled/reported).
 */
public record Delivery(String deliveryId, long planId, String batchId, String donorName, String ngoId, String ngoName, int meals,
                       String departAt, String arriveBy, String safeUntil, int travelMinutes, String volunteers, String status,
                       Integer receivedMeals, Integer rating, String note, String updatedAt) {

    public static final String DISPATCHED = "DISPATCHED", PICKED_UP = "PICKED_UP", DELIVERED = "DELIVERED", FAILED = "FAILED";

    /** In-flight or finished deliveries still hold their meals; FAILED ones return them to the pool. */
    public boolean holdsMeals() { return !FAILED.equals(status); }

    public Delivery with(String newStatus, Integer received, Integer newRating, String newNote, String now) {
        return new Delivery(deliveryId, planId, batchId, donorName, ngoId, ngoName, meals, departAt, arriveBy, safeUntil, travelMinutes,
                volunteers, newStatus, received == null ? receivedMeals : received, newRating == null ? rating : newRating,
                newNote == null ? note : newNote, now);
    }
}
