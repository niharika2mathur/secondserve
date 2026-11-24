# Correctness test report

**Run:** JUnit 5 (JUnit Platform Console launcher), JDK 21 compiling with `--release 17`, 2026-11-21.
**Result: 66 tests found, 66 successful, 0 failed, 0 skipped.** (36 original + 30 for the multi-role version, listed at the end.) Raw output: [test-output.txt](test-output.txt).
Run yourself with `mvn test` in `backend/` (note: this report's run used the JUnit console launcher because Maven Central was unreachable in the authoring environment; `mvn test` was not executed there).

## Edmonds-Karp (`EdmondsKarpTest`) — 10 tests
| ID | Scenario | Expected |
|---|---|---|
| EK-1 | CLRS textbook network | max flow 23, min-cut capacity 23, s in S, t not in S |
| EK-2 | Diamond with cross edge (multiple augmenting paths) | 20; bottlenecks sum to 20 |
| EK-3 | Destination unreachable | flow 0, 0 augmentations, cut 0 |
| EK-4 | Zero-capacity edge | edge carries 0; other path carries 7 |
| EK-5 | Reverse-edge rerouting required | flow 2; a path uses a reverse arc; shortcut edge ends at 0 |
| EK-6 | Invalid input | IAE for 0/1 nodes, negative capacity, self-loop, bad node, source = sink, null network |
| EK-7 | Boundary graphs | 2 nodes no edges → 0; single edge → its capacity |
| EK-8 | Parallel edges | 3 + 4 = 7 |
| EK-9 | 200 random graphs (seed 42) | capacity limits, conservation at internal nodes, flow = cut capacity |
| EK-10 | Greedy-trap allocation instance | flow 20, FCFS 10, both allocations valid |

## Bloom filter (`BloomFilterTest`) — 8 tests
| ID | Scenario | Expected |
|---|---|---|
| BF-1 | Insert and query; 5,000 IDs | always "possibly present" (no false negatives) |
| BF-2 | Query never-inserted ID in empty filter | false |
| BF-3 | Possible duplicate | exact lookup is performed; outcome DUPLICATE |
| BF-4 | Saturated tiny filter produces a false positive | exact lookup says absent → FALSE_POSITIVE_ACCEPTED, 0 duplicates reported |
| BF-5 | Empty filter and boundary / invalid configs | 0 set bits, expected FPP 0; IAE for n ≤ 0, p ∉ (0,1), null/empty key |
| BF-6 | Same ID inserted twice | second `put` changes no bit |
| BF-7 | Sizing n=1000, p=1 % | m = 9,586, k = 7, 1,200 bytes |
| BF-8 | 1,000 inserted, 20,000 negative queries | observed rate < 2.5 %, theoretical ≈ 1 % |

## Domain and end-to-end (`FeasibilityServiceTest` 7, `AllocationServiceTest` 11)
Expiry, missing travel time, NGO travel-limit boundary (30 accepted / 31 rejected), deadline boundary, availability-delayed departure, expired supply = 0; sample-data allocation valid and ≥ FCFS with flow = cut; expired batch gets nothing; empty dataset rejected with two errors; no routes ⇒ flow 0 with reasons; duplicate rejected and original untouched; delete then reuse ID; multi-error validation; CSV import (2 imported, 1 duplicate, 1 invalid, covering the `0`, `+N`, `-N` minute offsets); stale flag after edit; 150-instance property test (flow ≥ FCFS, all constraints hold); JSON round trip.

## UI / API verification (not part of the JUnit suite)
Using a headless Chromium (Playwright) against the running Java backend and Vite dev server: all 8 pages load; dashboard values match the API; Run Allocation → 630 meals; duplicate `batch-101` rejected with the Bloom + exact explanation; new batch accepted; CSV import (2 imported, 1 duplicate rejected, 1 invalid); travel matrix feasibility; Lab step-through and Bloom membership/sandbox; benchmark run; clear-data → run shows two validation errors; mobile layout renders. This was executed against the dependency-free backend (same service classes), not against Spring Boot.


---
# Multi-role version: 30 additional tests

## Min-cost flow (`MinCostFlowTest`) — 4 tests
| ID | Scenario | Expected |
|---|---|---|
| MC-1 | Textbook network with costs | flow 4, cost 20 |
| MC-2 | Negative-cost reverse arc needed | flow 2, cost 6, cross edge unused |
| MC-3 | Invalid input and no-edge network | IAE for bad nodes/capacity/cost/self-loop; flow 0, cost 0 |
| MC-4 | **400 random allocation instances vs exhaustive search** | same total as Edmonds-Karp, exactly the minimum cost among maximum allocations, valid, reported cost matches returned flow |

## Fair allocation (`FairAllocatorTest`) — 5 tests
| ID | Scenario | Expected |
|---|---|---|
| FA-1 | 10 meals, two NGOs | 5 / 5 |
| FA-2 | Water-filling with caps 4, 20, 20 and 20 meals | 4 / 8 / 8 |
| FA-3 | Unreachable NGO | frozen at 0, others unaffected |
| FA-4 | Jain index | equal = 1, one-takes-all = 1/n |
| FA-5 | **300 random instances vs exhaustive leximin** | valid, total = max flow ("fairness costs 0 meals"), sorted receipt vector equals the leximin optimum |

## Interval partitioning (`IntervalPartitionTest`) — 3 tests
IP-1 extremes (sequential = 1 volunteer, fully overlapping = n); IP-2 empty input and zero-length task rejected; IP-3 **500 random instances**: no volunteer has overlapping trips and volunteers = depth (optimal).

## Count-Min Sketch (`CountMinSketchTest`) — 4 tests
CM-1 sizing (ε = δ = 0.01 → 272 × 5); CM-2 exact on a single key; CM-3 50,000 events: never under-estimates and ≥ 99% of keys within ε·N; CM-4 invalid parameters.

## Roles, security and workflow (`MultiRoleTest`) — 14 tests
| ID | Scenario | Expected |
|---|---|---|
| RB-1 | Login / logout / bad token | token resolves to principal; wrong password, missing, unknown, logged-out tokens → 401 |
| RB-2 | 5 wrong passwords | account locked even for the right password |
| RB-3 | Password storage | salted 256-bit hash, never in the browser-facing view |
| RB-4 | `AccessPolicy` | public, any-role, role-only and default-deny (unlisted = admin-only) rules |
| RB-5 | Donor ownership | cannot edit/delete another donor's batch; donor name forced from account; route times inherited (3 routes from BATCH-101); duplicate ID rejected |
| RB-6 | NGO ownership | edits own needs only, name and id cannot be changed, validation enforced |
| ST-1 | Strategies | all three place the same number of meals; fair never lowers the smallest share; invalid strategy → 400 |
| SC-1 | Schedule | volunteers = peak overlap, no overlapping trips per volunteer, trips ≤ vehicle capacity, bigger vehicles never need more volunteers |
| DP-1 | Dispatch life cycle | commit, no double publish, role/ownership guards on hand-over and confirm, committed meals leave the next round, committed result is not "stale" |
| DP-2 | Confirm / fail | received count and rating validated, no double confirm, reason required for failure, failed meals released and re-planned |
| RG-1 | Registration | needs approval; approving an NGO creates N-08; disabled/pending cannot sign in; admin cannot disable self |
| IN-1 | Insights | baseline 630; +20 capacity gains ≤ 20; what-if validation and not-found handling |
| AN-1 | Analytics | Count-Min top-5 equals exact, never under-estimates, live confirmed deliveries are added to the totals |
| DB-1 | Role dashboards | return only the caller's data; donor account has no NGO dashboard |
