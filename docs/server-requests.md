# Requests to the RetroHub server

Needs found on the app side. The `../retrohub` repository is not modified from here; each item
describes the workaround in place.

1. **Add Dreamcast** to `consoles.py`:
   - `dreamcast`: extensions `chd`, `cdi`, `gdi`;
   - `cores: []` (no web core) and `native_cores: ["flycast"]`;
   - optional BIOS shipped as a ZIP with `dc/dc_boot.bin` and `dc/dc_flash.bin`, with the `dc/`
     folder at the root so extraction lands in `system/dc/`.
   - Workaround: the console and Flycast are ready in the app, and its games will appear as soon as
     the server serves them. Without a BIOS, Flycast boots with its HLE BIOS.
2. **Remove the consoles that will not be kept** (Atari 5200/7800, Lynx, Jaguar, Game Gear,
   arcade), as announced.
   - Workaround: the app shows these games as not playable in the app yet, without launching them.
3. **Adding `nestopia` to the native cores is not needed**, but `parallel_n64` must stay accepted.
   - N64 SRAM from mupen64plus_next (web) and parallel_n64 (app) share the same raw format. Save
     states are not shared between the two cores: the app only loads a state made by the same core.
4. **Renaming a device**: `PATCH /account/devices/{id}` with `{"name": "…"}` (1 to 64 characters,
   204 response), announced by the `device_rename` entry in `/native/status` `features`.
   - Workaround: the app hides "Rename" until the server announces `device_rename`; the name is
     chosen when linking.
5. **Announce new app versions** with `apps.android`, already present in `/native/status`. The test
   server returns `null`.
   - Workaround: About shows the update (and the "required" banner below `min_version`) as soon as
     the server fills `latest_version`, `min_version` and `download_url`.
