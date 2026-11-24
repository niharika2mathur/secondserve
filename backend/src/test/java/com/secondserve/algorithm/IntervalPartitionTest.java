package com.secondserve.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class IntervalPartitionTest {

    @Test @DisplayName("IP-1 disjoint tasks need one resource; fully overlapping tasks need one each")
    void extremes() {
        List<IntervalPartition.Task> seq = List.of(new IntervalPartition.Task(0, 0, 10), new IntervalPartition.Task(1, 10, 20), new IntervalPartition.Task(2, 20, 30));
        assertEquals(1, IntervalPartition.partition(seq).resources());
        List<IntervalPartition.Task> par = List.of(new IntervalPartition.Task(0, 0, 10), new IntervalPartition.Task(1, 0, 10), new IntervalPartition.Task(2, 5, 15));
        assertEquals(3, IntervalPartition.partition(par).resources());
    }

    @Test @DisplayName("IP-2 empty input and invalid task")
    void edge() {
        IntervalPartition.Result r = IntervalPartition.partition(List.of());
        assertEquals(0, r.resources()); assertEquals(0, r.depth());
        assertThrows(IllegalArgumentException.class, () -> IntervalPartition.partition(List.of(new IntervalPartition.Task(0, 5, 5))));
    }

    @Test @DisplayName("IP-3 500 random instances: no resource has overlapping tasks and resources == depth (optimal)")
    void optimal() {
        Random rnd = new Random(5);
        for (int it = 0; it < 500; it++) {
            int n = 1 + rnd.nextInt(40);
            List<IntervalPartition.Task> tasks = new ArrayList<>();
            for (int i = 0; i < n; i++) { long s = rnd.nextInt(200); tasks.add(new IntervalPartition.Task(i, s, s + 1 + rnd.nextInt(60))); }
            IntervalPartition.Result r = IntervalPartition.partition(tasks);
            assertEquals(r.depth(), r.resources(), "greedy is optimal: resources equal the depth lower bound");
            for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++) {
                if (r.resourceOfTask()[a] != r.resourceOfTask()[b]) continue;
                IntervalPartition.Task x = tasks.get(a), y = tasks.get(b);
                assertTrue(x.end() <= y.start() || y.end() <= x.start(), "tasks " + a + "," + b + " overlap on one resource");
            }
        }
    }
}
