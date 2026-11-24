package com.secondserve.service;

import com.secondserve.algorithm.AllocationFlowModel;
import com.secondserve.algorithm.FcfsAllocator;
import com.secondserve.benchmark.SyntheticData;
import com.secondserve.dto.Dtos.*;
import com.secondserve.exception.ConflictException;
import com.secondserve.exception.NotFoundException;
import com.secondserve.exception.ValidationException;
import com.secondserve.util.MiniJson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end service tests: allocation, validation, duplicate screening, CSV import, baseline comparison. */
class AllocationServiceTest {
    private static LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES); }

    private static Map<String, Object> donationBody(String id, int meals) {
        Map<String, Object> m = new HashMap<>();
        m.put("batchId", id); m.put("donorName", "Test Donor"); m.put("meals", meals);
        m.put("availableFrom", now().minusMinutes(10).toString()); m.put("safeUntil", now().plusMinutes(180).toString());
        return m;
    }

    @Test @DisplayName("AL-1 sample data: flow allocation is valid, never below FCFS, and equals the min-cut capacity")
    void sampleDataAllocation() {
        AppContext c = new AppContext(1000, 0.01, true);
        AllocationResult r = c.allocationService.run(now());
        assertEquals(r.summary().maxFlow(), r.summary().minCutCapacity());
        assertTrue(r.summary().allocated() >= r.baseline().allocated());
        assertTrue(r.baseline().gapMeals() > 0, "the sample data is designed so that greedy is suboptimal");
        for (NgoOutcome n : r.ngos()) assertTrue(n.allocated() <= n.effectiveCapacity());
        for (BatchOutcome b : r.batches()) assertTrue(b.allocated() <= b.supply());
        int fromRows = r.rows().stream().filter(x -> x.status().equals("ALLOCATED")).mapToInt(Row::meals).sum();
        assertEquals(r.summary().allocated(), fromRows);
    }

    @Test @DisplayName("AL-2 the expired sample batch receives nothing and is reported as EXPIRED")
    void expiredBatchGetsNothing() {
        AppContext c = new AppContext(1000, 0.01, true);
        AllocationResult r = c.allocationService.run(now());
        BatchOutcome b = r.batches().stream().filter(x -> x.batchId().equals("BATCH-107")).findFirst().orElseThrow();
        assertEquals("EXPIRED", b.status());
        assertEquals(0, b.allocated());
    }

    @Test @DisplayName("AL-3 empty dataset: running the allocation fails with clear validation errors (no silent result)")
    void emptyDatasetRejected() {
        AppContext c = new AppContext(1000, 0.01, false);
        ValidationException e = assertThrows(ValidationException.class, () -> c.allocationService.run(now()));
        assertEquals(2, e.getErrors().size());
        assertThrows(NotFoundException.class, () -> c.allocationService.results());
    }

    @Test @DisplayName("AL-4 no travel times entered: maximum flow is 0 and every unallocated batch explains why")
    void noFeasibleRoutes() {
        AppContext c = new AppContext(1000, 0.01, true);
        c.travelRepo.clear();
        AllocationResult r = c.allocationService.run(now());
        assertEquals(0, r.summary().allocated());
        assertTrue(r.bottleneck().edges().isEmpty());
        r.batches().stream().filter(b -> !b.status().equals("EXPIRED")).forEach(b -> assertTrue(b.reason().contains("no travel time entered"), b.reason()));
    }

    @Test @DisplayName("AL-5 duplicate batch ID is rejected (Bloom hit + exact confirmation) and the original record is untouched")
    void duplicateRejected() {
        AppContext c = new AppContext(1000, 0.01, true);
        int before = c.donationRepo.count();
        ConflictException e = assertThrows(ConflictException.class, () -> c.donationService.create(donationBody("batch-101", 10)));
        assertTrue(e.getMessage().contains("exact"));
        assertEquals(before, c.donationRepo.count());
        assertEquals(120, c.donationRepo.find("BATCH-101").orElseThrow().meals());
        assertEquals(1, c.screening.stats().confirmedDuplicates());
    }

    @Test @DisplayName("AL-6 a new ID is accepted and registered in the Bloom filter; deleting it rebuilds the filter so the ID can be reused")
    void createDeleteRecreate() {
        AppContext c = new AppContext(1000, 0.01, true);
        c.donationService.create(donationBody("BATCH-900", 25));
        assertTrue(c.screening.filter().mightContain("BATCH-900"));
        c.donationService.delete("BATCH-900");
        assertFalse(c.donationRepo.exists("BATCH-900"));
        assertDoesNotThrow(() -> c.donationService.create(donationBody("BATCH-900", 30)));
    }

    @Test @DisplayName("AL-7 invalid donation input returns every validation error at once")
    void invalidDonationInput() {
        AppContext c = new AppContext(1000, 0.01, false);
        Map<String, Object> bad = new HashMap<>();
        bad.put("batchId", "x"); bad.put("donorName", ""); bad.put("meals", -5);
        bad.put("availableFrom", "tomorrow"); bad.put("safeUntil", now().toString());
        ValidationException e = assertThrows(ValidationException.class, () -> c.donationService.create(bad));
        assertTrue(e.getErrors().size() >= 4, e.getErrors().toString());
        Map<String, Object> reversed = donationBody("BATCH-500", 5);
        reversed.put("safeUntil", now().minusHours(5).toString());
        assertThrows(ValidationException.class, () -> c.donationService.create(reversed));
        assertEquals(0, c.donationRepo.count());
    }

    @Test @DisplayName("AL-8 CSV import: imports new rows, rejects the duplicate, flags the malformed row, supports 0 / +N / -N minute offsets")
    void csvImport() {
        AppContext c = new AppContext(1000, 0.01, true);
        String csv = "batchId,donor,meals,availableFrom,safeUntil,notes\n"
                + "BATCH-201,Cafe One,30,-10,+120,fresh\n"
                + "BATCH-101,Duplicate Hotel,40,-10,+120,dup\n"
                + "BATCH-202,Broken Row,abc,-10,+120,\n"
                + "BATCH-203,Cafe Two,15,0,+90\n";
        DonationService.ImportReport rep = c.donationService.importCsv(csv);
        assertEquals(2, rep.imported());
        assertEquals(1, rep.duplicatesRejected());
        assertEquals(1, rep.invalid());
        assertTrue(c.donationRepo.exists("BATCH-201") && c.donationRepo.exists("BATCH-203") && !c.donationRepo.exists("BATCH-202"));
    }

    @Test @DisplayName("AL-9 editing data after a run marks the stored result as stale")
    void staleAfterEdit() {
        AppContext c = new AppContext(1000, 0.01, true);
        c.allocationService.run(now());
        assertFalse(c.allocationService.results().stale());
        c.donationService.delete("BATCH-108");
        assertTrue(c.allocationService.results().stale());
    }

    @Test @DisplayName("AL-10 property: on 150 random instances flow >= FCFS and both respect all constraints")
    void flowNeverWorseThanGreedy() {
        for (int seed = 0; seed < 150; seed++) {
            SyntheticData.Instance in = SyntheticData.generate(3 + seed % 12, 3 + (seed * 7) % 12, seed, 0.1 + (seed % 5) * 0.1);
            int[][] flow = AllocationFlowModel.solve(in.supply(), in.capacity(), in.feasible());
            int[][] greedy = FcfsAllocator.allocate(in.supply(), in.capacity(), in.feasible());
            assertNull(FcfsAllocator.validate(in.supply(), in.capacity(), in.feasible(), flow));
            assertNull(FcfsAllocator.validate(in.supply(), in.capacity(), in.feasible(), greedy));
            assertTrue(FcfsAllocator.total(flow) >= FcfsAllocator.total(greedy), "seed " + seed);
        }
    }

    @Test @DisplayName("AL-11 JSON writer/parser round-trip keeps structure (used by the standalone server and the benchmark CLI)")
    void jsonRoundTrip() {
        AppContext c = new AppContext(1000, 0.01, true);
        String json = MiniJson.write(c.allocationService.run(now()));
        Object parsed = MiniJson.parse(json);
        assertTrue(parsed instanceof Map);
        assertTrue(((Map<?, ?>) parsed).containsKey("summary"));
        assertEquals(List.of(1L, 2L), MiniJson.parse("[1, 2]"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{bad"));
    }
}
