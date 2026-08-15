# Project Structure

```
SmartLandmarks/
├── settings.gradle.kts              Module + repository declarations
├── build.gradle.kts                 Root build script
├── gradle.properties                >>> SMART_LANDMARKS_API_KEY lives here <<<
├── gradlew / gradlew.bat            Wrapper scripts
├── gradle/
│   ├── libs.versions.toml           Version catalog — every dependency version
│   └── wrapper/                     Gradle 8.9 wrapper
├── README.txt                       Overview, API usage, offline strategy, challenges
├── Ai_usage.txt                     AI usage declaration
├── Project_Documentation/
│   ├── ARCHITECTURE.md              Design decisions and their reasons
│   ├── API_CONFORMANCE.md           Implementation checked against openapi.json
│   ├── INSTALLATION.md              Setup, commands, troubleshooting
│   ├── TESTING_REPORT.md            Static results + device test plan
│   ├── PROJECT_STRUCTURE.md         This file
│   └── openapi.json                 The API specification (source of truth)
└── app/
    ├── build.gradle.kts             Module config, BuildConfig injection
    ├── proguard-rules.pro           R8 keep rules for Retrofit/Gson/Room/osmdroid
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/example/smartlandmarks/
        │   │   ├── SmartLandmarksApp.kt        @HiltAndroidApp, WorkerFactory, osmdroid init
        │   │   ├── data/
        │   │   │   ├── local/
        │   │   │   │   ├── AppDatabase.kt
        │   │   │   │   ├── Converters.kt
        │   │   │   │   ├── dao/                LandmarkDao, VisitDao, PendingCreateDao
        │   │   │   │   └── entity/             LandmarkEntity, VisitEntity, PendingCreateEntity
        │   │   │   ├── remote/
        │   │   │   │   ├── ApiService.kt       All 6 endpoints
        │   │   │   │   ├── AuthInterceptor.kt  Injects the student key
        │   │   │   │   ├── ApiResult.kt        Sealed error model + safeApiCall
        │   │   │   │   └── dto/                Request/response DTOs
        │   │   │   ├── mapper/Mappers.kt       DTO <-> entity <-> domain
        │   │   │   └── repository/
        │   │   │       └── LandmarkRepositoryImpl.kt
        │   │   ├── domain/
        │   │   │   ├── model/                  Landmark, Visit, VisitStatus, PendingLandmark
        │   │   │   └── repository/             LandmarkRepository interface + outcome types
        │   │   ├── di/                         Hilt: Database, Network, Repository, Dispatcher
        │   │   ├── services/
        │   │   │   ├── NetworkMonitor.kt
        │   │   │   └── LocationProvider.kt
        │   │   ├── workers/
        │   │   │   ├── VisitSyncWorker.kt      Queue drain + job polling
        │   │   │   └── WorkScheduler.kt
        │   │   ├── ui/
        │   │   │   ├── MainActivity.kt
        │   │   │   ├── splash/                 SplashFragment
        │   │   │   ├── map/                    MapFragment, MapViewModel
        │   │   │   ├── landmarks/              Fragment, ViewModel, LandmarkAdapter
        │   │   │   ├── activity/               Fragment, ViewModel, VisitAdapter
        │   │   │   ├── add/                    AddLandmarkFragment, ViewModel
        │   │   │   ├── details/                LandmarkDetailsSheet, ViewModel
        │   │   │   └── common/                 UiMessage, ErrorMessages, LocationMessages
        │   │   └── utils/                      Constants, Formatters, ScoreColor,
        │   │                                   ImageUrlResolver, FileUtils, UiExtensions
        │   └── res/
        │       ├── layout/          9 layouts
        │       ├── values/          colors, strings, dimens, themes
        │       ├── values-night/    dark theme overrides
        │       ├── drawable/        vector icons (no binary assets)
        │       ├── mipmap*/         adaptive icons + API 24-25 fallbacks
        │       ├── menu/            bottom_nav_menu.xml
        │       ├── navigation/      nav_graph.xml
        │       └── xml/             backup + data extraction rules
        └── test/java/com/example/smartlandmarks/
            ├── data/                ApiResultTest, MapperTest
            ├── ui/add/              AddLandmarkViewModelTest
            ├── utils/               ScoreColorTest, FormattersTest, ImageUrlResolverTest
            └── fake/                FakeLandmarkRepository
```

## Counts

| | |
|---|---|
| Kotlin source files (main) | 53 |
| Kotlin test files | 7 |
| XML resources | 43 |
| Layouts | 9 |
| String resources | 55 |
| API endpoints implemented | 6 / 6 |

## Where to look first

| To understand… | Read |
|---|---|
| The async visit flow | `LandmarkRepositoryImpl.recordVisit` → `runSyncPass` |
| Offline behaviour | `VisitEntity` (the status lifecycle) + `VisitSyncWorker` |
| Error handling | `ApiResult.kt` then `ui/common/UiMessage.kt` |
| The three content types | `ApiService.kt` |
| Where the API key enters | `gradle.properties` → `app/build.gradle.kts` → `AuthInterceptor` |
