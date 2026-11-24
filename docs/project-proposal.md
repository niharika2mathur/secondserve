# CIA 3 Project Proposal — SecondServe

**Course:** Advanced Algorithm · Semester V · B.Tech CS AI & ML · ATLAS – uGDX · 2026-27
**Title:** SecondServe — An Algorithm-Driven Food Rescue and Surplus Meal Allocation System
**Team:** *[Student name 1 — ID]*, *[Student name 2 — ID, if any]*, *[Student name 3 — ID, if any]*
**Submission mode:** *[Group of 2–3 / Individual]* — **if individual, explicit faculty approval is required under the CIA 3 guidelines and must be obtained before submission** *(approval reference: [to be filled])*.

## 1. Problem statement
Restaurants, hotels, college canteens and event organisers regularly end a service with surplus prepared meals that stay distributable only for a limited window. NGOs and community kitchens need food, but each has a different demand, a limited pickup capacity and a limit on acceptable travel time. Matching donors to recipients by phone or messages is slow and error-prone: early assignments use up the capacity that a later donation could only reach through that NGO, batches are submitted twice, and nobody can see *why* part of the food went unplaced.

## 2. Target users
- **Food-rescue coordinators** at a city NGO network or a campus sustainability cell (primary user, runs the allocation).
- **Donor staff** (hotel/canteen managers) who register surplus batches.
- **NGO volunteers** who need a clear list of what they will receive.

## 3. Proposed application
A web application whose central feature is an allocation engine. For a chosen planning time it determines the **maximum number of meal packs that can feasibly be redistributed**, the donor-batch → NGO quantities, the unallocatable remainder with reasons, and the **capacity bottleneck** (minimum cut). Scope is intentionally focused: one food category (prepared meal packs), one planning window, manually entered travel times, realistic synthetic data, no external APIs.

## 4. Main features
Operations dashboard · donor management (CRUD, CSV import) · NGO management · travel-time and feasibility configuration with explicit reasons · allocation engine with flow-network visualisation · results and bottleneck view · Algorithm Lab (step-by-step Edmonds-Karp, Bloom bit array) · benchmark page running real experiments.

## 5. Core algorithms and syllabus mapping
| # | Technique | Syllabus unit | Why it is appropriate |
|---|---|---|---|
| 1 | **Edmonds-Karp maximum flow + max-flow/min-cut** | **Unit 5** – Network Flows and Approximation Algorithms | Allocation under supply, intake-capacity and feasibility constraints is exactly a capacitated flow problem. Integer capacities (meal packs are indivisible) give integral flows; BFS augmentation gives a polynomial O(V·E²) bound; the min cut identifies the bottleneck. |
| 2 | **Bloom filter** (probabilistic data structure) | **Unit 6** – Randomized Algorithms (**CO5**) | Duplicate batch-ID screening is a membership test where most queries are negatives. A Bloom filter answers “definitely new” with no lookup and “possibly present” with a controllable false-positive rate; positives are verified exactly, so no genuine donation is ever rejected on a false positive. |

Both are implemented by us in Java; no flow or Bloom library is used. A First-Come-First-Served greedy allocator is the comparison baseline.

## 6. Data source
Realistic **synthetic** data (8 donor batches, 7 NGOs, travel-time matrix) plus user-entered records and CSV import. Seeded generators produce benchmark inputs at 10/25/50/100 donors × NGOs and 100/1,000/10,000 identifiers. No personal data is used.

## 7. Measurable success criteria
1. Edmonds-Karp allocation is always valid (never exceeds supply, NGO capacity or feasibility) and never allocates fewer meals than FCFS on identical inputs; max flow equals min-cut capacity on every run.
2. At least 5 documented correctness tests per core algorithm including boundary cases (target: 10 + 8 delivered).
3. Benchmarks at ≥3 increasing sizes per algorithm with fixed seeds; Bloom observed false-positive rate within ±0.5 percentage point of the 1 % target; zero false negatives.
4. No genuine duplicate batch is accepted and no non-duplicate is rejected.
5. Complete workflow (input → configure → run → interpret) usable in an 8-minute demonstration, with graceful handling of empty data, invalid input, duplicates and infeasible routes.

## 8. Technical stack
Java 17 · Spring Boot 3.2 · Maven · JUnit 5 · React 18 · Vite · Recharts · SVG. In-memory repositories (a database is optional under the guidelines).

## 9. Timeline (per guideline checkpoints)
Week 11 group + proposal · Week 12 design review (architecture, API, pseudocode) · Week 13 working prototype · Week 14 validation + benchmarking · Week 15 final review, demo and viva.
