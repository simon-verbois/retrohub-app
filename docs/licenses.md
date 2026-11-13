# Licenses

The app's code is under the MIT license and published with each version. The APK bundles
LibretroDroid (GPL-3.0): distributed as a whole, it therefore follows the GPL-3.0, and the
corresponding source code is the public repository at the version's tag. The cores come from the
libretro buildbot; their source is in each core's libretro repository.

| Component | License | Role |
|---|---|---|
| ZXing Android Embedded 4.3.0 and ZXing Core 3.5.4 | Apache-2.0 | Offline pairing QR camera and decoder |
| LibretroDroid 0.14.0 (Swordfish90) | GPL-3.0 | Android libretro frontend (Lemuroid's engine) |
| fceumm | GPL-2.0 | NES |
| snes9x | Snes9x non-commercial license | Super Nintendo |
| gambatte | GPL-2.0 | Game Boy / Color |
| mgba | MPL-2.0 | Game Boy Advance |
| genesis_plus_gx | Genesis Plus GX non-commercial license | Master System, Mega Drive |
| pcsx_rearmed | GPL-2.0 | PlayStation |
| swanstation | GPL-3.0 | PlayStation (option) |
| melonds | GPL-3.0 | Nintendo DS |
| azahar | GPL-2.0+ | Nintendo 3DS; [source](https://github.com/JohnDoe6345789/azahar_libretro) |
| armsx2 | GPL-3.0+ | PlayStation 2; [source](https://github.com/ARMSX2/ARMSX2) |
| stella2014 | GPL-2.0 | Atari 2600 |
| parallel_n64 | GPL-2.0 | Nintendo 64 |
| dolphin (and its Sys folder) | GPL-2.0+ | GameCube |
| flycast | GPL-2.0 | Dreamcast |
| Lucide (icons, logo) | ISC | Interface and app icons |
| RetroArch Monochrome icons | CC-BY-4.0 | Console silhouettes from the [Libretro assets contributors](https://github.com/libretro/retroarch-assets), commit `8c918e2872482139273374ef4cc6c7aff34d7585`; converted from SVG to Compose vector resources and normalized to 24 units |
| Manrope | SIL OFL 1.1 (`docs/fonts/Manrope-OFL.txt`) | Font, taken from the web |
| "Laser Club" illustrations | Original works of the RetroHub project | Placeholder artwork for missing covers |

Cores come from the libretro buildbot (`scripts/fetch-cores.sh`, `cores.lock`). Snes9x and
Genesis Plus GX forbid commercial use: the app is distributed for free. No ROM or BIOS is in the
repository or the APK; BIOS files come from the RetroHub server.

The Light (300) Manrope asset is a static instance of the OFL variable font from
[Google Fonts commit b31870a](https://github.com/google/fonts/blob/b31870aff700ab7a1d74fa0c6887d95beb9e0037/ofl/manrope/Manrope%5Bwght%5D.ttf).
It uses the same `docs/fonts/Manrope-OFL.txt` license.

The console icon attribution, original filenames and checksums are retained in
`shared/src/commonMain/composeResources/files/licenses/retroarch-icons-source.json`.
The complete CC BY 4.0 license is bundled beside it as `retroarch-assets.txt`.
The artwork is tinted by the app's existing icon colors; no manufacturer endorsement is implied.
