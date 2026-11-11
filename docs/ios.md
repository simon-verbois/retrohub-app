# iOS

The shared code (`shared/`) already targets `iosArm64` and `iosSimulatorArm64`: Ktor API (Darwin
engine), sync, Room KMP, Compose Multiplatform interface (`MainViewController`). iOS targets only
compile on a Mac (Xcode); on Linux, Gradle skips them (`kotlin.native.ignoreDisabledTargets`).

## Still to write on a Mac

1. **Xcode project** `iosApp/` that embeds the `RetroHubShared` framework, creates the Room database
   (`Room.databaseBuilder<AppDatabase>(name = …)`) and calls `MainViewController(container)`.
2. **iOS `Platform`**:
   - token in the Keychain (`TokenVault`);
   - network with `NWPathMonitor`;
   - controllers with `GCController`;
   - background sync with `BGTaskScheduler`;
   - launching a game.
3. **Libretro frontend** implementing `EmulatorEngine` (Metal or OpenGL ES rendering,
   `AVAudioEngine` audio, input) and a screen that places the shared `GameScreen` over its view.
4. **Cores**: iOS cores must be compiled, signed and bundled in the app (no download at runtime).
   - Without JIT, Dolphin and the recompilers (N64, PlayStation) will be slow or impossible on a
     consumer iPhone: GameCube will probably stay Android-only.
5. Writing GCI ZIPs: the iOS `zipFlat` remains to be implemented (reading already works via Okio).
