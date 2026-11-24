package com.secondserve.model;

/** A recipient organisation. The effective intake in one planning window is min(requiredMeals, pickupCapacity). */
public record Ngo(String ngoId, String name, int requiredMeals, int pickupCapacity, int maxTravelMinutes, String contact) {
    public int effectiveCapacity() { return Math.min(requiredMeals, pickupCapacity); }
}
