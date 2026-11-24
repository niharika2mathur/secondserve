# 8-minute demonstration script

**Setup (before the audience arrives):** backend on :8080, frontend on :5173 (see README). Open **Allocation Engine → Restore sample data** so the data are fresh (times are relative to now). Browser at http://localhost:5173. Numbers below are **real output** from the application on the sample data (planning time = now); minutes-left values will differ slightly.

| Time | Step | What to do / say |
|---|---|---|
| 0:00–0:45 | **1. The problem** | “Hotels and canteens have surplus meals that must move within a window; NGOs differ in demand, pickup capacity and travel tolerance. A greedy first-come-first-served hand-off strands food. SecondServe uses **Edmonds-Karp max flow** to place the maximum number of meals, and a **Bloom filter** to stop duplicate submissions.” Show the **Dashboard**: 770 meals, demand 770 (650 collectable), 8 donors, 7 NGOs, 2 batches near deadline, 70 meals already expired. “Allocated: *Not run*, nothing is faked.” |
| 0:45–1:45 | **2. Donor registration** | **Donors** page → *Add donation*: `BATCH-301`, “Demo Cafe”, 40 meals, defaults for times → accepted; toast says *Bloom filter: definitely new (no exact lookup needed)*. Try an empty form → all validation errors listed. Show Edit/Delete exist. |
| 1:45–2:15 | **3. NGO registration** | **NGOs** page: columns *required*, *pickup capacity*, *max travel*; note intake = min(required, pickup), e.g. N-01 requires 150 but can collect 120. |
| 2:15–3:15 | **4. Travel constraints** | **Travel & Feasibility**: matrix coloured by feasibility (15 of 56 routes feasible on the sample data). Hover: BATCH-105→N-03 *“Arrival … is after the safe delivery deadline”*; BATCH-101→N-03 *“Travel time 50 min exceeds the NGO limit of 45 min.”* Change one cell (e.g. BATCH-102→N-02 = 20), Save → count rises. Say deadlines are user-entered operational values, not food-safety claims. Then clear that cell/restore data if you need the original numbers. |
| 3:15–4:30 | **5. Run allocation** | **Allocation Engine** → *Run Allocation*. Read the pipeline: validated 8 batches/7 NGOs; screened 8 IDs, 0 duplicates; removed 1 expired batch, 15/56 feasible routes; **10 augmenting paths, 11 BFS passes**; **max flow 630 = min-cut 630**. |
| 4:30–5:30 | **6. Explain the flow network** | Point at the graph: Source → batches (capacity = meals) → NGOs (only feasible, dashed grey = unavailable, hover for reason) → Sink (capacity = intake). Open **Algorithm Lab → Edmonds-Karp**, jump to step 10: `Source → BATCH-102 → N-01 → ↩ BATCH-101 → N-02 → Sink`, +60. “BATCH-102 can only reach N-01, so the algorithm *pulled back* 60 meals from BATCH-101 and sent them to N-02 via the reverse edge. Greedy can’t do that.” |
| 5:30–6:15 | **7. Bottleneck & baseline** | **Results**: 630 redistributed, 140 left (70 expired + 70 unplaceable), efficiency 96.9 %. **Bottleneck panel** (min cut 630): BATCH-103 and BATCH-105 fully used; N-01, N-02, N-03, N-05, N-07 at intake limit. “More supply won’t help those NGOs; more pickup capacity would.” Baseline card: **FCFS 570 vs Edmonds-Karp 630 (+60)** on identical constraints. |
| 6:15–7:00 | **8. Bloom filter duplicates** | **Donors → Add donation** `batch-101` → **409**: *Bloom filter reported possibly present and the exact lookup confirmed the duplicate*. Dashboard shows *duplicates detected*. **Lab → Bloom filter**: bit array (9,586 bits, k = 7), orange probes for BATCH-101; sandbox with expected 50, p = 0.1, inserting 200: false positives climb – “a hit is only *possibly present*; we never reject without the exact check.” |
| 7:00–7:35 | **9. Benchmarks** | **Benchmarks** → Run (repetitions 3). Saved 15-run results (docs/benchmark-results.md): EK vs FCFS 25×25: **1766 vs 1581 meals (+11.7 %)**; EK slower per run (0.19 ms vs 0.014 ms) – honest trade-off. Bloom: observed FP 1.00 % vs 1 % theory, 0 false negatives, **11,984 bytes vs ≈945,616** for 10,000 IDs. |
| 7:35–8:00 | **10. Edge case** | **Allocation Engine → Clear all data → Run Allocation**: red box with two errors (*No donation batches…*, *No recipient NGOs…*) — no silent empty result. Restore sample data. (Alternative: delete all travel times → max flow 0 with “no travel time entered” reasons.) |

## Example input (CSV import) and actual output
Input (`sample-data/donations.csv`):
```
BATCH-201,Blue Lagoon Cafe,45,-10,+150,Lunch buffet
BATCH-101,Duplicate Hotel Entry,60,-10,+120,Accidental re-submission
BATCH-202,Metro Food Court,35,0,+90,Packed rice meals
BATCH-203,Broken Row,abc,-5,+60,Invalid meal count
```
Observed output (UI import on a freshly reset dataset): **2 imported (BATCH-201, BATCH-202), 1 duplicate rejected (BATCH-101), 1 invalid (BATCH-203: “Number of meal packs must be a number”)**.

## Actual allocation output (sample data)
Summary: supply 770 (usable 700), demand 770 (collectable 650), **allocated 630**, surplus left 140, remaining demand 140, efficiency 96.9 %, 10 augmentations, 11 BFS passes, FCFS baseline **570**.

| Donation batch | Donor | NGO | Meals | Status |
|---|---|---|---|---|
| BATCH-101 | Grand Orchid Hotel | N-01 | 60 | Allocated |
| BATCH-101 | Grand Orchid Hotel | N-02 | 60 | Allocated |
| BATCH-102 | St. Xavier's College Canteen | N-01 | 60 | Allocated |
| BATCH-102 | St. Xavier's College Canteen | – | 20 | Unallocated (every reachable NGO is at intake capacity) |
| BATCH-103 | Spice Route Restaurant | N-04 | 60 | Allocated |
| BATCH-104 | Lakeview Convention Centre | N-03 | 150 | Allocated |
| BATCH-104 | Lakeview Convention Centre | N-05 | 90 | Allocated |
| BATCH-104 | Lakeview Convention Centre | N-07 | 20 | Allocated |
| BATCH-105 | Sunrise Bakery & Cafe | N-04 | 10 | Allocated |
| BATCH-105 | Sunrise Bakery & Cafe | N-06 | 40 | Allocated |
| BATCH-106 | TechPark Cafeteria | N-07 | 40 | Allocated |
| BATCH-106 | TechPark Cafeteria | – | 50 | Unallocated (NGO-side bottleneck) |
| BATCH-107 | Hotel Meridian | – | 70 | Expired |
| BATCH-108 | Annapurna Mess | N-02 | 40 | Allocated |

(The exact split between N-01/N-02 may differ if you change data, but the total, the min cut and the validity are what matter: another maximum flow of 630 is equally correct.)

Min cut (capacity 630): supply used up — BATCH-103, BATCH-105; NGO intake reached — N-01 (120), N-02 (100), N-03 (150), N-05 (90), N-07 (60). NGO utilisation of pickup capacity: N-01, N-02, N-03, N-04, N-05, N-07 = 100 %; N-06 = 66.7 % (40 of 60; supply-limited because only BATCH-103/105 can reach it).

## Viva prompts (each student should be able to answer)
1. Why does BFS (not DFS) give a polynomial bound? 2. Walk through augmentation 10 on the sample data. 3. Why are the middle edges unbounded? 4. Prove flow = min cut at termination. 5. How is m, k chosen and what is the false-positive probability? 6. Why must a Bloom hit be verified? 7. Why does the filter rebuild on delete? 8. Why can FCFS be worse and when is it equal? 9. Show `EdmondsKarp.solve` and explain each line.


---
# Multi-role demonstration (about 6 minutes)
1. **Login page** – show the three demo accounts; mention PBKDF2 hashing and that the server, not the menu, enforces roles.
2. **Coordinator** (`admin` / `admin123`) – dashboard alerts ("BATCH-105 expires in 34 min"), then **Allocation Engine**: pick *Smart (min-cost)*, run. Open **Results & Plan**: point at the *methods compared* table (same 630 meals; Smart has fresher leftovers, Fair raises the smallest NGO share from 40 to 50, FCFS only 570), the per-NGO chart and the **volunteer Gantt** (10 volunteers = peak overlap; V1 is reused for two sequential trips). Click **Publish dispatch plan**.
3. **Donor** (`orchid` / `demo123`) – dashboard shows the batch "In transit", impact tier and rank; **Deliveries → Mark picked up**. Try **My batches → add a batch**: routes are inherited from the previous batch; a duplicate ID is rejected (Bloom filter + exact check).
4. **NGO** (`hope` / `demo123`) – dashboard: incoming meals, the "why can't donor X reach me" table, the chart of what each method would give this NGO; **Deliveries → Confirm receipt** with a rating.
5. Back as **admin** – the pipeline shows Delivered; **Impact Analytics** shows the Count-Min top donors vs exact counts; **What-if insights** shows "+20 pickup capacity at X → +20 meals"; **Users & Audit** shows every action.
6. **Benchmarks** – min-cost travel saving, fairness gain, Count-Min memory at 1,000,000 events, and the honest result that the heap is slower than a linear scan here.

Viva prompts for the new parts: Why SPFA instead of Dijkstra in successive shortest paths (negative reverse-arc costs)? Why does fairness cost 0 meals (polymatroid)? Why is interval partitioning optimal (depth lower bound)? What does Count-Min guarantee and when is an exact map better? Why is the access check on the server and not in the menu?
