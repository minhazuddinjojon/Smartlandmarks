# Testing Report

## Status — read this first

This report has two halves, and it is important not to confuse them.

- **Part A** was executed. Automated static analysis over the full source tree.
- **Part B** was **not** executed. It is a device test plan that still needs running,
  because the generating environment had no Android SDK and no access to the
  Google/Maven repositories. `./gradlew assembleDebug` has never run against this code.

Anything below marked ☐ has not been verified by anyone yet.

---

## Part A — Static verification (executed, 0 errors)

A checker was written and run across all 53 Kotlin files, 43 XML files, and the Gradle
configuration.

| # | Check | Result |
|---|---|---|
| A1 | Kotlin `package` declaration matches directory for every file | ✅ 53/53 |
| A2 | Every `R.<type>.<name>` in Kotlin resolves to a declared resource | ✅ 0 unresolved |
| A3 | Every `@drawable/@string/@layout/@id/@color/@dimen/@style/@menu` reference in XML resolves | ✅ 0 unresolved |
| A4 | Every `binding.<id>` in a fragment exists in that fragment's layout | ✅ 0 mismatches |
| A5 | Every class named in `AndroidManifest.xml` exists on disk | ✅ |
| A6 | Every fragment class named in `nav_graph.xml` exists on disk | ✅ |
| A7 | Bottom-nav item ids match navigation destination ids | ✅ 4/4 |
| A8 | Every intra-project `import` resolves to a real declaration | ✅ 0 unresolved |
| A9 | All XML files are well-formed | ✅ 43/43 parse |
| A10 | Kotlin brace/paren nesting balances in every file | ✅ |
| A11 | No `TODO`, `FIXME`, stub, or placeholder code | ✅ 0 occurrences |
| A12 | All 6 API actions implemented **and** called | ✅ 6/6 |
| A13 | `FakeLandmarkRepository` overrides exactly the interface surface | ✅ 11/11 |
| A14 | Implementation matches `openapi.json` (see API_CONFORMANCE.md) | ✅ |

**What this does not cover.** Static analysis catches unresolved references and
structural mistakes. It does **not** catch type errors, KSP/Room/Hilt annotation-
processing failures, Gradle dependency resolution problems, or any runtime behaviour.

### Defects found and fixed during generation

| # | Defect | Fix |
|---|---|---|
| D1 | `fileContext()` placeholder threw `UnsupportedOperationException` in the multipart upload path — would have crashed every image upload | Removed; MIME derived from file extension, no `Context` needed |
| D2 | `minZoomLevel =` / `maxZoomLevel =` written as property assignments; osmdroid's getter returns primitive `double` while the setter takes boxed `Double`, so Kotlin synthesises no property | Explicit `setMinZoomLevel()` / `setMaxZoomLevel()` calls |
| D3 | `android.preference.PreferenceManager` (deprecated since API 29) | `getSharedPreferences("osmdroid", MODE_PRIVATE)` |
| D4 | Unit test asserted `"0.81 km"` for 812.35 m — wrong, that formats as metres | Corrected to `"812 m"`; kept as a regression test |
| D5 | Launcher icons only as `mipmap-anydpi-v26`, leaving API 24–25 with no icon | Unqualified `mipmap/` layer-list fallbacks added |

---

## Part B — Test plan (NOT YET EXECUTED)

Run these on a device or a Play-services emulator image before submitting.

### B1. Build and launch
- ☐ `./gradlew assembleDebug` completes with no errors
- ☐ `./gradlew test` — all unit tests pass
- ☐ `./gradlew lint` — no blocking issues
- ☐ App installs and launches; splash appears, then the Map tab
- ☐ Rotate on each tab — no crash, state survives

### B2. Navigation
- ☐ All four tabs reachable; icons and labels correct
- ☐ Bottom nav hidden on splash, visible everywhere else
- ☐ Back from the Map tab exits the app (splash is not on the back stack)

### B3. API — landmarks
- ☐ Map and list populate from `get_landmarks`
- ☐ Images load; missing images show the placeholder rather than a blank box
- ☐ Marker colours vary with score, and are not all identical
- ☐ Tapping a marker opens the detail sheet with matching data

### B4. Visit (the async flow — the critical path)
- ☐ "Record visit" prompts for location permission on first use
- ☐ Snackbar confirms acceptance **without** claiming a distance
- ☐ Activity tab shows the visit as **Processing** with a job id
- ☐ Within ~30s it becomes **Done** with a distance in metres/km
- ☐ UI never freezes during any of this
- ☐ Visiting a landmark id that does not exist → 404 handled, no crash

### B5. Sorting and filtering
- ☐ All four sort orders reorder the list correctly
- ☐ Score slider filters live; "showing N of M" updates
- ☐ Clear filter restores the full list
- ☐ Filtering to an empty result shows the *filtered* empty message

### B6. Add landmark
- ☐ "Use my location" fills latitude and longitude
- ☐ Photo Picker returns an image and the preview renders
- ☐ Blank title rejected; out-of-range coordinates rejected (try 233.7)
- ☐ Image over 2 MB rejected with a clear message
- ☐ Valid submit succeeds and the landmark appears after refresh
- ☐ **Confirm the upload is multipart** — a JSON body leaves `$_FILES` empty server-side

### B7. Delete and restore
- ☐ Delete asks for confirmation, then the landmark leaves the list
- ☐ It also disappears from the map
- ☐ App does not crash when the data set changes underneath it

### B8. Offline (mandatory requirement)
- ☐ Airplane mode → offline banner appears
- ☐ Cached landmarks still render on both map and list
- ☐ Visit while offline → appears as **Queued**
- ☐ Add landmark while offline → saved locally, no error
- ☐ Restore connectivity → queue drains automatically within ~30s
- ☐ Queued visit resolves **Queued → Processing → Done** in one connectivity restore
- ☐ Data still present after killing and reopening the app

### B9. WorkManager
- ☐ Queue a visit offline, force-stop the app, restore network, reopen — it syncs
- ☐ Reboot the device with a queued visit — the periodic worker picks it up
- ☐ No `Unable to instantiate worker` in Logcat (Hilt factory wiring)
- ☐ Failures back off rather than hammering the server

### B10. Error handling
- ☐ Set an invalid key → dialog naming the `gradle.properties` fix, not a raw 403
- ☐ Deny location permission → dialog offering app settings
- ☐ Disable device location → dialog offering location settings
- ☐ No unhandled exception reaches the user anywhere

### B11. UI quality
- ☐ Dark mode renders correctly on every screen
- ☐ Empty states appear where expected
- ☐ Text scales with the system font size setting
- ☐ TalkBack announces controls meaningfully

### B12. Devices
- ☐ API 24 (minSdk floor)
- ☐ API 33+ (notification permission path)
- ☐ One physical device, ideally a Xiaomi/Oppo/Huawei build, where background
      restrictions are most aggressive

---

## Unit tests included

`./gradlew test` — 7 files:

| File | Covers |
|---|---|
| `ScoreColorTest` | Ramp normalisation, including the degenerate all-equal-scores case |
| `FormattersTest` | Metres/kilometres boundary, null distance, score precision |
| `ImageUrlResolverTest` | Relative → absolute URLs, absolute pass-through, blank input |
| `ApiResultTest` | 403/404/400/500, IO failure, timeout, empty body — every documented code |
| `MapperTest` | The docs' own sample payload; dropped rows; defaults; `is_active` |
| `AddLandmarkViewModelTest` | Validation rules and form reset, via the fake repository |
| `FakeLandmarkRepository` | In-memory test double (no Room, no network) |
