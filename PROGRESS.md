# Progress

## Phase status

| Phase | Scope | Status |
|---|---|---|
| 0 | Skeleton, Compose navigation, GitHub Actions APK build | Done |
| 1 | 3D viewer, mesh pipeline, exporters | Done (tested on phone) |
| 2 | Engine 1 without LLM (DSL, templates, sliders, SDF → mesh) | **Done — awaiting on-phone test** |
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

### Phase 1
- New pure-Kotlin `:core` module (no Android dependencies, fast JVM tests):
  - `Mesh` (indexed, mm, Z-up), bounds, volume, surface area, unique edges.
  - `MeshCleanup`: drops invalid coordinates, merges duplicate vertices (spatial hash), removes zero-size and duplicate triangles,
    makes face orientation consistent and outward, then reports holes / non-manifold edges / inside-out / separate parts in plain words.
  - `Decimator`: quadric-error edge collapse that keeps closed meshes closed (link condition + flip checks). 82k → 5k triangles in <1 s on desktop JVM.
  - Exporters: binary STL, OBJ, GLB (glTF 2.0; converted to metres, Y-up), 3MF (millimetres).
  - Sample meshes: calibration cube, sphere, torus ring, cylinder, dense sphere (82k tris), deliberately damaged box.
- 21 core unit tests (cleanup, decimation, exporters incl. round-trips, sample geometry).
- **Independent verification** in the build environment: every sample in every format loads in `trimesh` (watertight, correct volume/size);
  **PrusaSlicer 2.7 CLI sliced the exported STL, 3MF and OBJ into G-code**, and independently reported the damaged box's 3 open edges.
- App: OpenGL ES 3 viewer (orbit / pinch-zoom / two-finger pan / double-tap reset, 4× MSAA, wireframe overlay, Studio/Clay/Contrast lighting, bed grid).
  Inside faces seen through holes are tinted orange.
- Viewer screen: size in mm, triangle count, volume, watertight status, plain-language health report, simplify slider with restore,
  export as STL/3MF/OBJ/GLB via share sheet or save to Downloads/MeshGen.
- Home → "Sample meshes" gallery → viewer.
- Shaders validated with `glslangValidator`; `tools/render_preview.py` renders the app's shaders on desktop (docs/viewer-preview.png).

### Phase 1 follow-up (on-phone feedback)
- Viewer now takes 50% of the screen: title/back button overlay the 3D view, stats in one slim row, health check collapses to one
  tappable line, simplify is a compact card, export is one row (format menu + Save + Share). Lighting is one button that cycles presets;
  added a Reset view button. Checked at 1.3× font scale with a Paparazzi layout render (local only, not in CI).
- Fixed: screen titles and back arrows were black on black on sub-screens (default text colour now light app-wide).

### Phase 2
- **Shape recipe language** (docs/SHAPE_DSL.md): 7 primitives, 4 booleans, 3 transforms, 2 arrays, shell (with open top) and offset;
  named parameters with ranges; expressions in any number field. Strict validation with paths; all problems reported at once.
- **SDF engine** (`core/sdf`): exact distance functions where possible, Lipschitz bounds for twisted/tapered shapes,
  bounding-box culling in unions and cuts.
- **Marching cubes** with a case table generated in code (consistent ambiguous-face rule, outward loops, fan triangulation
  that never puts a diagonal on a cube face, centre vertex when needed) → watertight and manifold by construction.
  Slab-by-slab (low memory), coarse pre-pass skips empty space safely, multi-core plane evaluation, cancellable.
- **18 templates**: planter, hex pen holder, box with lid, wall hook, phone stand, twisted vase, gear-like ring,
  cable holder, coaster, bowl, jewelry tray, stackable bin, L bracket, knob, soap dish, keychain tag, drawer pull, tealight holder.
  Each has a natural-language `prompt` (few-shot examples for Phase 3). Thumbnails rendered with the app's shaders (`tools/render_thumbnails.py`).
- App: Text → Shape card opens the template gallery; editor with live parameter sliders (Draft preview while dragging,
  auto-refine to chosen quality), quality picker (Draft/Standard/Fine) with timing, recipe JSON view, health check,
  simplify and export. Out-of-memory falls back to a lower quality and says so.
- Tests: 43 core tests. Every template is meshed at default, all-minimum and all-maximum parameters and must be watertight.
- Verified in the build environment: **PrusaSlicer reports all 18 templates manifold and slices each to G-code**;
  Standard quality meshes in 20–390 ms on a desktop JVM (expect several times slower on a phone).

## In progress
- Nothing. Waiting for go-ahead on Phase 3.

## Known issues
- Release APK is signed with a public test key (see README → Release signing). Fine for testing, must be replaced before any public release.
- Device tier is RAM-based only; accelerator (GPU/NPU delegate) checks come with the first model integration.
- No emulator or phone in the build environment: the viewer's GL code has not run on real hardware yet. Shaders are validated and
  previewed on desktop Mesa; the on-phone test is the real check.
- Holes and non-manifold edges are reported but not repaired (hole filling comes later if needed; engine 1 produces watertight meshes by construction).
- Decimation skips edges on open boundaries, so open meshes simplify less.
- Library does not store meshes yet (Phase 6).
- Marching cubes rounds sharp edges slightly (about one cell); Fine quality keeps this under ~0.5 mm for 100 mm parts.
  Feature-preserving dual contouring could be added later if needed.
- `smooth_union` bulges slightly where two coplanar faces meet; templates trim it with an `intersect`.
- Twisted/tapered extrusions have slightly thinner shells than the nominal thickness at strong twist.
- Phone generation speed not yet measured on a real device.

## Decisions
- **minSdk 29 (Android 10).** All 6 GB+ phones ship with 10+, and ARCore Depth / modern ML runtimes need recent APIs.
- **Test-key signing in repo** so every CI build installs over the previous one without a laptop. Private key via secrets is supported and documented.
- **Debug build uses `.debug` package suffix** so it can sit next to the release build.
- **Version = 0.0.<CI run number>** so each build is a valid upgrade.
- **Pure-Kotlin `:core` module** for mesh/SDF/DSL/exporters so tests run on the JVM without a device.
- **Viewer: custom OpenGL ES 3 renderer instead of SceneView/Filament.** Generated meshes change constantly and need a wireframe overlay;
  Filament has no wireframe mode, needs materials precompiled with an extra tool, and adds native libraries. The custom renderer is
  ~300 lines, flat-shades any mesh without duplicating vertices, and tints inside faces to reveal holes. Can be revisited if we need PBR/AR.
- **Exports in millimetres**; GLB converted to metres + Y-up as glTF requires. GLB omits normals so viewers flat-shade (correct for printable parts).
- **Shape DSL: every primitive rests on the bed, centred on Z.** One rule is easier for a small LLM to follow than mixed conventions.
- **Expressions instead of free code**: numbers can reference parameters, but there are no loops or variables beyond params.
- **Marching cubes with generated table** rather than a hand-typed 256-row table (avoids transcription errors, guarantees manifold output).
- **Live editing = Draft first, then refine** to the chosen quality after a short pause.
- **Save to Downloads uses MediaStore** (no storage permission on Android 10+), into Downloads/MeshGen.
