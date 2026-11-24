package com.secondserve.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Interval partitioning (greedy, Kleinberg-Tardos): give every task [start, end) to a resource so that no resource
 * handles two overlapping tasks, using the MINIMUM number of resources.
 *
 * Sort tasks by start time; keep a min-heap of resources keyed by the time they become free. For each task, reuse the
 * resource that frees earliest if it is free by the task's start, otherwise open a new resource. This is optimal because the
 * number of resources opened never exceeds the maximum number of simultaneously running tasks (the "depth"), and depth is
 * also a lower bound. Time O(n log n), space O(n).
 */
public final class IntervalPartition {
    private IntervalPartition() {}

    public record Task(int id, long start, long end) {}
    public record Result(int[] resourceOfTask, int resources, int depth) {}

    public static Result partition(List<Task> tasks) {
        for (Task t : tasks) if (t.end() <= t.start()) throw new IllegalArgumentException("Task " + t.id() + " must end after it starts (positive duration)");
        List<Task> order = new ArrayList<>(tasks);
        order.sort(Comparator.comparingLong(Task::start).thenComparingLong(Task::end).thenComparingInt(Task::id));
        PriorityQueue<long[]> free = new PriorityQueue<>(Comparator.comparingLong((long[] a) -> a[0]).thenComparingLong(a -> a[1])); // {freeAt, resourceId}
        int[] res = new int[tasks.size()];
        int maxId = -1;
        for (Task t : tasks) maxId = Math.max(maxId, t.id());
        int[] byId = new int[maxId + 1];
        Arrays.fill(byId, -1);
        int opened = 0;
        for (Task t : order) {
            long[] top = free.peek();
            int r;
            if (top != null && top[0] <= t.start()) { free.poll(); r = (int) top[1]; }
            else r = opened++;
            free.add(new long[]{t.end(), r});
            byId[t.id()] = r;
        }
        for (int i = 0; i < tasks.size(); i++) res[i] = byId[tasks.get(i).id()];
        return new Result(res, opened, depth(tasks));
    }

    /** Maximum number of tasks running at the same instant (sweep line over +1/-1 events; end is exclusive). */
    public static int depth(List<Task> tasks) {
        long[][] ev = new long[tasks.size() * 2][];
        int k = 0;
        for (Task t : tasks) { ev[k++] = new long[]{t.start(), 1}; ev[k++] = new long[]{t.end(), -1}; }
        Arrays.sort(ev, (a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1])); // -1 before +1 at equal time
        int cur = 0, best = 0;
        for (long[] e : ev) { cur += (int) e[1]; best = Math.max(best, cur); }
        return best;
    }
}
