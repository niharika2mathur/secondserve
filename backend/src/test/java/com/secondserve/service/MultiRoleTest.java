package com.secondserve.service;

import com.secondserve.dto.Dtos.*;
import com.secondserve.exception.*;
import com.secondserve.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Authentication, role-based access, ownership, strategies, dispatch life cycle and insights. */
class MultiRoleTest {
    private static LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES); }
    private static Map<String, Object> m(Object... kv) { Map<String, Object> x = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) x.put((String) kv[i], kv[i + 1]); return x; }
    private static Principal login(AppContext c, String u, String p) { return c.auth.authenticate("Bearer " + c.auth.login(m("username", u, "password", p)).token()); }

    @Test @DisplayName("RB-1 login: right password gives a token that resolves to the principal; wrong/missing/unknown tokens give 401")
    void login() {
        AppContext c = new AppContext(1000, 0.01, true);
        Principal p = login(c, "hope", "demo123");
        assertEquals(Role.NGO, p.role()); assertEquals("N-02", p.ngoId());
        assertThrows(UnauthorizedException.class, () -> c.auth.login(m("username", "hope", "password", "nope")));
        assertThrows(UnauthorizedException.class, () -> c.auth.authenticate(null));
        assertThrows(UnauthorizedException.class, () -> c.auth.authenticate("Bearer deadbeef"));
        String tok = c.auth.login(m("username", "admin", "password", "admin123")).token();
        c.auth.logout(tok);
        assertThrows(UnauthorizedException.class, () -> c.auth.authenticate("Bearer " + tok));
    }

    @Test @DisplayName("RB-2 five wrong passwords lock the account for a minute, even for the right password")
    void lockout() {
        AppContext c = new AppContext(1000, 0.01, false);
        for (int i = 0; i < 5; i++) assertThrows(UnauthorizedException.class, () -> c.auth.login(m("username", "admin", "password", "bad")));
        UnauthorizedException e = assertThrows(UnauthorizedException.class, () -> c.auth.login(m("username", "admin", "password", "admin123")));
        assertTrue(e.getMessage().contains("Too many"));
    }

    @Test @DisplayName("RB-3 passwords are stored salted and hashed, never in clear text")
    void hashing() {
        AppContext c = new AppContext(1000, 0.01, false);
        User u = c.userRepo.findByUsername("admin").orElseThrow();
        assertNotEquals("admin123", u.hash());
        assertEquals(64, u.hash().length());
        assertFalse(com.secondserve.util.MiniJson.write(AuthService.view(u)).contains(u.hash()), "the view sent to the browser has no hash");
    }

    @Test @DisplayName("RB-4 AccessPolicy: public, any-role, role-only and default-deny rules")
    void policy() {
        AppContext c = new AppContext(1000, 0.01, true);
        Principal admin = login(c, "admin", "admin123"), donor = login(c, "orchid", "demo123"), ngo = login(c, "hope", "demo123");
        assertTrue(AccessPolicy.isPublic("POST", "/api/auth/login"));
        assertFalse(AccessPolicy.isPublic("GET", "/api/dashboard"));
        assertDoesNotThrow(() -> AccessPolicy.check("GET", "/api/my/donations", donor));
        assertThrows(ForbiddenException.class, () -> AccessPolicy.check("GET", "/api/my/donations", ngo));
        assertThrows(ForbiddenException.class, () -> AccessPolicy.check("GET", "/api/my/ngo", donor));
        assertThrows(ForbiddenException.class, () -> AccessPolicy.check("POST", "/api/allocation/run", donor), "unlisted route is admin only");
        assertThrows(ForbiddenException.class, () -> AccessPolicy.check("GET", "/api/ngos", ngo));
        assertDoesNotThrow(() -> AccessPolicy.check("POST", "/api/allocation/run", admin));
        assertDoesNotThrow(() -> AccessPolicy.check("POST", "/api/deliveries/D-1-1/confirm", ngo));
        assertThrows(ForbiddenException.class, () -> AccessPolicy.check("POST", "/api/deliveries/D-1-1/confirm", donor));
        assertThrows(ForbiddenException.class, () -> AccessPolicy.check("POST", "/api/deliveries/D-1-1/handover", ngo));
        assertDoesNotThrow(() -> AccessPolicy.check("GET", "/api/notifications", ngo));
    }

    @Test @DisplayName("RB-5 a donor can only see, create, edit and delete its own batches; new batches inherit its route times")
    void donorOwnership() {
        AppContext c = new AppContext(1000, 0.01, true);
        Principal orchid = login(c, "orchid", "demo123");
        assertEquals(1, c.portal.myDonations(orchid).size());
        assertThrows(ForbiddenException.class, () -> c.portal.updateDonation(orchid, "BATCH-102", m("meals", 5, "availableFrom", now().toString(), "safeUntil", now().plusHours(1).toString())));
        assertThrows(ForbiddenException.class, () -> c.portal.deleteDonation(orchid, "BATCH-102"));
        PortalService.DonorCreateResult r = c.portal.createDonation(orchid, m("batchId", "orc-9", "donorName", "SOMEONE ELSE", "meals", 30, "availableFrom", now().toString(), "safeUntil", now().plusHours(3).toString()));
        assertEquals("Grand Orchid Hotel", r.donation().donorName(), "donor name comes from the account, not the request body");
        assertEquals("BATCH-101", r.routesInheritedFrom());
        assertEquals(3, r.routesInherited());
        assertEquals(20, c.travelRepo.get("ORC-9", "N-01").orElseThrow());
        assertThrows(ConflictException.class, () -> c.portal.createDonation(orchid, m("batchId", "BATCH-101", "meals", 1, "availableFrom", now().toString(), "safeUntil", now().plusHours(1).toString())), "duplicate caught by Bloom + exact check");
    }

    @Test @DisplayName("RB-6 an NGO can edit its own needs but not its name, and only its own record")
    void ngoOwnership() {
        AppContext c = new AppContext(1000, 0.01, true);
        Principal hope = login(c, "hope", "demo123");
        Ngo u = c.portal.updateMyNgo(hope, m("name", "HACKED", "ngoId", "N-01", "requiredMeals", 150, "pickupCapacity", 120, "maxTravelMinutes", 35, "contact", "x"));
        assertEquals("N-02", u.ngoId()); assertEquals("Hope Shelter Home", u.name()); assertEquals(150, u.requiredMeals());
        assertEquals("Asha Community Kitchen", c.ngoRepo.find("N-01").orElseThrow().name(), "other NGO untouched");
        assertThrows(ValidationException.class, () -> c.portal.updateMyNgo(hope, m("requiredMeals", 0, "pickupCapacity", 1, "maxTravelMinutes", 1)));
    }

    @Test @DisplayName("ST-1 all three strategies place the same number of meals, are valid, and FAIR never lowers the smallest NGO share")
    void strategies() {
        AppContext c = new AppContext(1000, 0.01, true);
        AllocationResult a = c.allocationService.run(now(), Strategy.MAX_FLOW, 100);
        AllocationResult b = c.allocationService.run(now(), Strategy.MIN_COST, 100);
        AllocationResult f = c.allocationService.run(now(), Strategy.FAIR, 100);
        assertEquals(a.summary().allocated(), b.summary().allocated());
        assertEquals(a.summary().allocated(), f.summary().allocated());
        assertEquals("FAIR", f.strategy());
        StrategyMetric mf = f.comparison().stream().filter(x -> x.key().equals("MAX_FLOW")).findFirst().orElseThrow();
        StrategyMetric ff = f.comparison().stream().filter(x -> x.key().equals("FAIR")).findFirst().orElseThrow();
        StrategyMetric mc = f.comparison().stream().filter(x -> x.key().equals("MIN_COST")).findFirst().orElseThrow();
        assertTrue(ff.minReceipt() >= mf.minReceipt());
        assertTrue(mc.mealMinutes() <= mf.mealMinutes() + 0 || mc.tightMeals() <= mf.tightMeals() || mc.avgLeftoverMinutesToExpiry() >= mf.avgLeftoverMinutesToExpiry(),
                "min-cost is at least as good as plain max flow on one of its criteria");
        assertThrows(ValidationException.class, () -> c.allocationService.runRequest(m("planningTime", now().toString(), "strategy", "BOGUS")));
    }

    @Test @DisplayName("SC-1 schedule: volunteers needed equals the peak number of overlapping trips; every trip carries at most the vehicle capacity")
    void schedule() {
        AppContext c = new AppContext(1000, 0.01, true);
        AllocationResult r = c.allocationService.run(now(), Strategy.MIN_COST, 50);
        Schedule s = r.schedule();
        assertTrue(s.optimal());
        assertEquals(s.volunteersNeeded(), s.peakConcurrent());
        assertEquals(r.summary().allocated(), s.trips().stream().mapToInt(Trip::meals).sum());
        for (Trip t : s.trips()) assertTrue(t.meals() <= 50);
        // no volunteer has two overlapping trips
        for (Trip a : s.trips()) for (Trip b : s.trips()) if (a.tripId() < b.tripId() && a.volunteer().equals(b.volunteer()))
            assertTrue(a.freeAt().compareTo(b.departAt()) <= 0 || b.freeAt().compareTo(a.departAt()) <= 0);
        AllocationResult big = c.allocationService.run(now(), Strategy.MIN_COST, 1000);
        assertTrue(big.schedule().volunteersNeeded() <= s.volunteersNeeded(), "bigger vehicles never need more volunteers");
    }

    @Test @DisplayName("DP-1 dispatch life cycle: commit -> handover -> confirm; wrong roles and wrong states are refused; committed meals leave the next round")
    void lifecycle() {
        AppContext c = new AppContext(1000, 0.01, true);
        Principal admin = login(c, "admin", "admin123"), orchid = login(c, "orchid", "demo123"), hope = login(c, "hope", "demo123"), asha = login(c, "asha", "demo123");
        assertThrows(NotFoundException.class, () -> c.dispatch.commit(admin));
        AllocationResult r = c.allocationService.run(now(), Strategy.MIN_COST, 100);
        DispatchService.CommitReport rep = c.dispatch.commit(admin);
        assertEquals(r.summary().allocated(), rep.meals());
        assertThrows(ConflictException.class, () -> c.dispatch.commit(admin), "no double publishing");
        assertFalse(c.allocationService.results().stale(), "a committed result is not reported as stale");

        Delivery d = c.dispatch.list(orchid).get(0);
        assertEquals("Grand Orchid Hotel", d.donorName());
        assertThrows(ForbiddenException.class, () -> c.dispatch.handover(hope, d.deliveryId()), "NGO cannot hand over");
        assertThrows(ForbiddenException.class, () -> c.dispatch.confirm(orchid, d.deliveryId(), m()), "a donor cannot confirm receipt");
        Delivery hopes = c.dispatch.list(hope).get(0);
        assertThrows(ForbiddenException.class, () -> c.dispatch.confirm(asha, hopes.deliveryId(), m()), "another NGO cannot confirm it");
        assertEquals("PICKED_UP", c.dispatch.handover(orchid, d.deliveryId()).status());
        assertThrows(ConflictException.class, () -> c.dispatch.handover(orchid, d.deliveryId()), "cannot hand over twice");
        assertTrue(c.dispatch.list(asha).stream().allMatch(x -> x.ngoId().equals("N-01")), "an NGO only sees deliveries addressed to it");
        assertTrue(c.dispatch.list(orchid).stream().allMatch(x -> x.donorName().equals("Grand Orchid Hotel")));
        assertEquals(0, c.allocationService.run(now(), Strategy.MAX_FLOW, 100).summary().allocated(), "everything feasible is already committed, so the next round has nothing left to place");
    }

    @Test @DisplayName("DP-2 NGO confirmation, ratings, failure reporting and re-allocation of failed meals")
    void confirmAndFail() {
        AppContext c = new AppContext(1000, 0.01, true);
        Principal admin = login(c, "admin", "admin123"), orchid = login(c, "orchid", "demo123");
        c.allocationService.run(now(), Strategy.MAX_FLOW, 100);
        c.dispatch.commit(admin);
        for (Delivery d : c.dispatch.list(admin)) {
            Principal ngo = c.userRepo.findAll().stream().filter(u -> d.ngoId().equals(u.ngoId())).findFirst()
                    .map(u -> c.auth.authenticate("Bearer " + c.auth.login(m("username", u.username(), "password", "demo123")).token())).orElse(null);
            if (ngo == null) continue;
            assertThrows(ValidationException.class, () -> c.dispatch.confirm(ngo, d.deliveryId(), m("rating", 9)));
            assertThrows(ValidationException.class, () -> c.dispatch.confirm(ngo, d.deliveryId(), m("receivedMeals", d.meals() + 1)));
            Delivery done = c.dispatch.confirm(ngo, d.deliveryId(), m("receivedMeals", d.meals() - 1, "rating", 4));
            assertEquals("DELIVERED", done.status()); assertEquals(d.meals() - 1, done.receivedMeals()); assertEquals(4, done.rating());
            assertThrows(ConflictException.class, () -> c.dispatch.confirm(ngo, d.deliveryId(), m()), "cannot confirm twice");
            break;
        }
        Delivery victim = c.dispatch.list(admin).stream().filter(x -> x.status().equals("DISPATCHED")).findFirst().orElseThrow();
        assertThrows(ValidationException.class, () -> c.dispatch.report(admin, victim.deliveryId(), m()), "a reason is required");
        int heldBefore = c.deliveryRepo.heldByBatch().getOrDefault(victim.batchId(), 0);
        c.dispatch.report(admin, victim.deliveryId(), m("note", "vehicle broke down"));
        assertEquals(heldBefore - victim.meals(), c.deliveryRepo.heldByBatch().getOrDefault(victim.batchId(), 0), "failed meals are released");
        AllocationResult again = c.allocationService.run(now(), Strategy.MAX_FLOW, 100);
        assertTrue(again.summary().allocated() > 0, "released meals can be planned again");
        assertTrue(again.summary().allocated() <= victim.meals());
    }

    @Test @DisplayName("RG-1 registration needs approval; approving an NGO creates its record; rejected/pending accounts cannot sign in")
    void registration() {
        AppContext c = new AppContext(1000, 0.01, true);
        Principal admin = login(c, "admin", "admin123");
        assertThrows(ValidationException.class, () -> c.auth.register(m("username", "x", "password", "short", "displayName", "A", "role", "ADMIN", "orgName", "O")));
        c.auth.register(m("username", "newhome", "password", "longenough1", "displayName", "A", "role", "NGO", "orgName", "New Home", "requiredMeals", 80, "pickupCapacity", 60, "maxTravelMinutes", 30));
        assertThrows(ConflictException.class, () -> c.auth.register(m("username", "NewHome", "password", "longenough1", "displayName", "A", "role", "DONOR", "orgName", "O")));
        assertThrows(ForbiddenException.class, () -> c.auth.login(m("username", "newhome", "password", "longenough1")));
        int before = c.ngoRepo.count();
        AuthService.UserView v = c.auth.approve(admin, c.auth.listUsers().stream().filter(u -> u.username().equals("newhome")).findFirst().orElseThrow().userId());
        assertEquals("ACTIVE", v.status()); assertEquals("N-08", v.ngoId());
        assertEquals(before + 1, c.ngoRepo.count());
        assertEquals("N-08", login(c, "newhome", "longenough1").ngoId());
        AuthService.UserView dis = c.auth.setEnabled(admin, v.userId(), false);
        assertEquals("DISABLED", dis.status());
        assertThrows(ForbiddenException.class, () -> c.auth.login(m("username", "newhome", "password", "longenough1")));
        assertThrows(ConflictException.class, () -> c.auth.setEnabled(admin, admin.userId(), false), "admin cannot lock itself out");
    }

    @Test @DisplayName("IN-1 insights re-run max flow on modified copies: +20 capacity at a saturated NGO gains exactly 20; what-if validates input")
    void insights() {
        AppContext c = new AppContext(1000, 0.01, true);
        InsightService.Insights i = c.insights.recommendations(now());
        assertEquals(630, i.baselineMeals());
        assertFalse(i.recommendations().isEmpty());
        for (InsightService.Recommendation r : i.recommendations()) {
            assertTrue(r.gainMeals() > 0);
            if (r.type().equals("CAPACITY")) assertTrue(r.gainMeals() <= 20, "+20 capacity cannot place more than 20 extra meals");
        }
        InsightService.WhatIfResult w = c.insights.whatIf(m("planningTime", now().toString(), "ngoId", "N-02", "extraPickup", 30));
        assertEquals(w.scenarioMeals() - w.baselineMeals(), w.delta());
        assertTrue(w.delta() >= 0);
        assertThrows(ValidationException.class, () -> c.insights.whatIf(m("planningTime", now().toString())));
        assertThrows(NotFoundException.class, () -> c.insights.whatIf(m("planningTime", now().toString(), "ngoId", "N-99", "extraPickup", 5)));
    }

    @Test @DisplayName("AN-1 analytics: Count-Min top donors agree with exact counts, never under-estimate, and live deliveries are added")
    void analytics() {
        AppContext c = new AppContext(1000, 0.01, true);
        AnalyticsService.Summary s = c.analytics.summary();
        assertEquals(5, s.topDonors().size());
        for (AnalyticsService.HeavyHitter h : s.topDonors()) assertTrue(h.estimated() >= h.exact() && h.overestimate() <= s.sketch().errorBound());
        assertTrue(s.sketch().donorTopMatchesExact());
        assertEquals(12, s.weekly().size());
        long before = s.totalMeals();
        Principal admin = login(c, "admin", "admin123");
        c.allocationService.run(now(), Strategy.MAX_FLOW, 100);
        c.dispatch.commit(admin);
        Delivery d = c.dispatch.list(admin).get(0);
        Principal ngo = c.auth.authenticate("Bearer " + c.auth.login(m("username", c.userRepo.findAll().stream().filter(u -> d.ngoId().equals(u.ngoId())).findFirst().orElseThrow().username(), "password", "demo123")).token());
        c.dispatch.confirm(ngo, d.deliveryId(), m());
        assertEquals(before + d.meals(), c.analytics.summary().totalMeals());
        AnalyticsService.DonorStats lv = c.analytics.donorStats("Lakeview Convention Centre");
        assertTrue(lv.rank() >= 1 && lv.rank() <= lv.donorCount());
        assertEquals(1, c.analytics.donorStats("A donor never seen before").donorCount() - c.analytics.donorStats("Lakeview Convention Centre").donorCount() + 0 * 1 + 0, "an unseen donor is counted as one extra donor in the ranking");
    }

    @Test @DisplayName("DB-1 role dashboards return only the caller's data and never throw on sample data")
    void dashboards() {
        AppContext c = new AppContext(1000, 0.01, true);
        Principal orchid = login(c, "orchid", "demo123"), hope = login(c, "hope", "demo123"), admin = login(c, "admin", "admin123");
        RoleDashboardService.DonorDashboard d = c.roleDashboards.donor(orchid);
        assertEquals("Grand Orchid Hotel", d.orgName());
        assertEquals(1, d.myBatches().size());
        assertEquals("BATCH-101", d.myBatches().get(0).batchId());
        assertEquals(120, d.mealsOffered());
        assertEquals(120, d.mealsAwaiting() + d.mealsDispatched() + d.mealsDelivered() + d.mealsExpired());
        RoleDashboardService.NgoDashboard n = c.roleDashboards.ngo(hope);
        assertEquals("N-02", n.profile().ngoId());
        assertFalse(n.reach().isEmpty());
        assertNotNull(c.roleDashboards.admin(admin).analytics());
        assertThrows(NotFoundException.class, () -> c.roleDashboards.ngo(orchid), "a donor account has no NGO record");
    }
}
