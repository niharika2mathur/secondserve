# Benchmark results (measured)

Executed 2026-11-21T05:14:52 on Java 21.0.13.1, 1 CPU(s), seed 2026, median of 7 runs.

## Edmonds-Karp vs First-Come-First-Served

| Donors x NGOs | Feasible routes | Supply | EK meals | FCFS meals | Gap (meals) | Gap % | EK ms | FCFS ms | Augmentations | Valid |
|---|---|---|---|---|---|---|---|---|---|---|
| 10x10 | 26 | 672 | 620 | 620 | 0 | 0.0% | 0.0182 | 0.0032 | 12 | true |
| 25x25 | 155 | 1766 | 1766 | 1581 | 185 | 11.7% | 0.2871 | 0.0167 | 51 | true |
| 50x50 | 651 | 3320 | 3320 | 3267 | 53 | 1.6% | 1.3479 | 0.0532 | 88 | true |
| 100x100 | 2463 | 7023 | 7023 | 7023 | 0 | 0.0% | 6.8400 | 0.0598 | 184 | true |

## Bloom filter vs exact HashSet membership

| Identifiers | Neg. queries | Bits (m) | k | Bloom bytes | HashSet bytes (approx) | Bloom ms | HashSet ms | Bloom+exact ms | False positives | Observed FP rate | Theoretical FP rate | False negatives | Exact lookups avoided |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 100 | 20000 | 959 | 7 | 120 | 9904 | 1.9401 | 0.1982 | 1.8728 | 197 | 0.0099 | 0.0100 | 0 | 19803 |
| 1000 | 20000 | 9586 | 7 | 1200 | 96272 | 2.1197 | 0.2245 | 2.1267 | 204 | 0.0102 | 0.0100 | 0 | 19796 |
| 10000 | 20000 | 95851 | 7 | 11984 | 945616 | 7.4317 | 0.4607 | 7.7304 | 199 | 0.0100 | 0.0100 | 0 | 19801 |

## Min-cost flow and fair share vs plain maximum flow

| Size | Meals (all equal) | EK ms | Min-cost ms | Fair ms | Fair max-flow calls | Meal-minutes EK | Meal-minutes min-cost | Travel saving | Tight meals EK -> min-cost | Smallest NGO share EK -> fair | Jain EK -> fair | Valid |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 10x10 | 624 | 0.008 | 0.259 | 0.415 | 47 | 21645 | 20599 | 4.8% | 0 -> 0 | 0 -> 38 | 0.835 -> 0.945 | true |
| 25x25 | 1816 | 0.063 | 7.763 | 5.998 | 26 | 73533 | 41543 | 43.5% | 50 -> 0 | 0 -> 31 | 0.873 -> 0.982 | true |
| 50x50 | 3321 | 0.314 | 7.255 | 41.229 | 44 | 110741 | 50782 | 54.1% | 0 -> 0 | 0 -> 31 | 0.891 -> 0.978 | true |
| 100x100 | 6545 | 7.461 | 40.540 | 305.879 | 87 | 240755 | 83748 | 65.2% | 491 -> 0 | 0 -> 32 | 0.822 -> 0.967 | true |

## Count-Min Sketch vs exact HashMap (skewed donor stream)

| Events | Distinct keys | Sketch bytes | HashMap bytes (approx) | Sketch ms | HashMap ms | Error bound (eps*N) | Max over-estimate | Keys within bound | Never under-estimates | Top-10 correct |
|---|---|---|---|---|---|---|---|---|---|---|
| 10000 | 500 | 108760 | 51200 | 0.60 | 0.58 | 603 | 0 | 100.00% | true | true |
| 100000 | 4991 | 108760 | 518264 | 14.15 | 10.40 | 6053 | 2671 | 100.00% | true | true |
| 1000000 | 49942 | 108760 | 5193168 | 273.26 | 324.68 | 60533 | 31805 | 100.00% | true | true |

## Volunteer scheduling: greedy interval partitioning (heap) vs linear first-fit

| Trips | Volunteers used | Depth (lower bound) | Optimal | Heap ms | Linear scan ms |
|---|---|---|---|---|---|
| 1000 | 3 | 3 | true | 3.358 | 0.446 |
| 10000 | 8 | 8 | true | 43.741 | 9.564 |
| 100000 | 26 | 26 | true | 153.813 | 32.767 |

### Notes

- Times are the median of 7 timed repetitions after warm-up runs, measured with System.nanoTime on this machine; they vary between machines.
- Flow instances: random supply 20-120, NGO capacity 30-130, each donor-NGO route feasible with probability 0.25; seed = 2026 + size.
- Both allocators use the same feasibility matrix and capacities; every allocation is validated against supply, capacity and feasibility constraints.
- Network memory is a structural estimate (arc arrays), not a heap measurement. Exact-set memory is an estimate: bytes allocated while building a pre-sized HashSet plus its String objects.
- Bloom queries: all inserted IDs (to confirm zero false negatives) plus up to 20000 never-inserted IDs per size.
- An in-memory HashSet is itself very fast, so Bloom screening is not expected to beat it on speed; its value is avoiding exact lookups against a slower authoritative store while using far less memory.
- Strategy instances: same generator as the flow benchmark plus travel 10-60 min and 60-300 min to deadline per batch; cost = travel + 2*max(0, 30 - slack) on routes and minutes-to-deadline/15 (capped at 720) on supply. Fair share is timed with min(repetitions, 3) runs (1 at 100x100) because it needs many max-flow calls.
- Count-Min: epsilon 0.001, delta 0.01, keys skewed so that a few donors dominate; the exact map is a HashMap<String, Long>; its memory is an analytic estimate (node + Long + String objects), the sketch memory is exact (width x depth x 8 bytes).
- Interval partitioning: trip durations 20-180 min over a 0-1,000,000 min window; the linear-scan baseline checks volunteers in order for the first free one. Both are optimal (resources = depth). The heap version is NOT faster in this experiment: the number of volunteers k stays small (at most a few dozen), so an O(n k) scan beats O(n log k) heap bookkeeping; the heap only pays off when k is large.
