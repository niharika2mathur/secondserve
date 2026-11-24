# SecondServe

**An Algorithm-Driven Food Rescue and Surplus Meal Allocation System** — *Saving food through intelligent resource allocation.*

CIA 3 project for **Advanced Algorithm** (Semester V, B.Tech CS AI & ML, ATLAS – uGDX 2026-27).

Restaurants, hotels, canteens and event organisers have surplus prepared meals with a short delivery window; NGOs have limited demand, pickup capacity and travel tolerance. SecondServe decides **how many meal packs can feasibly be redistributed, which donor batch goes to which NGO, what stays unallocated, and where the capacity bottleneck is**, using algorithms implemented from scratch in Java, behind **three role-based dashboards (coordinator, donor, NGO)**.

| Algorithm | Syllabus | Role |
|---|---|---|
| **Edmonds-Karp maximum flow** (+ minimum cut) | Unit 5 – Network Flows | Allocation engine: Source → donor batches → feasible NGOs → Sink |
| **Min-cost maximum flow** (successive shortest paths, SPFA) | Unit 5 – Network Flows | "Smart" strategy: same meals, least travel, safest margins, urgent batches first |
| **Max-min fair allocation** (progressive filling over repeated max-flow) | Unit 5 – Flows / fairness | "Fair share" strategy: lifts the smallest NGO share at zero cost in meals |
| **Interval partitioning** (greedy + min-heap) | Greedy algorithms | Minimum number of volunteers and the trip schedule |
| **Bloom filter** | Unit 6 – Randomized Algorithms (**CO5**) | Duplicate donation-batch screening, always followed by an exact check |
| **Count-Min Sketch + heavy hitters** | Unit 6 – Randomized Algorithms | Top donors / NGOs from a stream of deliveries |

A First-Come-First-Served greedy allocator is included as the baseline. **See [docs/new-features.md](docs/new-features.md) for everything added in the multi-role version.**

### Demo accounts
| Role | Username / password | Example |
|---|---|---|
| Coordinator | `admin` / `admin123` | full access |
| Donor | `orchid` / `demo123` | Grand Orchid Hotel (also `xavier`, `spice`, `lakeview`, `sunrise`, `techpark`, `meridian`, `annapurna`) |
| NGO | `hope` / `demo123` | Hope Shelter Home (also `asha`, `rotibank`, `littlestars`, `seva`, `greenfield`, `streetkids`) |

Quick demo: sign in as `admin` → *Run smart allocation* → *Results & Plan* → *Publish dispatch plan*; then sign in as `orchid` (mark picked up) and `hope` (confirm receipt, rate) and watch the dashboards update.

---

## Contents
1. [Features](#features) · 2. [Architecture](#architecture) · 3. [Algorithms](#algorithms) · 4. [Prerequisites](#prerequisites) · 5. [Run the backend](#run-the-backend) · 6. [Run the frontend](#run-the-frontend) · 7. [Tests](#tests) · 8. [Benchmarks](#benchmarks) · 9. [API examples](#api-examples) · 10. [Sample data](#sample-data) · 11. [Configuration](#configuration) · 12. [Troubleshooting](#troubleshooting) · 13. [Repository layout](#repository-layout)

## Features
**New in the multi-role version** (details in [docs/new-features.md](docs/new-features.md)):
- **Login, registration with admin approval, role-based access control** (server-enforced; donors/NGOs only ever touch their own records).
- **Coordinator dashboard** – prioritised alerts, delivery pipeline, impact chart, Count-Min top donors/NGOs, publish-plan workflow, users & audit log.
- **Donor dashboard** – own batches with countdown and status, hand-over confirmation, impact card (rank, tier, kg estimate, ratings).
- **NGO dashboard** – incoming / received meals, "why can't donor X reach me?", how each allocation method affects the NGO, editable needs, receipt confirmation with rating.
- **Allocation strategies** – Maximum flow, **Smart (min-cost)**, **Fair share (max-min)**, compared side by side on every run.
- **Volunteer schedule** – minimum volunteers by interval partitioning, shown as a Gantt chart.
- **Delivery life cycle** – publish → picked up → delivered/failed, with notifications; committed meals leave the next planning round.
- **What-if insights** – exact meals gained by "+20 pickup capacity" / "+15 min travel" / free-form scenarios.

**Original features**
- **Dashboard** – live totals (available meals, NGO demand, allocated, unallocated surplus, donors, NGOs, deadlines nearing, duplicates detected) and charts. All values come from the backend.
- **Donors** – add / edit / delete batches, CSV import, duplicate batch-ID detection with Bloom filter + exact verification.
- **NGOs** – add / edit / delete recipients with required meals, pickup capacity and travel limit.
- **Travel & Feasibility** – editable donor × NGO travel-time matrix; every infeasible pair shows its reason (expired, no travel time, NGO limit, arrival after deadline).
- **Allocation Engine** – choose a planning time, run Edmonds-Karp, see the pipeline and an SVG flow network (flow, capacity, bottleneck and unavailable edges).
- **Results** – allocation table, NGO pickup utilisation, remaining surplus/demand, reasons for unallocated meals, min-cut bottleneck, baseline comparison.
- **Algorithm Lab** – step through each augmenting path, residual graph, min cut, complexity; live Bloom bit array, membership queries, false-positive sandbox.
- **Benchmarks** – real seeded experiments (Edmonds-Karp vs FCFS, Bloom vs exact HashSet) with charts.

Deliberately out of scope: payments, GPS/maps APIs, chatbots, external services. Travel times are entered manually; safe-delivery deadlines are user-supplied operational values (the app makes **no** food-safety claims).

## Architecture

```
┌────────────────────────┐   /api (JSON)   ┌──────────────────────────────────────────────┐
│ React + Vite (SPA)      │ ───────────────▶│ Spring Boot REST controllers (web/)          │
│ Recharts, SVG network   │ ◀─────────────── │  DonationController NgoController            │
└────────────────────────┘                  │  TravelController AllocationController       │
                                            │  AlgorithmController DashboardController     │
                                            │  GlobalExceptionHandler (400/404/409/500)    │
                                            ├──────────────────────────────────────────────┤
                                            │ Services (plain Java, framework-free)        │
                                            │  Donation/Ngo/Travel/Feasibility/Allocation  │
                                            │  DuplicateScreen Bloom Benchmark Dashboard   │
                                            ├──────────────────────────────────────────────┤
                                            │ Algorithms (self-implemented)                │
                                            │  FlowNetwork EdmondsKarp AllocationFlowModel │
                                            │  BloomFilter FcfsAllocator                   │
                                            ├──────────────────────────────────────────────┤
                                            │ In-memory repositories + synthetic sample data│
                                            └──────────────────────────────────────────────┘
```
All assessed algorithms execute on the Java server. The frontend only renders what the API returns; it contains no allocation or hashing logic.

## Algorithms

**Edmonds-Karp.** Network: `Source → batch` (capacity = meal packs), `batch → NGO` (only if feasible; unbounded), `NGO → Sink` (capacity = min(required meals, pickup capacity)). BFS finds the shortest augmenting path in the residual graph, the bottleneck is pushed, reverse residual edges allow earlier choices to be undone. Time O(V·E²), space O(V+E). The final reachable set gives the minimum cut, shown as the bottleneck. Max flow maximises total meals placed; it does **not** by itself guarantee fairness, freshness priority or route optimality.

**Bloom filter.** Bit array of m = ⌈−n ln p /(ln 2)²⌉ bits and k = round(m/n · ln 2) hash functions via double hashing (FNV-1a + SplitMix64). A “possibly present” answer is *never* a rejection on its own: the authoritative repository is queried; if the record does not exist the submission is accepted and counted as a false positive.

## Prerequisites
| Tool | Version | Notes |
|---|---|---|
| JDK | 17 or newer (tested with 21) | `java -version`, `javac -version` |
| Maven | 3.8+ | for the Spring Boot backend (needs access to Maven Central) |
| Node.js | 18+ (tested with 22) | for the frontend |

No API keys, secrets, database or environment variables are required.

## Run the backend

### Option A – Spring Boot (primary)
**Windows PowerShell**
```powershell
cd secondserve\backend
mvn spring-boot:run
```
**macOS / Linux / Git Bash**
```bash
cd secondserve/backend
mvn spring-boot:run
```
The API is served on **http://localhost:8080**. Sample data is loaded automatically.

### Option B – dependency-free fallback (JDK only, no Maven)
Exposes the identical REST API from the same service classes using the JDK's built-in HTTP server. Useful if Maven Central is blocked.
```powershell
# PowerShell
cd secondserve
.\scripts\standalone.ps1          # optional: -Port 9090
```
```bash
# bash
./scripts/standalone.sh           # optional: ./scripts/standalone.sh 9090
```

## Run the frontend
```powershell
cd secondserve\frontend
npm install
npm run dev
```
(same commands in any terminal). Open **http://localhost:5173**. The dev server proxies `/api` to `http://localhost:8080`; to use another backend address set `BACKEND_URL` first (`$env:BACKEND_URL="http://localhost:9090"` in PowerShell, `BACKEND_URL=... npm run dev` in bash).

Production build: `npm run build` (output in `frontend/dist`).

## Tests
```powershell
cd secondserve\backend
mvn test
```
66 JUnit 5 tests: 10 Edmonds-Karp, 8 Bloom filter, 7 feasibility, 11 service/end-to-end, plus 30 for the multi-role version (min-cost flow and fair share verified against exhaustive search, interval partitioning, Count-Min Sketch, login/lockout/hashing, access policy, ownership, dispatch life cycle, registration, insights, analytics, dashboards). Descriptions and results: [docs/test-report.md](docs/test-report.md). The tests need only the JDK classes plus JUnit, so they can also be run with the JUnit console launcher if Maven is unavailable.

## Benchmarks
Either click **Run benchmarks** in the UI, or from the command line (writes `docs/benchmark-results.json|csv|md`):
```powershell
.\scripts\benchmark.ps1 -Seed 2026 -Repetitions 15
```
```bash
./scripts/benchmark.sh 2026 15
```
Sizes: Edmonds-Karp vs FCFS at 10×10, 25×25, 50×50, 100×100; Bloom vs HashSet at 100 / 1,000 / 10,000 identifiers. Fixed seeds make the *results* (meals, false positives) reproducible; timings depend on the machine.

## API examples
PowerShell:
```powershell
$t = (Invoke-RestMethod -Method Post http://localhost:8080/api/auth/login -ContentType application/json -Body '{"username":"admin","password":"admin123"}').token
$h = @{ Authorization = "Bearer $t" }
Invoke-RestMethod http://localhost:8080/api/dashboard -Headers $h
Invoke-RestMethod -Method Post http://localhost:8080/api/allocation/run -Headers $h -ContentType application/json `
  -Body (@{ planningTime = (Get-Date).ToString("yyyy-MM-ddTHH:mm"); strategy = "MIN_COST" } | ConvertTo-Json)
```
curl:
```bash
# every /api route except login/register needs a token:
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/donations
curl -X POST localhost:8080/api/donations -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"batchId":"BATCH-301","donorName":"Demo Cafe","meals":40,"availableFrom":"2026-11-18T10:00","safeUntil":"2026-11-18T15:00"}'
curl -X POST localhost:8080/api/allocation/run -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"planningTime":"2026-11-18T12:00","strategy":"MIN_COST"}'
curl -X POST localhost:8080/api/algorithms/bloom-check -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"id":"BATCH-101"}'
```
| Method & route | Purpose |
|---|---|
| `POST /api/auth/login`, `/register`, `/logout`; `GET /api/auth/me` | sign in (Bearer token), self-register (needs approval), sign out, current user |
| `POST /api/allocation/run` `{planningTime, strategy: MAX_FLOW\|MIN_COST\|FAIR, vehicleCapacity}` | allocation with the chosen strategy; response includes `comparison`, `perNgo`, `schedule` |
| `POST /api/dispatch/commit` | publish the latest allocation as deliveries (admin) |
| `GET /api/dashboard/admin` · `/donor` · `/ngo` | role dashboards (each role may call only its own) |
| `GET/POST/PUT/DELETE /api/my/donations` · `GET/PUT /api/my/ngo` · `GET /api/my/deliveries` | donor / NGO self-service (ownership enforced) |
| `POST /api/deliveries/{id}/handover` · `/confirm` · `/report` | donor hand-over, NGO receipt + rating, failure report |
| `GET /api/deliveries` · `/api/analytics` · `/api/admin/users` · `/api/admin/audit` | admin overview, analytics (Count-Min), accounts, audit log |
| `POST /api/admin/users/{id}/approve\|reject\|disable\|enable` | account administration |
| `GET /api/insights/recommendations?planningTime=` · `POST /api/insights/what-if` | exact "meals gained" sensitivity analysis |
| `GET /api/notifications` · `POST /api/notifications/read-all` | in-app notifications |
| `GET /api/dashboard` | live statistics and chart data |
| `GET/POST /api/donations`, `PUT/DELETE /api/donations/{id}` | donation CRUD (POST screens the ID with the Bloom filter) |
| `POST /api/donations/import-csv` | body `{"csv": "..."}` |
| `GET/POST /api/ngos`, `PUT/DELETE /api/ngos/{id}` | NGO CRUD |
| `GET/PUT /api/travel` | travel-time matrix (`{"entries":[{"donationId","ngoId","minutes"}]}`) |
| `GET /api/feasibility?planningTime=...` | feasibility of every donor-NGO pair with reasons |
| `POST /api/allocation/run` | run Edmonds-Karp (`{"planningTime":"yyyy-MM-ddTHH:mm"}`) |
| `GET /api/allocation/results` | last result (flagged `stale` if data changed since) |
| `GET /api/algorithms/network-analysis` | augmenting-path trace, residual data, min cut |
| `GET /api/algorithms/bloom-stats` | bit array, statistics, screening log |
| `POST /api/algorithms/bloom-check` / `bloom-demo` | read-only membership query / false-positive sandbox |
| `POST /api/algorithms/benchmarks`, `GET .../benchmarks/latest` | run / fetch benchmarks |
| `POST /api/data/reset` | `{"mode":"sample"}` or `{"mode":"empty"}` |

Errors share one shape: `{"status":400,"error":"Bad Request","message":"...","details":["..."]}` with 400 (validation), 404, 409 (duplicate/conflict) or 500.

## Sample data
Built in at startup with times **relative to now** (so the demo is always inside its window): 8 batches (one deliberately expired), 7 NGOs, 15 feasible routes out of 56 pairs. See [sample-data/sample-dataset.md](sample-data/sample-dataset.md). [sample-data/donations.csv](sample-data/donations.csv) is a CSV import example containing a duplicate and a malformed row (`+N` / `-N` mean minutes from now).

## Configuration
`backend/src/main/resources/application.properties`: `server.port`, `secondserve.bloom.expected-insertions` (1000), `secondserve.bloom.false-positive-rate` (0.01), `secondserve.load-sample-data` (true).

## Troubleshooting
| Symptom | Fix |
|---|---|
| UI shows “Cannot reach the Java backend on port 8080” | Start the backend first; check nothing else uses port 8080. |
| `mvn` cannot download dependencies | Check network/proxy access to Maven Central, or use backend Option B. |
| `Release version 17 not supported` | Use JDK 17+; check `JAVA_HOME`. |
| Everything shows as expired / zero feasible routes | Data is relative to start time; use **Allocation Engine → Restore sample data**. |
| PowerShell blocks scripts | `Set-ExecutionPolicy -Scope Process Bypass`, then re-run. |
| Port 5173 busy | `npm run dev -- --port 5174`. |
| Data disappears after restart | By design: in-memory repositories; sample data reloads. |

## Repository layout
```
secondserve/
├── backend/            Spring Boot + Maven (pom.xml)
│   └── src/main/java/com/secondserve/{algorithm,service,repository,model,dto,web,exception,util,benchmark,standalone,config}
│   └── src/test/java/com/secondserve/{algorithm,service}
├── frontend/           React + Vite (src/pages incl. AdminHome/DonorHome/NgoHome, auth.jsx, api.js, NetworkGraph.jsx)
├── docs/               proposal, technical report, demo script, test report, benchmark results
├── sample-data/        sample CSV + dataset description
└── scripts/            standalone backend and benchmark launchers (.sh / .ps1)
```

## Screenshots
`docs/screenshots/` — original screens (`1_`–`11_`) and the multi-role version (`m1_`–`m10_`: login, coordinator/donor/NGO dashboards, strategy comparison, volunteer schedule, what-if, analytics, users & audit, mobile).

## Verification status (be transparent in your viva)
- Algorithms, services and 66 JUnit 5 tests: compiled and run (all pass) with the JDK + JUnit console launcher.
- Frontend: built and exercised end-to-end with headless Chromium against the Java backend.
- **Spring Boot layer (`web/`, `config/`, `pom.xml`)**: written to the Spring Boot 3.2.5 API but **not compiled or run by Maven in the authoring environment** (Maven Central was unreachable); run `mvn test` and `mvn spring-boot:run` once on your machine. The controllers (including the new `AuthController`, `PortalController`, `AdminController` and `AuthInterceptor`) delegate to the same services and the same `AccessPolicy` the standalone fallback server uses, which is what was verified over HTTP; they were additionally type-checked against minimal Spring/Jakarta stubs.
- Multi-role UI: driven end-to-end with headless Chromium as admin, donor and NGO (login, allocation, publish, hand-over, confirm, rating, registration), including a phone-width layout check.
