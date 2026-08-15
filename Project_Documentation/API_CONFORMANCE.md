# API Conformance

Every claim here was checked against the machine-readable spec in
[`openapi.json`](./openapi.json) (OpenAPI 3.0.3, `CSE 489 — Smart Geo-Tagged Landmarks
API`, version **5.0.0**), not against prose. Where the spec and my initial reading of the
lab PDF could have diverged, the spec won.

## Authentication

```json
"StudentKey": { "type": "apiKey", "in": "query", "name": "key" }
```

Applied globally via top-level `security`. The app satisfies this in `AuthInterceptor`,
which appends `key` to the URL of every outgoing request — so no call site can omit it,
and no `ApiService` method declares it.

Server: `https://labs.anontech.info/cse489/exm3` → `BuildConfig.BASE_URL` (trailing
slash added so Retrofit resolves the relative `api.php` path correctly).

## Endpoint conformance

| Spec operation | Content type | Implementation | ✓ |
|---|---|---|---|
| `GET ?action=get_landmarks` | – | `ApiService.getLandmarks` | ✓ |
| `POST ?action=create_landmark` | `multipart/form-data` | `createLandmark` — `@Multipart` | ✓ |
| `POST ?action=delete_landmark` | `x-www-form-urlencoded` | `deleteLandmark` — `@FormUrlEncoded` | ✓ |
| `POST ?action=restore_landmark` | `x-www-form-urlencoded` | `restoreLandmark` — `@FormUrlEncoded` | ✓ |
| `POST ?action=visit_landmark` | `application/json` | `visitLandmark` — `@Body` | ✓ |
| `GET ?action=get_job_status` | – | `getJobStatus` — `job_id` query, required, integer | ✓ |

All six are implemented **and** called from `LandmarkRepositoryImpl`; verified
programmatically, not by eye.

## Schema conformance

**`Landmark`** → `LandmarkDto` maps all nine properties one-to-one: `id`, `title`, `lat`,
`lon`, `image`, `is_active`, `visit_count`, `avg_distance`, `score`.

Two details from the spec that the code depends on:

- `image` is documented as *"Relative path under the server; prefix with the server
  base"*. Handled by `ImageUrlResolver`, which also passes absolute URLs through
  unchanged.
- `is_active` is constrained to `enum: [0, 1]`. Mapped to a Kotlin `Boolean`; an **absent**
  value defaults to active, since `get_landmarks` only returns active rows anyway.

**`distance` is in metres.** The spec states this explicitly (*"Distance in meters
between the supplied user location and the landmark"*). `Formatters.distance` therefore
renders `< 1000` as metres and above that as kilometres — and there is a unit test
pinning the docs' own sample value, `812.35` → `"812 m"`.

**`create_landmark` returns `id` as a JSON *string***, not a number (`"example": "11"`).
`CreateLandmarkDto.id` is declared `String?` and converted with `toIntOrNull()`. Declaring
it as an `Int` would have thrown at parse time on every successful create.

## Job status — the `oneOf`

The spec models the 200 response as `oneOf` three shapes:

| Schema | `status` | Extra field |
|---|---|---|
| `VisitJobPending` | `"pending"` | – |
| `VisitJobDone` | `"done"` | `distance` (number) |
| `VisitJobFailed` | `"failed"` | `error` (string) |

Gson cannot discriminate a `oneOf` on its own, so `JobStatusDto` is the **union** of all
three — `job_id`, `status`, `distance?`, `error?` — and `LandmarkRepositoryImpl` branches
on `status` after parsing. All three variants are handled:

- `done` → write `distance`, mark `DONE`
- `failed` → mark `FAILED`, surfacing the server's `error` string to the user
- `pending` → keep polling, until the 40-pass ceiling

Note that `failed` is a real documented state, not a hypothetical. Treating anything
that is not `done` as "still pending" would leave failed visits spinning forever.

## Documented error codes

| Code | Spec meaning | Mapped to |
|---|---|---|
| 403 | key missing / unknown / previous semester | `ApiError.InvalidKey` — fatal, never retried |
| 404 | unknown `landmark_id`; or `job_id` not found / belongs to another key | `ApiError.NotFound` — fails that one row |
| 400 | required fields missing | `ApiError.BadRequest` |

403 is documented on **all six** operations, which is why it is handled centrally in
`safeApiCall` rather than per call site. 400 and 404 appear only on `visit_landmark` and
`get_job_status`; the app handles them generically, which costs nothing and is safe if
the server ever widens their use.
