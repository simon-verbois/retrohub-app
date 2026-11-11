# Decisions

Choices made while setting up the project, with their reasons. The simplest option won whenever
nothing required otherwise.

## Platforms and technologies

- **Kotlin Multiplatform + Compose Multiplatform** (goal: Android and iOS, as fast and customisable
  as possible). Everything that is not platform-specific is shared: API, sync, Room KMP, emulation
  profiles and the whole interface, game screen included. Emulation goes through the
  `EmulatorEngine` interface: LibretroDroid on Android, a native libretro frontend on iOS
  (`docs/ios.md`).
- **Ktor (OkHttp engine on Android) instead of Retrofit**: Retrofit is not multiplatform. On
  Android, requests still go through OkHttp; kotlinx.serialization remains the format.
- **Manual injection** (`AppContainer`) rather than Hilt: one graph, simple, multiplatform.
- **Custom navigation** (`ui/nav/Navigation.kt`): a screen stack and sections driven by LB/RB and B;
  easier to make identical with a controller, touch and keyboard.
- **Room KMP with bundled SQLite**, same queries on Android, iOS and the test JVM.
- **Versions**: AGP 9.5.1, Gradle 9.9.0, Kotlin 2.4.21, KSP 2.3.13, Compose Multiplatform 1.12.2,
  Compose BOM 2026.09.00, Room 2.8.5, WorkManager 2.12.0, Ktor 3.6.1, OkHttp 5.5.1, Coil 3.6.3,
  LibretroDroid 0.14.0 (JitPack), checked on the Maven repositories on 10 November 2026.
- `minSdk 26`, `compileSdk`/`targetSdk 37` (latest stable API, platform `android-37.0`).
- Application ID `ovh.verbois.retrohub` (`.debug` for debug builds), after the instance's domain.

## Appearance

- Theme taken from the web's new design (the "sky" of `frontend/src/app/globals.css` and `Sky` in
  `shell.tsx`): blue gradient, glows, line ribbons, particles, glass and white text, Manrope font.
  The dark variant is the same sky at night. By default the app follows the phone; the system
  "remove animations" setting freezes the background.
- Home screen in the style of the PS5, validated on a mockup: a row of covers, the game's artwork
  in large, activity cards. No game detail page: tapping the selected tile runs the main action, a
  long press or □ opens the game menu under its title; the rest (cheats, history, removal) lives in
  the game options.
- Tabs reduced to Home and Downloaded: the library opens from the tile at the end of the row,
  favourites from the heart in the top bar.
- Little text: no explanatory sentence under settings or in cards; only values, states, errors and
  what a confirmation is about to change.
- Animated background optimised like the web: 24 frames per second at most, ribbons drawn once then
  moved, precomputed particles, no animated blur.

## Settings

- Settings form a tree: **RetroHub account** (shared with the web), **This device**, **Consoles**
  (and each game's options), **About**.
- Only `prefer_french`, `auto_save_seconds` and `cores` go through `/account/preferences`, with a
  read-merge-write (GET then PUT of the whole object) so the web's keys are never erased. The
  account wins on every sync. A core is only sent if the web can run it. Volume, theme and
  controls stay specific to each device.
- Parental control: only the SHA-256 of the code is kept; it protects the settings and the chosen
  games, on this device only.
- Touch controls: the in-game editor stores an offset and a scale per group (D-pad, buttons,
  shoulders, Start/Select), separately in portrait and landscape.
- Advanced options: the list comes from the running core (`retro_get_variable`); a choice is kept
  for the whole console and takes precedence over every other setting.
- Cheat codes are stored on the device, per game; the server has no field for them.
- "What's new" reads the repository's `CHANGELOG.md`, bundled as is in the app; the dialog opens
  once per version (not on the very first launch).

## Test emulator

- **docker-android dropped in favour of the SDK emulator**. On this workstation (Fedora, kernel
  7.1, rootless podman), the `budtmo/docker-android` image had two problems:
  - `sudo chown /dev/kvm` fails without root (could be worked around);
  - QEMU crashes (SIGSEGV) as soon as KVM is active, with emulators 37.2.12, 37.2.13 and 37.3.8,
    outside the container as well.
  - The trace points to the host's SwiftShader renderer. With `-gpu host` (the host's Intel GPU),
    the SDK emulator boots in 30 s: that is what `scripts/emulator.sh` does.
- Test device: API 35 x86_64 (Google APIs), Pixel 6 screen. The last free docker-android image
  stopped at API 34.
- CI (`scripts/ci.sh`) wipes the app's data: on a workstation whose play emulator is linked to a
  real account, run it on another virtual device:
  `RETROHUB_AVD=retrohub-ci RETROHUB_EMULATOR_PORT=5582 scripts/ci.sh`.
- Tests cut the network with `svc wifi/data disable`; `adb reverse` still reaches the backend, but
  the app believes it is offline, as on a real phone.

## Emulation

- Cores chosen to **share save states with the web** when the core exists on Android: fceumm,
  snes9x, gambatte, mgba, genesis_plus_gx, pcsx_rearmed, melonds, stella2014. They are the server's
  first EmulatorJS cores, so a state made on the web reloads on the phone.
- PlayStation: **pcsx_rearmed by default** (fastest on ARM, states shared with the web);
  **swanstation as an option**, with software rendering. Its OpenGL renderer needs a desktop
  context that LibretroDroid does not provide.
- Nintendo 64: `parallel_n64` (mupen64plus_next is not built for Android on the buildbot), with
  **Glide64 on GLES2 and HLE RSP**. The default ParaLLEl renderer requires Vulkan.
- DS: melonDS renders in software on Android and draws the screen layout itself
  (`melonds_screen_layout`). It follows the orientation: stacked in portrait, side by side in
  landscape. The touch screen receives fingers placed outside the controls.
- GameCube: Dolphin libretro, with its bundled `Sys` folder (`dolphin-sys.zip`, extracted on first
  launch). Saves follow the contract's `gci_zip` format.
- Dreamcast: Flycast is bundled. The console does not exist on the server yet
  (`docs/server-requests.md`): its games will appear once it is added. Without a BIOS, Flycast
  boots with its HLE BIOS.
- **Cores pinned by `cores.lock`** (SHA-256 of each `.so`). The buildbot only keeps the "latest"
  nightlies: verified files are kept in a content-addressed cache
  (`~/.cache/retrohub-android/cores`). A changed nightly is refused until `--update` is run, then
  tested and the lock committed.
- Default rendering: sharp for 2D home consoles (high definition in the Quality profile), LCD for
  handhelds, smooth for 3D.

## Sync and data

- The **upload queue** is the set of slots modified locally: a local copy whose SHA-256 differs
  from the base revision's. There is no separate table: the state is always true, even after a
  hard stop.
- Autosave like the web: SRAM and state 0 every 60 s (adjustable), on pause and on exit; slots 1
  to 9 at the player's choice. At launch, the most recent compatible state is reloaded, which can
  be turned off in the settings.
- An empty or zero-filled SRAM (game without battery) is not uploaded.
- Server restore (different save_epoch):
  - a locally modified copy becomes a conflict;
  - an unchanged copy takes the restored version;
  - if the server no longer has the slot, the copy restarts from revision 0 and is uploaded again,
    so nothing is lost.
- Another account signing in replaces the local library. Saves stay on disk, tied to their
  `play_game_id`.
- Downloads run as a foreground WorkManager job (progress notification), so they continue with the
  app closed. An interruption leaves a `.part` file resumed with Range on the next download.
- Mobile data is refused for games until the setting is enabled; saves always go through.
- Nothing is backed up by Android (cloud or transfer): the token is tied to the device and progress
  is already on the server.

## Signing and distribution

- **Published like Inkora**: the private source lives on Forgejo; an `YYYY.W.N` tag starts the CI,
  which checks, signs the APK with the distribution key (Forgejo variables), publishes an audited
  snapshot of the code on GitHub (without private history or internal files) and the release with
  the APK and its SHA-256 sums.
- Code under the MIT license, like Inkora; because of LibretroDroid, the APK follows the GPL-3.0.
- `versionCode = year × 1,000,000 + week × 10,000 + number`; `0.1.0` (code 1) between published
  versions.
- `-Pretrohub.abis=arm64-v8a` produces a lighter APK for phones; both ABIs are bundled by default.

## Performance and regression checks

- Snapshot serialization waits on the emulation thread from an I/O coroutine. Save comparison uses
  the recorded digest, hashes new snapshots in bounded chunks, and never reads the old snapshot
  into a second full-sized array. The activity waits for its final save before destroying the core.
- Catalogue JSON is decoded before joining local downloads. Search and sorting run on a background
  dispatcher; screen state is retained per route, including library focus and scroll position.
- Download progress is published at most five times per second, plus completion. WorkManager retries
  transient failures, respects the mobile-data setting, and coalesces pending sync requests.
- Release builds enable R8 and resource shrinking. The `benchmark` variant uses the same optimizers,
  debug signing and local test-server access; local CI runs instrumented and end-to-end checks on
  both variants. Test references generate scoped keep rules for the benchmark app, preserving the
  APIs reached from the separate instrumentation APK and their package boundaries. Production builds
  only keep JNI entry points.
  This is a functional regression check, not a hardware performance benchmark.
