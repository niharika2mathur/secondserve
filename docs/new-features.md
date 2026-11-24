# What was added in the multi-role version

This document describes the extension of SecondServe from a single-user allocation tool into a complete three-role platform with four new algorithms. Everything below is implemented in the repository and verified by the tests in `backend/src/test` (66 tests, all passing) and the experiments in [benchmark-results.md](benchmark-results.md).

## 1. New algorithms (all implemented from scratch, no libraries)

| # | Algorithm | Syllabus link | Where it is used | Complexity |
|---|---|---|---|---|
| 1 | **Min-cost maximum flow** (Successive Shortest Paths with SPFA / queue-based Bellman-Ford) | Unit 5, network flows | "Smart" allocation: among all maximum allocations pick the cheapest | O(F·V·E) worst case; one SPFA is O(V·E) worst, ≈O(E) typical |
| 2 | **Max-min fair allocation** (progressive filling = binary search on the common level + repeated Edmonds-Karp) | Unit 5, flows; fairness / lexicographic optimisation | "Fair share" allocation | ≤ N rounds × O(log C + N) max-flow calls, each O(V·E²) |
| 3 | **Interval partitioning** (greedy by start time with a min-heap of volunteer free-times) | Greedy algorithms | Minimum number of volunteers + the trip schedule (Gantt chart) | O(n log n) |
| 4 | **Count-Min Sketch + streaming heavy hitters** | Unit 6, randomized algorithms | Top donors / top NGOs in the impact analytics | update/query O(d), space O((1/ε)·ln(1/δ)) independent of the number of keys |

### 1.1 Min-cost maximum flow ("Smart" strategy)
Maximum flow tells us *how many* meals can be placed but not *which* maximum allocation is best. The same network is rebuilt with costs:

* `batch → NGO` cost = travel minutes + 2 × max(0, 30 − slack), where slack = minutes between arrival and the batch deadline (penalises risky, tight deliveries)
* `source → batch` cost = minutes-to-deadline ÷ 15 (capped at 720 min), so urgent batches are cheaper to drain and are used first, leaving long-lived batches for the next round

Successive shortest paths finds a cheapest augmenting path in the residual graph each time (reverse arcs carry negative cost, hence SPFA rather than Dijkstra) and pushes the bottleneck. Because every path is a cheapest one, the result is the minimum-cost flow of maximum value. **Verified** against an exhaustive search over all integer allocations on 400 random instances (test `MC-4`): same total as Edmonds-Karp and exactly the optimal cost.

Measured benefit (seeded synthetic instances, same meals placed): total travel meal-minutes reduced by **4.8% at 10×10, 43.5% at 25×25, 54.1% at 50×50 and 65.2% at 100×100**; tight-margin deliveries at 100×100 fell from 491 to 0. On the 8-batch / 7-NGO sample data the effect is small (avg travel 26.6 → 26.4 min) but leftover meals are fresher (150 → 167 min to expiry). Cost: min-cost is slower than a single Edmonds-Karp run (e.g. 40.5 ms vs 7.5 ms at 100×100).

### 1.2 Max-min fair allocation ("Fair share" strategy)
Raise a common level *t* for every NGO that can still grow; level *t* is feasible iff a max-flow with sink capacities min(cap, t) saturates all of them (monotone, so binary search). When no common raise is possible, integer ties are resolved by a greedy single-step raise (the raisable sets form a matroid), and the NGOs that cannot rise are frozen. Receipt vectors of such a network form a polymatroid, so the leximin-optimal vector is also a *maximum* one: **fairness costs 0 meals** (the test asserts `total == max flow`). **Verified** against exhaustive leximin on 300 random instances (test `FA-5`). While writing the test the first version of the algorithm failed on integer ties; the greedy single-step raise is the fix, and the test remains as the regression guard.

Measured: the smallest share among reachable NGOs rises from **0 to 31–38 meals** at every benchmark size; Jain's fairness index of fulfilment rises (e.g. 0.822 → 0.967 at 100×100). Cost: many max-flow calls (305.9 ms at 100×100).

### 1.3 Interval partitioning (volunteer scheduling)
Every allocated batch→NGO flow becomes one or more trips (at most *vehicle capacity* meals each, default 100). A trip occupies a volunteer from departure until return + 10 min hand-over. The greedy algorithm sorts trips by start and reuses the volunteer who frees earliest, else opens a new one. Optimality argument: the number of volunteers never exceeds the maximum number of simultaneously running trips (the *depth*), and depth is a lower bound for any schedule. Tests `IP-3` (500 random instances: no overlaps and volunteers = depth) and `SC-1`.
**Honest benchmark result:** the heap version is *not* faster than a naive linear first-fit scan here (e.g. 153.8 ms vs 32.8 ms for 100,000 trips) because the number of volunteers stays small (≤ 26). Both are optimal; the heap would only pay off for large fleets. This is reported as measured in the Benchmarks page.

### 1.4 Count-Min Sketch + heavy hitters
A d × w counter table (w = ⌈e/ε⌉, d = ⌈ln 1/δ⌉); update adds to one counter per row, estimate takes the row-minimum. Guarantees: never under-estimates, and over-estimates by at most ε·N with probability ≥ 1−δ. A bounded candidate map turns it into a streaming top-k. Used for the "top donors / NGOs" lists, which are cross-checked against exact counts on the page. Tests `CM-1..4` and `AN-1`.
Measured (1,000,000 events, 49,942 donors): sketch 108,760 bytes vs ≈5.2 MB exact map, top-10 identical, 100% of keys within the error bound, never under-estimates. **At 10,000 events the exact map is smaller than the sketch**; the page says so.

## 2. Three role-based dashboards

| Role | Demo login | Dashboard shows | Can do |
|---|---|---|---|
| **Coordinator (admin)** | `admin` / `admin123` | KPIs, prioritised "needs attention" alerts, delivery pipeline, weekly impact chart, Count-Min top donors/NGOs, pending approvals | Everything: manage data, run allocation with any strategy, publish the dispatch plan, track all deliveries, what-if insights, analytics, users & audit log, algorithm lab, benchmarks |
| **Donor** | `orchid` / `demo123` (also `xavier`, `spice`, `lakeview`, `sunrise`, `techpark`, `meridian`, `annapurna`) | Own batches with live countdown and status, meals offered / in transit / delivered / not placed, alerts (expiring, hand-over due, missing routes), impact card (lifetime meals, kg estimate, rank among donors, tier Starter→Platinum, NGO rating) | Add/edit/delete **own** batches only (locked once dispatched), confirm hand-over, report problems, notifications |
| **NGO** | `hope` / `demo123` (also `asha`, `rotibank`, `littlestars`, `seva`, `greenfield`, `streetkids`) | Incoming/received/planned meals, need covered, personalised suggestions, "how each allocation method affects me" chart, a table showing **why** each donor batch can or cannot reach the NGO | Edit own needs (required meals, pickup capacity, travel limit), confirm receipt with received count + 1–5 rating, report problems, notifications |

Accounts can also self-register (donor or NGO) and wait for admin approval; approving an NGO creates its record automatically.

### Security / access control
* PBKDF2-HMAC-SHA256 salted password hashing; random 256-bit bearer tokens, 8 h expiry; 5 failed logins lock an account for 60 s; the hash never leaves the server.
* **One** framework-free `AccessPolicy` (deny-by-role by default: any route not listed is admin-only) used by both the standalone server and the Spring interceptor; the front-end only hides menus, the server decides.
* Record-level ownership in the services: the donor name / NGO id always come from the authenticated account, never from the request body (tests `RB-5`, `RB-6`).
* Demo accounts share one demo password and one PBKDF2 salt to keep start-up fast; production should use ≥ 210,000 iterations or Argon2/bcrypt (the iteration count is a single constant).

## 3. Delivery life cycle (the part that makes the dashboards live)
`Allocation → Publish plan → DISPATCHED → PICKED_UP (donor) → DELIVERED (NGO, with received count + rating)` or `FAILED` (any party reports a problem with a reason).
* Publishing needs a fresh, non-stale result and cannot be done twice.
* Committed meals are subtracted from the next planning round (rolling horizon). A FAILED delivery releases its meals so they can be planned again (test `DP-2`).
* Every step creates in-app notifications for the people concerned and an audit-log entry.

## 4. Other additions
* **Strategy comparison** on every run: FCFS, max flow, min-cost and fair share on the *same* network, with meals, average travel, tight-margin meals, leftover freshness, smallest NGO share, Jain equity index and run time, plus a per-NGO chart "who receives what".
* **What-if insights**: re-runs max flow on modified copies of the data to rank single changes (+20 pickup capacity, +15 min travel) by exact meals gained, plus a free-form scenario tester. Example: +30 pickup capacity at Hope Shelter gains only +20 meals because supply, not capacity, becomes the limit.
* **Smart route inheritance**: a donor's new batch reuses the route times of its most recent earlier batch (the kitchen does not move).
* **Impact analytics** with 12 weekly buckets (84 days of *synthetic, clearly labelled* history plus live confirmations).
* **Audit log** and **notifications**.

## 5. Mapping to typical marking criteria

| Criterion | Evidence |
|---|---|
| Algorithmic depth / novelty | 6 algorithms in total (2 original + 4 new), 2 of them (min-cost flow, fair share) solving real weaknesses documented in the original README (fairness, freshness, route cost) |
| Correctness | 66 JUnit tests; the new algorithms are checked against **exhaustive search**, not just examples |
| Complexity analysis | per-algorithm above and in the technical report; measured in the Benchmarks page |
| Real-world completeness | three role dashboards, authentication, ownership, delivery life cycle, notifications, audit |
| Honest evaluation | negative results are reported (heap slower than linear scan; sketch larger than a map at small n; min-cost gain small on the tiny sample) |
| UI / usability | role-aware navigation, responsive layout, empty/error states, Gantt schedule, all numbers from the backend |

## 6. Known limitations (stated honestly)
* Data is in memory (as in the original); restarting the server resets everything including accounts created by registration.
* The Spring Boot layer was **type-checked against stubs only** in the authoring environment (Maven Central was unreachable); the standalone JDK server, which shares every service and the same `AccessPolicy`, was run end-to-end and all tests were executed with the JUnit console launcher. Run `mvn test` and the Spring app once on your machine.
* Tokens live in `localStorage` in the browser; there is no HTTPS, CSRF/refresh-token handling or password reset, which a production system would need.
* Notifications are in-app only. History data and the kg-per-meal factor (0.4) are labelled assumptions, not measurements.
* Interval partitioning assumes all trips of a plan start as early as possible; it minimises the volunteers needed, it does not trade delay for fewer volunteers.
