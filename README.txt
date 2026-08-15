===============================================================================
SMART GEO-TAGGED LANDMARKS
CSE 489: Mobile Application Development — Lab Exam (v5)
===============================================================================

Student ID / API key : 24241197
Platform             : Native Android (Kotlin only, no Java)
Min SDK              : 24        Target/Compile SDK : 35
Build system         : Gradle 8.9 (Kotlin DSL) + AGP 8.7.2 + version catalog


-------------------------------------------------------------------------------
1. PROJECT OVERVIEW
-------------------------------------------------------------------------------
An Android client for the faculty-provided Smart Landmarks REST API. It shows
landmarks on a map and in a list, records visits against the server's
asynchronous job queue, keeps working with no connectivity, and syncs whatever
it queued once the connection returns.

The design problem in this lab is not the UI — it is that visiting a landmark
is asynchronous. The server accepts a visit, hands back a job_id, and computes
the distance later. The app therefore cannot show a result inline; it has to
record the intent, poll in the background, and update the screen when the
answer arrives. That single constraint drove the architecture below.


-------------------------------------------------------------------------------
2. FEATURES IMPLEMENTED
-------------------------------------------------------------------------------
Map tab
  - All active landmarks as markers on an OpenStreetMap view
  - Map centred on Bangladesh (23.685 N, 90.356 E) at zoom 7
  - Marker colour encodes score on a red -> amber -> green ramp
  - Marker tap opens a detail sheet; FAB centres the map on your GPS position

Landmarks tab
  - List of every active landmark with image, title, and score badge
  - Sort by score (both directions), title, or visit count
  - Filter by minimum score with a live slider
  - Pull to refresh; result counter shows "showing N of M"

Activity tab
  - Visit history, newest first: landmark name, visit time, distance
  - Status badge for the whole visit lifecycle — Queued / Processing / Done /
    Failed — so an offline visit is visible rather than invisible
  - Job id shown once the server has accepted the visit
  - Banner counting visits still waiting to sync

Add tab
  - Title, latitude, longitude, optional image
  - "Use my location" pre-fills coordinates from GPS
  - System Photo Picker (no storage permission needed on any API level)
  - Client-side validation: required title, coordinate range checks, 2 MB image cap
  - Uploaded as multipart/form-data, as the server requires

Cross-cutting
  - Full offline support (see section 5)
  - Soft delete with confirmation; restore endpoint implemented
  - Material 3 theming with a complete dark mode
  - Every interactive control is at least 48x48dp, with content descriptions


-------------------------------------------------------------------------------
3. API USAGE
-------------------------------------------------------------------------------
Base URL : https://labs.anontech.info/cse489/exm3/
Endpoint : api.php (single script, routed by an `action` query parameter)

The student key is appended to every request by AuthInterceptor, so no call
site can forget it.

  ACTION             METHOD  CONTENT TYPE            IMPLEMENTED IN
  -----------------  ------  ----------------------  -------------------------
  get_landmarks      GET     -                       ApiService.getLandmarks
  create_landmark    POST    multipart/form-data     ApiService.createLandmark
  delete_landmark    POST    x-www-form-urlencoded   ApiService.deleteLandmark
  restore_landmark   POST    x-www-form-urlencoded   ApiService.restoreLandmark
  visit_landmark     POST    application/json        ApiService.visitLandmark
  get_job_status     GET     -                       ApiService.getJobStatus

All six endpoints are implemented and called. Note the three different content
types — this API is not internally consistent, and mixing them up is the most
common way to fail it. In particular create_landmark must be multipart: the
server reads the image through PHP's $_FILES, which is empty for a JSON body.

The visit flow, end to end:
  1. User taps "Record visit" -> app reads GPS
  2. A visit row is written to Room as QUEUED *before* any network call, so a
     process death mid-request cannot lose it
  3. POST visit_landmark -> server returns { job_id, status: "pending" }
     -> row becomes PENDING with that job_id
  4. WorkManager polls get_job_status on a backoff schedule
  5. status "done" -> distance written to Room -> Activity tab updates itself

Error mapping (ApiResult.kt):
  403 -> InvalidKey (fatal, never retried)   404 -> NotFound
  400 -> BadRequest                          5xx -> Server (retried)
  IOException -> Network (queue and retry)   bad JSON -> Parse (degrade, no crash)


-------------------------------------------------------------------------------
4. ARCHITECTURE USED
-------------------------------------------------------------------------------
MVVM + Repository, with Room as the single source of truth.

    UI (Fragment + ViewBinding)
        | observes StateFlow
    ViewModel  --calls-->  Repository
                              |--> ApiService (Retrofit)  writes to Room
                              '--> DAOs (Room)            read here only
    WorkManager --------------> Repository (writes to Room; UI reacts on its own)

The rule that makes everything else simple: **the UI never reads from the
network.** Every screen observes a Room-backed Flow. The network's only job is
to keep Room current. Offline support then falls out for free rather than
being a special case bolted on at the end.

Layers
  data/local      Room entities, DAOs, type converters
  data/remote     Retrofit service, DTOs, auth interceptor, error model
  data/repository Repository implementation (the only place both sides meet)
  data/mapper     DTO <-> entity <-> domain conversions
  domain          Framework-free models and the repository interface
  ui/*            One package per screen: Fragment + ViewModel + adapter
  workers         VisitSyncWorker + WorkScheduler
  services        NetworkMonitor, LocationProvider
  di              Hilt modules

Stack: Kotlin, Coroutines + Flow/StateFlow, Hilt, Room, Retrofit + OkHttp +
Gson, WorkManager, Navigation Component, Material 3, ViewBinding, Coil,
osmdroid, FusedLocationProvider.

Why osmdroid rather than Google Maps: it needs no API key and no billing
account, so the project is extract-and-run for anyone who opens it. The only
environment-specific value in the whole repository is the student key.


-------------------------------------------------------------------------------
5. OFFLINE STRATEGY
-------------------------------------------------------------------------------
Reads
  Every landmark fetched is cached in Room. Screens observe the cache, so the
  app renders identically online and offline; only the sync banner differs.
  Refresh reconciles the server's list against the cache in one transaction,
  so a landmark deleted elsewhere disappears locally instead of lingering.

Writes
  A visit is written to Room first, then posted. With no connection it simply
  stays QUEUED. Landmarks added offline go to a pending_creates table, with the
  chosen image copied into app-private cache — a content:// URI grant would not
  survive until upload time, possibly hours later and across a reboot.

Draining the queue
  One WorkManager worker (VisitSyncWorker) handles the whole thing, constrained
  to NetworkType.CONNECTED with exponential backoff from 10s:
     1. POST every QUEUED visit          (offline queue drain)
     2. Poll get_job_status for PENDING  (async job resolution)
     3. Upload pending landmarks
  The order matters: a visit posted in step 1 becomes pollable in step 2 of the
  same pass, so an offline visit can resolve completely the moment the network
  returns instead of needing two separate runs.

Surviving restarts
  A periodic worker (15 min, KEEP policy) is re-armed on every cold start. If
  the app is killed while visits are pending, no in-process listener will ever
  fire again — the periodic worker is what actually delivers the "survives
  process death and app restart" requirement. Nothing here uses a raw Thread,
  Timer, or Handler loop.

Give-up rules
  A job is abandoned after 40 poll passes; a queued upload after 10 attempts.
  Without these, one permanently broken row would retry forever.


-------------------------------------------------------------------------------
6. CHALLENGES FACED
-------------------------------------------------------------------------------
1. The async visit flow. The obvious implementation — call visit_landmark and
   show the distance — is wrong, because that response only contains a job_id.
   Restructuring around "record locally, resolve later" is what forced Room to
   become the source of truth rather than a cache bolted on afterwards.

2. Three content types on one endpoint. create_landmark needs multipart,
   delete/restore need form-urlencoded, visit_landmark needs raw JSON. Sending
   JSON to create_landmark silently produces an empty $_FILES server-side, and
   nothing in the response says why.

3. Offline queue vs visit history were the same table. They started as two.
   Merging them removed a whole class of consistency bugs and, as a side
   effect, made queued visits visible to the user instead of hidden in an
   invisible outbox.

4. Score ranges vary per student key, so a fixed colour scale collapsed every
   marker to one colour. The ramp is now stretched across the range actually
   present in the data, with a guard for the degenerate all-equal case.

5. Hilt + WorkManager needs two coordinated changes — a HiltWorkerFactory in
   the Application *and* removal of the default WorkManagerInitializer in the
   manifest. With only one half in place, every enqueue fails at runtime with
   no compile-time warning.

6. Gson bypasses Kotlin constructors, so a missing JSON field lands as null in
   a non-null property and throws somewhere unrelated later. Every DTO field is
   nullable, with defaults applied in one mapper — a malformed response now
   degrades instead of crashing, which the lab explicitly requires.


-------------------------------------------------------------------------------
7. BUILD AND RUN
-------------------------------------------------------------------------------
  1. Open the project root in Android Studio (Ladybug or newer)
  2. Let Gradle sync — it downloads all dependencies
  3. Run on a device or emulator with Google Play services (for location)

The API key is already set to 22301003 in gradle.properties:

      SMART_LANDMARKS_API_KEY=22301003

To use a different key, change that one line and rebuild. It is injected into
BuildConfig.API_KEY at build time; it appears nowhere else in the source. An
environment variable of the same name overrides it, for CI.

See Project_Documentation/INSTALLATION.md for detail and troubleshooting.


-------------------------------------------------------------------------------
8. WHAT IS NOT VERIFIED
-------------------------------------------------------------------------------
Stated plainly, because it matters for grading:

This project was generated in an environment with no Android SDK and no access
to the Google/Maven artifact repositories, so `./gradlew assembleDebug` was
NEVER EXECUTED. It has not been compiled, installed, or run against the live
API by its author.

What WAS verified, by automated static analysis over the whole source tree:
  - Every Kotlin package declaration matches its directory
  - Every R.* reference in Kotlin resolves to a declared resource
  - Every @drawable/@string/@layout/@id/@color/@dimen/@style reference in every
    XML file resolves
  - Every ViewBinding property used in a fragment exists in that fragment's layout
  - Every class named in the manifest and nav graph exists on disk
  - Bottom-navigation item ids match navigation destination ids
  - Every intra-project import resolves to a real declaration
  - All 43 XML files parse; all Kotlin brace/paren nesting balances
  - All 6 API endpoints are implemented AND called
  - No TODOs, stubs, or placeholder code anywhere

Run the build yourself before submitting. See TESTING_REPORT.md for the
device test plan that still needs to be executed.
