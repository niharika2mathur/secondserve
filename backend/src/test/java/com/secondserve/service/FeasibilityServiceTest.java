package com.secondserve.service;

import com.secondserve.model.Donation;
import com.secondserve.model.Ngo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Domain feasibility rules: deadline, arrival time, NGO travel limit, travel entry, capacity. */
class FeasibilityServiceTest {
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 17, 12, 0);
    private static final Ngo NGO = new Ngo("N-1", "Test NGO", 100, 80, 30, "");

    private static FeasibilityService.Pair eval(Donation d, Ngo n, Integer minutes, LocalDateTime planning) {
        Map<String, Integer> t = minutes == null ? Map.of() : Map.of(d.batchId() + "|" + n.ngoId(), minutes);
        return FeasibilityService.evaluate(List.of(d), List.of(n), (a, b) -> Optional.ofNullable(t.get(a + "|" + b)), planning).pairs[0][0];
    }

    private static Donation donation(int meals, int availOffset, int deadlineOffset) {
        return new Donation("B-1", "Donor", meals, T0.plusMinutes(availOffset), T0.plusMinutes(deadlineOffset), "");
    }

    @Test @DisplayName("FE-1 feasible when arrival is before the deadline and travel is within the NGO limit")
    void feasible() {
        var p = eval(donation(50, -10, 90), NGO, 25, T0);
        assertTrue(p.feasible());
        assertEquals("OK", p.code());
    }

    @Test @DisplayName("FE-2 expired batch (deadline not after planning time) is infeasible with reason EXPIRED")
    void expired() {
        assertEquals("EXPIRED", eval(donation(50, -60, 0), NGO, 10, T0).code());
        assertEquals("EXPIRED", eval(donation(50, -60, -5), NGO, 10, T0).code());
    }

    @Test @DisplayName("FE-3 missing travel time is reported, never silently treated as zero")
    void missingTravel() {
        var p = eval(donation(50, -10, 90), NGO, null, T0);
        assertFalse(p.feasible());
        assertEquals("NO_TRAVEL_TIME", p.code());
    }

    @Test @DisplayName("FE-4 travel time above the NGO limit is rejected (31 > 30) and exactly 30 is accepted")
    void ngoTravelLimit() {
        assertEquals("NGO_TRAVEL_LIMIT", eval(donation(50, -10, 200), NGO, 31, T0).code());
        assertTrue(eval(donation(50, -10, 200), NGO, 30, T0).feasible());
    }

    @Test @DisplayName("FE-5 arrival after the safe deadline is rejected; arrival exactly at the deadline is accepted")
    void arrivalAfterDeadline() {
        assertEquals("ARRIVAL_AFTER_DEADLINE", eval(donation(50, -10, 20), NGO, 25, T0).code());
        assertTrue(eval(donation(50, -10, 25), NGO, 25, T0).feasible());
    }

    @Test @DisplayName("FE-6 a batch available only later departs at its availability time, not at planning time")
    void departureWaitsForAvailability() {
        // available +60, deadline +80, travel 25 -> arrival +85 > +80
        assertEquals("ARRIVAL_AFTER_DEADLINE", eval(donation(50, 60, 80), NGO, 25, T0).code());
        assertTrue(eval(donation(50, 60, 90), NGO, 25, T0).feasible());
    }

    @Test @DisplayName("FE-7 expired batches contribute zero supply to the network")
    void expiredSupplyIsZero() {
        var ev = FeasibilityService.evaluate(List.of(donation(50, -60, -5)), List.of(NGO), (a, b) -> Optional.of(10), T0);
        assertArrayEquals(new int[]{0}, ev.supply());
        assertEquals(0, ev.feasibleCount());
    }
}
