# Cores and settings

Libretro options applied by `shared/.../emulation/CoreOptions.kt`. Keys and values were taken from
each core's source (github.com/libretro repositories, November 2026). A console's advanced options
(`ConsoleSettings.optionsByCore`) are isolated by console and core and override defaults. Legacy
`options` apply only to the console’s selected core, keeping older preferences readable.

| Console | Core | Recommended settings |
|---|---|---|
| NES | fceumm | 4:3, "High" sound, overscan hidden |
| Super Nintendo | snes9x | 4:3, Gaussian interpolation |
| Game Boy / Color | gambatte (mgba as an option) | automatic colourisation, GBC colour correction, LCD ghosting |
| Game Boy Advance | mgba | colour correction, frame blending, BIOS skipped, frame skip on small devices |
| Master System, Mega Drive | genesis_plus_gx | auto region, Nuked YM2612 (MAME on small devices) |
| PlayStation | pcsx_rearmed (swanstation as an option) | ×2 "NEON enhancement" depending on the profile, libretro memory card; swanstation in software |
| Nintendo DS | melonds | layout following the orientation, touch screen in "Touch" mode, French, JIT |
| Nintendo 3DS | azahar (ARM64 only) | OpenGL ES, native resolution by default, private per-game save directory, orientation-aware dual screens |
| PlayStation 2 | armsx2 (ARM64 only) | OpenGL ES, native resolution by default, dedicated memory cards and private BIOS/resources |
| Atari 2600 | stella2014 | standard palette |
| Nintendo 64 | parallel_n64 | Glide64 (GLES2), HLE RSP, recompiler, resolution depending on the profile, Controller Pak, enhanced HLE audio (32-tap resampling) and buffered audio output |
| GameCube | dolphin | hardware rendering, ×1 to ×4 resolution, asynchronous shader compilation, French, BIOS skipped |
| PSP | ppsspp | OpenGL ES, 480×272 to 1920×1088, French, JIT, private per-game Memory Stick saves; system assets bundled for offline play |
| Dreamcast | flycast | ×1 to ×4 resolution, French, threaded rendering, per-game VMU |

Touch button mapping (RetroPad): `ui/play/PadLayouts.kt`, following the cores. Examples: Mega Drive
A/B/C = Y/B/A; N64 A/B = B/Y, Z = L2, C buttons = right stick; Dreamcast A/B/X/Y = B/A/Y/X.

Disc archives are extracted locally before launch. PlayStation prefers an existing M3U playlist,
then CUE, CHD, PBP and ISO; multiple disc descriptors receive a generated M3U playlist. Dreamcast
prefers GDI descriptors. Track directories are retained and extracted content is replaced when the
server ROM checksum changes. The original archive checksum remains the save compatibility key.

3DS/PS2 are integrated for Android ARM64; their cores are not published for the x86 test emulator.
Actual rendering, audio and controller behavior require validation on a physical ARM64 device.
The optional `game_mods_v1` contract adds immutable 3DS texture packs, RomFS overlays, ExeFS
replacements/IPS/BPS patches and native Gateway cheat files. The app stages complete private
load trees before activation, keeps original ROMs unchanged, and separates gameplay-mod save
contexts. Texture variants can stay local to each device; instant saves require the exact full
mod fingerprint in addition to the core build and ROM. Unsupported installer kinds are rejected.
The complete pending server/API contract is recorded in `docs/server-requests.md`.
The selected renderer is OpenGL ES, matching LibretroDroid's context. Sources and option values:
[Azahar](https://github.com/JohnDoe6345789/azahar_libretro/tree/dc62d51a8b1c0c0db1ee368d244de420a385b2cc/src/citra_libretro)
and [ARMSX2](https://github.com/ARMSX2/ARMSX2/blob/37ed35b88fe9a0164369c8042f2c9aa707e33667/pcsx2-libretro/Main.cpp).
Their verified binary hashes are pinned in `cores.lock`; state uploads carry that hash in
`X-Emulator-Build`. Loading requires matching console, core, build and ROM.

Native title data/cards and resource installations are private to an account and game. PS2's
system directory is dedicated because ARMSX2 also writes its cards below that directory.
The app downloads only authenticated, bounded resources from `games[].system_resources`,
checks the object and individual file hashes, and installs them before starting the core.
Missing PS2 files ask the player to import their BIOS and resources on the server. No BIOS is bundled.
3DS saves export only the validated title's data, with normalized SD identities; arbitrary homebrew
files remain local. Restores validate the archive and manifest before replacing dedicated data.

A native runtime binding is recorded before launch. Before a pull, push or epoch reconciliation,
the app checkpoints files flushed by a stopped core, including a session interrupted before its
first autosave. Those changes use the normal revision/conflict protocol. Running cores are not
read, and an explicit server/history choice awaiting installation is preserved. A restored server
invalidates the running game's old base revision without replacing its files.

PPSSPP uses the pinned libretro system archive under `system/PPSSPP` and a private per-account,
per-game and per-ROM Memory Stick root. Only `PSP/SAVEDATA` is synchronized in the versioned
`ppsspp_savedata_zip_v1` archive; caches, firmware and emulator configuration remain local.
States require the exact PPSSPP build and ROM checksum. PSP controls follow the original handheld:
D-pad, one left analog stick, four PlayStation face buttons, L/R, Select and Start.

Native tests download PPSSPP's public [spinning-cube demo](https://www.ppsspp.org/legacybuilds/)
with a pinned checksum through `scripts/fetch-test-content.sh`. It is packaged only in the test
APK; the application APK contains no game or test ROM.
