# Cores and settings

Libretro options applied by `shared/.../emulation/CoreOptions.kt`. Keys and values were taken from
each core's source (github.com/libretro repositories, November 2026). A console's advanced options
(`ConsoleSettings.options`) override everything.

| Console | Core | Recommended settings |
|---|---|---|
| NES | fceumm | 4:3, "High" sound, overscan hidden |
| Super Nintendo | snes9x | 4:3, Gaussian interpolation |
| Game Boy / Color | gambatte (mgba as an option) | automatic colourisation, GBC colour correction, LCD ghosting |
| Game Boy Advance | mgba | colour correction, frame blending, BIOS skipped, frame skip on small devices |
| Master System, Mega Drive | genesis_plus_gx | auto region, Nuked YM2612 (MAME on small devices) |
| PlayStation | pcsx_rearmed (swanstation as an option) | ×2 "NEON enhancement" depending on the profile, libretro memory card; swanstation in software |
| Nintendo DS | melonds | layout following the orientation, touch screen in "Touch" mode, French, JIT |
| Atari 2600 | stella2014 | standard palette |
| Nintendo 64 | parallel_n64 | Glide64 (GLES2), HLE RSP, recompiler, resolution depending on the profile, Controller Pak |
| GameCube | dolphin | hardware rendering, ×1 to ×4 resolution, asynchronous shader compilation, French, BIOS skipped |
| Dreamcast | flycast | ×1 to ×4 resolution, French, threaded rendering, per-game VMU |

Touch button mapping (RetroPad): `ui/play/PadLayouts.kt`, following the cores. Examples: Mega Drive
A/B/C = Y/B/A; N64 A/B = B/Y, Z = L2, C buttons = right stick; Dreamcast A/B/X/Y = B/A/Y/X.
