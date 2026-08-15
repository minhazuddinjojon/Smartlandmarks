# Architecture — Smart Geo-Tagged Landmarks

## The constraint that shaped everything

`visit_landmark` does not return a distance. It returns a `job_id` and the word
`pending`. The distance exists only after the server processes the job, some seconds
later, and only if you ask for it via `get_job_status`.

So the app cannot do request → response → render. It has to:

1. record the user's intent durably,
2. tell them it was accepted,
3. resolve the answer in the background,
4. update the screen whenever that lands.

Step 4 is the interesting one. If the UI reads from the network, "update the screen
later" means plumbing a callback from a background worker into a live fragment that may
not exist any more. If the UI reads from a database, it means writing one row — and
every screen observing that table redraws itself. That is why Room is the single source
of truth here rather than a cache.

## Layers

```
UI (Fragment + ViewBinding)
     │ collects StateFlow
ViewModel ──────────► Repository ──┬──► ApiService (Retrofit)   ─┐
                                    └──► DAOs (Room)  ◄──────────┘ writes
                                              ▲
WorkManager ──► Repository ───────────────────┘   (UI reacts on its own)
```

| Package | Responsibility |
|---|---|
| `data/local` | Entities, DAOs, type converters, database |
| `data/remote` | Retrofit service, DTOs, `AuthInterceptor`, `ApiResult` error model |
| `data/repository` | The only class where network and database meet |
| `data/mapper` | DTO ↔ entity ↔ domain conversion; all defaulting lives here |
| `domain` | Framework-free models + repository interface |
| `ui/<screen>` | Fragment + ViewModel + adapter per screen |
| `workers` | `VisitSyncWorker`, `WorkScheduler` |
| `services` | `NetworkMonitor`, `LocationProvider` |
| `di` | Hilt modules |

The UI depends on `LandmarkRepository` (interface), never on `LandmarkRepositoryImpl`.
That is what lets ViewModel tests run against an in-memory fake with no Room and no
network — see `FakeLandmarkRepository`.

## Database

Three tables.

**`landmarks`** — cached server state. Soft-deleted rows are filtered in the DAO
(`WHERE is_active = 1`), not in the UI, so no screen can show one by forgetting a check.
`replaceAll` reconciles against the server list in a transaction; without that pass, a
landmark deleted by another client would linger locally forever.

**`visits`** — deliberately doing two jobs. It is both the Activity screen's history and
the offline visit queue:

| status | meaning | job_id | distance |
|---|---|---|---|
| `QUEUED` | recorded locally, not yet posted | – | – |
| `PENDING` | server accepted it, job running | ✓ | – |
| `DONE` | server resolved it | ✓ | ✓ |
| `FAILED` | rejected, or gave up | maybe | – |

These started as two separate tables. Merging them removed a class of consistency bugs
and, as a side effect, made queued visits *visible* — the user watches an offline visit
progress instead of it vanishing into an invisible outbox.

`landmark_title` is denormalised onto the row so history still reads correctly after the
landmark is deleted.

**`pending_creates`** — landmarks added offline. The picked image is copied into
app-private cache first: a `content://` grant does not survive until upload time, which
may be hours later and across a reboot.

## Background work

One worker, `VisitSyncWorker`, constrained to `NetworkType.CONNECTED`, exponential
backoff from 10s. Requirements 8 and 10 of the lab are the same underlying problem —
guaranteed work over an unreliable network that must survive process death — so one
mechanism serves both rather than two competing schedulers.

A single pass, in this order:

1. POST every `QUEUED` visit → store `job_id`, mark `PENDING`
2. Poll `get_job_status` for every `PENDING` visit → write distance, or fail it
3. Upload every pending landmark

**The order is load-bearing.** A visit posted in step 1 becomes pollable in step 2 of the
*same pass*, so an offline visit can resolve completely the moment connectivity returns
rather than needing two separate runs.

Return value drives retry: still-unresolved rows or a transient failure → `Result.retry()`
(backoff widens); `invalidKey` → `Result.failure()` immediately, because a bad key will
never succeed by retrying.

Scheduling: `enqueueSyncNow()` uses `APPEND_OR_REPLACE`, not `REPLACE` — replacing would
cancel a pass already in flight, potentially dropping a visit that had just been posted
but not yet recorded. A 15-minute periodic worker (`KEEP`) is re-armed on every cold
start; it is what actually delivers "survives process death and app restart", since no
in-process listener fires again after the app is killed.

Give-up rules: 40 poll passes per job, 10 attempts per queued upload. Without them one
permanently broken row retries forever.

## Error handling

`ApiError` is a sealed interface with one case per failure the app reacts to differently:

| Failure | Treatment |
|---|---|
| `Network` / `Timeout` | Not an error — queue and retry. Snackbar. |
| `InvalidKey` (403) | Fatal. Dialog naming the gradle.properties fix. Never retried. |
| `NotFound` (404) | Fails that one job or landmark, nothing else. |
| `BadRequest` (400) | Dialog. |
| `Server` (5xx) | Transient; retried with backoff. |
| `Parse` | Degrade to cached data. Never crashes. |

`safeApiCall` catches every exception, including `JsonSyntaxException`, so a corrupted
response is an ordinary result rather than a crash — the lab requires this explicitly.

The Success/Warning/Error split in `UiMessage` is decided in the ViewModel, where the
cause is known, so every screen presents the same class of problem the same way.

## Deliberate trade-offs

- **osmdroid over Google Maps** — no API key, no billing account; the project stays
  extract-and-run. Cost: no built-in marker clustering.
- **Sorting/filtering in memory, not SQL** — the score slider reacts instantly without
  re-querying on every drag. Fine at this data scale; would not be at 10,000 rows.
- **Gson with all-nullable DTOs** — Gson bypasses Kotlin constructors, so non-null
  properties are a lie waiting to throw. Nullable fields plus one mapper that applies
  defaults makes malformed responses degrade instead of crash.
- **`fallbackToDestructiveMigration`** — acceptable at version 1 for a cache. Queued work
  lives in the same tables, so a real release would need proper migrations.
