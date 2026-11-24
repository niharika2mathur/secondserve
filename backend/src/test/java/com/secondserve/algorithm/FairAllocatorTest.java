package com.secondserve.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Progressive-filling fairness checked against exhaustive leximin on small instances. */
class FairAllocatorTest {

    @Test @DisplayName("FA-1 equal split: 10 meals, two NGOs that can each take 10 -> 5 / 5, total unchanged")
    void equalSplit() {
        FairAllocator.Result r = FairAllocator.solve(new int[]{10}, new int[]{10, 10}, new boolean[][]{{true, true}});
        assertArrayEquals(new int[]{5, 5}, r.receipt());
        assertEquals(10, r.total());
    }

    @Test @DisplayName("FA-2 water-filling: small NGO saturates first, the rest share what remains (4 / 8 / 8 of 20)")
    void waterFilling() {
        FairAllocator.Result r = FairAllocator.solve(new int[]{20}, new int[]{4, 20, 20}, new boolean[][]{{true, true, true}});
        int[] got = r.receipt().clone();
        assertEquals(20, r.total());
        assertEquals(4, got[0]); assertEquals(8, got[1]); assertEquals(8, got[2]);
        assertFalse(r.rounds().isEmpty());
    }

    @Test @DisplayName("FA-3 an unreachable NGO is frozen at 0 and does not hold back the others")
    void unreachable() {
        FairAllocator.Result r = FairAllocator.solve(new int[]{9}, new int[]{5, 5}, new boolean[][]{{true, false}});
        assertArrayEquals(new int[]{5, 0}, r.receipt());
        assertEquals(5, r.total());
    }

    @Test @DisplayName("FA-4 Jain index: equal = 1, one-takes-all = 1/n")
    void jain() {
        assertEquals(1.0, FairAllocator.jain(new double[]{3, 3, 3}), 1e-12);
        assertEquals(1.0 / 4, FairAllocator.jain(new double[]{8, 0, 0, 0}), 1e-12);
        assertEquals(1.0, FairAllocator.jain(new double[]{0, 0}), 1e-12);
    }

    @Test @DisplayName("FA-5 300 random instances: valid, total = max flow, receipt vector equals exhaustive leximin optimum")
    void matchesBruteForceLeximin() {
        Random rnd = new Random(11);
        for (int it = 0; it < 300; it++) {
            int b = 2 + rnd.nextInt(2), n = 2 + rnd.nextInt(2);
            int[] supply = new int[b], cap = new int[n];
            boolean[][] ok = new boolean[b][n];
            for (int i = 0; i < b; i++) supply[i] = rnd.nextInt(5);
            for (int j = 0; j < n; j++) cap[j] = rnd.nextInt(6);
            for (int i = 0; i < b; i++) for (int j = 0; j < n; j++) ok[i][j] = rnd.nextInt(4) != 0;
            FairAllocator.Result r = FairAllocator.solve(supply, cap, ok);
            assertNull(FcfsAllocator.validate(supply, cap, ok, r.flow()));
            assertEquals(FcfsAllocator.total(AllocationFlowModel.solve(supply, cap, ok)), r.total(), "fairness costs no meals");
            int[] recv = new int[n];
            for (int i = 0; i < b; i++) for (int j = 0; j < n; j++) recv[j] += r.flow()[i][j];
            assertArrayEquals(r.receipt(), recv, "reported receipt vector matches the flow");
            int[][] best = {null};
            enumerate(supply, cap, ok, new int[b][n], 0, best);
            int[] mine = recv.clone(); Arrays.sort(mine);
            assertArrayEquals(best[0], mine, "leximin-optimal sorted receipt vector");
        }
    }

    private static void enumerate(int[] supply, int[] cap, boolean[][] ok, int[][] f, int cell, int[][] best) {
        int b = supply.length, n = cap.length;
        if (cell == b * n) {
            int[] recv = new int[n];
            for (int i = 0; i < b; i++) for (int j = 0; j < n; j++) recv[j] += f[i][j];
            for (int j = 0; j < n; j++) if (recv[j] > cap[j]) return;
            Arrays.sort(recv);
            if (best[0] == null || lex(recv, best[0]) > 0) best[0] = recv;
            return;
        }
        int i = cell / n, j = cell % n;
        int used = 0; for (int jj = 0; jj < j; jj++) used += f[i][jj];
        int maxV = ok[i][j] ? supply[i] - used : 0;
        for (int v = 0; v <= maxV; v++) { f[i][j] = v; enumerate(supply, cap, ok, f, cell + 1, best); }
        f[i][j] = 0;
    }

    private static int lex(int[] a, int[] b) { for (int k = 0; k < a.length; k++) if (a[k] != b[k]) return Integer.compare(a[k], b[k]); return 0; }
}
