# Progress

## Phase status

| Phase | Scope | Status |
|---|---|---|
| 0 | Skeleton, Compose navigation, GitHub Actions APK build | **Done — awaiting on-phone test** |
| 1 | 3D viewer, mesh pipeline, exporters | Not started |
| 2 | Engine 1 without LLM (DSL, templates, sliders, SDF → mesh) | Not started |
| 3 | Engine 1 with local LLM | Not started |
| 4 | Engine 2 (photo → 3D) | Not started |
| 5 | Engine 3 (camera capture) | Not started |
| 6 | Polish | Not started |

## Done
- Gradle project (AGP 8.7, Kotlin 2.0, Compose BOM 2024.12, compileSdk/targetSdk 35, minSdk 29 / Android 10).
- Single-activity Compose app, MVVM, navigation: Home → Engine (×3), Library, Models, This device.
- Home: three engine cards with staggered entry animation, Library/Models tiles, device chip.
- Device detection at startup: RAM, SoC (Android 12+), CPU cores, Vulkan, ABI → tier (Entry / Mid-range / Flagship). Unit-tested.
- Engine, Library and Models screens are honest placeholders ("Not built yet — arrives in Phase N"). Nothing is faked.
- GitHub Actions: unit tests → signed release + debug APKs → artifact + GitHub Release (main) / rolling `dev-build` pre-release (other branches).
- Adaptive launcher icon (wireframe cube).

## In progress
- Nothing. Waiting for go-ahead on Phase 1.

## Known issues
- Release APK is signed with a public test key (see README → Release signing). Fine for testing, must be replaced before any public release.
- Device tier is RAM-based only; accelerator (GPU/NPU delegate) checks come with the first model integration.
- Not yet run on a physical device by the developer (no emulator in the build environment); first on-device check is the Phase 0 acceptance test.

## Decisions
- **minSdk 29 (Android 10).** All 6 GB+ phones ship with 10+, and ARCore Depth / modern ML runtimes need recent APIs.
- **Test-key signing in repo** so every CI build installs over the previous one without a laptop. Private key via secrets is supported and documented.
- **Debug build uses `.debug` package suffix** so it can sit next to the release build.
- **Version = 0.0.<CI run number>** so each build is a valid upgrade.
- **Single `:app` module for Phase 0**; a pure-Kotlin `:core` module (mesh/SDF/DSL/exporters) will be added in Phase 1 for fast, device-free tests.
