# Requests to the RetroHub server

Needs found on the app side. Checked items are implemented and tested in the `../retrohub`
server as of **2026-11-12**, deployed to production in **2026.46.5** (requests 1–6 first shipped
in **2026.46.2**, request 7 in **2026.46.3**, request 8 in **2026.46.4**; the avatar
replacement and request 9 shipped in **2026.46.5**).
The app's fallback behavior is kept below where it still matters for older servers.

1. [x] **Add Dreamcast** to `consoles.py`:
   - `dreamcast`: CDI and CHD images, plus GDI sets served as a validated ZIP containing the
     `.gdi` descriptor and all its tracks; a descriptor alone is not a complete game;
   - `cores: []` (no web core) and `native_cores: ["flycast"]`;
   - optional BIOS are not bundled for Dreamcast; Flycast uses its HLE BIOS. If added later,
     the ZIP must keep `dc/dc_boot.bin` and `dc/dc_flash.bin` at those paths.
   - Validated: native console discovery, Flycast save acceptance, complete GDI imports,
     missing/unsafe track rejection and CHD console detection (`test_native_api.py`,
     `test_extended_consoles.py`).
2. [x] **Remove the consoles that will not be kept** (Atari 5200/7800, Lynx, Jaguar, Game Gear,
   arcade), as announced.
   - Validated: retired consoles are absent from the registry and native discovery; the migration
     removes their catalog entries while preserving unrelated games (`test_retired_consoles.py`,
     `test_extended_consoles.py`, `test_native_api.py`).
3. [x] **Adding `nestopia` to the native cores is not needed**, but `parallel_n64` must stay accepted.
   - N64 SRAM from mupen64plus_next (web) and parallel_n64 (app) share the same raw format. Save
     states are not shared between the two cores: the app only loads a state made by the same core.
   - Validated: discovery advertises `parallel_n64`, its saves are accepted for N64, and NES does
     not gain a duplicate native-only Nestopia entry (`test_native_api.py`). Cross-core state
     compatibility is still the app's responsibility, not asserted by the server.
4. [x] **Renaming a device**: `PATCH /account/devices/{id}` with `{"name": "…"}` (1 to 64 characters,
   204 response), announced by the `device_rename` entry in `/native/status` `features`.
   - Implemented: bearer and browser-session support, ownership checks, CSRF for browser writes,
     whitespace normalization, and unchanged tokens. Existing save provenance and library ETags
     reflect the new device name.
   - Validated: 204 with an empty body; valid 64-character names; invalid names rejected; 401/403/404
     for missing authentication, CSRF or ownership; native rename followed by web display and
     device revocation (`test_native_api.py`, `frontend/tests/operations.spec.ts`).
   - Older-server fallback: the app hides "Rename" until `device_rename` is advertised.
5. [x] **Announce new app versions** with `apps.android` in `/native/status`.
   - Validated: configured `min_version`, `latest_version` and `download_url` are returned;
     unset or empty values return `null` without preventing startup (`test_native_api.py`).
   - Completed the Compose wiring for `RETROHUB_ANDROID_MIN_VERSION`,
     `RETROHUB_ANDROID_LATEST_VERSION` and `RETROHUB_ANDROID_DOWNLOAD_URL`. Rendered Compose
     configurations were checked with both unset and populated values. Helm already supports
     the same settings through `extraEnv`.
   - Deployment configuration remains explicit: set the latest published version and its APK URL
     using the example in `../retrohub/.env.example`; keep the minimum unset unless an older app
     must stop synchronizing. No minimum version or update announcement was enabled by this
     validation; these remain explicit deployment settings.

6. [x] **Display a pairing QR code beside the existing one-use code** in Applications and devices.
   - Encode the code itself (for example `ABCD-EFGH`) as plain QR text. Keep the readable code,
     its expiry and the new-code button. No password or device token belongs in a QR code.
   - Implemented: local SVG generation on the web with a white scanning margin in both themes;
     no external QR service or change to `/auth/pair`. Manual entry remains available.
   - Validated: decode the rendered QR and compare its full payload with the displayed code in
     dark/mobile and light/desktop layouts; replace the code, reject the previous one, pair with
     the decoded replacement and reject its reuse (`frontend/tests/operations.spec.ts`).

7. [x] **Notify active native clients when their library changes.**
   - Client consumption: subscribe to the authenticated stream when `library_events` is advertised,
     reconcile after every connection and coalesce invalidations over 250 ms through the manifest's
     existing refresh lock. Reconnect with the last event ID, server retry hints and exponential
     backoff with jitter up to two minutes. Revocation forgets the device session.
   - Polling fallback: `GET /native/library` supports conditional reads with `If-None-Match`.
     The app checks every 10 seconds when streaming is unsupported or disconnected,
     while its library interface is resumed and online. Checks stop in the background and during
     gameplay; reconnecting or returning to the interface checks immediately. Unchanged libraries
     return 304; failures back off up to two minutes and maintenance honors `Retry-After`.
   - Implemented capability: `library_events` in `/native/status.features` and
     `GET /native/events` as an authenticated SSE stream (`Authorization: Bearer <device token>`).
     Reuse normal device ownership, revocation, maintenance and authorization checks. Never put
     tokens in URLs. The complete contract is in `../retrohub/docs/native-api.md`.
   - Emits a user-scoped `library_changed` invalidation after a committed change: add/remove a
     library game, favorite or selected progression; finish installing/removing a ROM; change
     title, console, genre, year or artwork; change saves, play time or device provenance. Shared
     game changes must reach every affected library, without revealing other users' data.
   - Includes an opaque event ID, the authoritative manifest ETag and a reason; affected library
     IDs are omitted. Sends invalidations, not ROMs or save bodies. Samples committed ETags at
     most every 2 seconds per account/API process, sharing the sample across its connections
     and coalescing bursts from batch imports. This also observes commits from other workers
     and API replicas, without adding per-route notification hooks.
     The client debounces invalidations and re-reads the authoritative conditional manifest through
     the existing refresh lock. Receiving an event never overwrites a local save or resolves a conflict.
   - Sends a heartbeat comment after 20 seconds without an event, disables proxy buffering and
     transformation, and limits streams to 5 minutes. Per API process: 2 connections per device,
     6 per account and 100 total; excess connections return 429 with `Retry-After: 3`.
     The SSE retry is 3 seconds; repeated failures should back off with jitter up to two minutes.
     `Last-Event-ID` is accepted, but there is no replay journal: each connection begins with
     `resync_required` (reason `connected`, or `cursor_unavailable` when a cursor was supplied).
     Always check once after connecting to close the manifest/subscription gap.
   - On server restore, invalidate the library and expose the new `save_epoch` through the manifest.
     End revoked-device streams and reject reconnects with 401. Missing capability, dropped streams
     and older servers retain conditional polling; offline play and downloaded games remain available.
   - Native event delivery is only needed while the library interface is visible. Background push
     via FCM/APNs and Android notification permission are not prerequisites for this behavior.
   - Validated acceptance tests: web import/favorite/removal reaches an already connected native
     client; installation completion updates its ROM; batch changes coalesce; another user's events
     stay private; reconnect and expired cursors trigger reconciliation; revocation terminates access;
     maintenance is recoverable; no-change reads still return 304.
   - Nine real-HTTP server/regression tests cover these rules, including maintenance-lock release,
     resource cleanup and a slow library not blocking other stream connections. A browser-suite
     test validates live streaming through the Next.js proxy, not a buffered mock.
   - Deployed in server release **2026.46.3**. Live HTTPS checks validated the initial SSE frame,
     conditional manifest, cursor reconciliation, termination after token revocation and 401 on
     reconnect. The temporary verification device was removed afterward.

8. [x] **Ship a self-hosted default-avatar catalogue and share the chosen avatar across clients.**
   - Owner-approved replacement in **2026.46.5**: **260** local avatars from **Mario, Zelda,
     Sonic and Pokémon**, replacing the initial DiceBear catalogue. Includes PokéSprite,
     PAdventures, PokéAPI (Game Boy/GBA/DS), SpriteCollab portraits and expressions, the Mario
     archive, wrenr Zelda NES sprites, Outertoaster Sonic customs and SAT-R Sonic Advance 2.
     Commits, source bytes, crops, credits and licenses are pinned in
     `../retrohub/resources/avatars/`. No LoL or Baechu portraits are included.
   - Initial upstream for **2026.46.4**: [DiceBear](https://github.com/dicebear/dicebear), whose generator is
     [MIT licensed](https://github.com/dicebear/dicebear/blob/10.x/LICENSE). Use only the
     **Pixel Art**, **Pixel Art Neutral**, **Identicon** and **Shapes** collections for the initial
     catalogue: their artwork is **CC0 1.0**, independently of the generator's license
     ([style license inventory](https://github.com/dicebear/styles/blob/main/LICENSE.md),
     [Pixel Art](https://www.dicebear.com/styles/pixel-art/)). Other DiceBear styles have other
     licenses; do not automatically import the entire collection under the MIT label.
   - Generate a diverse curated set of at least 256 avatars at build time (for example 64 per
     collection), with stable, non-personal seeds. Pin the exact upstream version/commit and
     record seeds, generator options, source hashes and licenses in a manifest. Vendor the
     generated PNGs (128 or 256 pixels) and source/license notices into the server image. Keep
     published IDs stable across upgrades. No external avatar service or CDN is needed at runtime;
     do not send usernames, email addresses or tokens to DiceBear.
   - Give each account a stable default avatar from this catalogue, including existing accounts
     that have never chosen one. Resolve this locally from a stored default ID or a deterministic
     server-side assignment; a refresh must not randomly change the picture. A default is a real
     catalogue image, not an external URL derived from a username or email address.
   - Requested native capability: advertise `account_avatars` in `/native/status.features` only
     when the following endpoints and image assets are ready. The app consumes the contract below;
     the authoritative implemented contract must also be added to `docs/native-api.md`.
   - `GET /account/avatars` returns `{"avatars": [{"id": "pokemon-portrait-pikachu", "name": "Pikachu · Portrait",
     "url": "/api/v1/account/avatars/pokemon-portrait-pikachu.png?v=<content-hash>"}], "default_avatar": <item>}`.
     The default is one of the catalogue items. Return the complete bounded catalogue in stable
     order. Names are short and readable; IDs are opaque to clients.
     Images are served by RetroHub as PNG with immutable, versioned URLs, not arbitrary remote URLs
     or executable SVG content. The app accepts only `/api/v1/account/avatars/…` image paths.
   - `GET /account/avatar` returns `{"avatar": <catalogue item or null>}` for the authenticated
     user (the chosen image, or the resolved default). `PUT /account/avatar` with
     `{"avatar_id": "pokemon-portrait-pikachu"}` returns the same shape after saving. `{"avatar_id": null}`
     restores that account's stable default avatar, returned in
     `avatar`; `default_avatar` in the catalogue lets the app preview this reset option.
     Validate membership in the catalogue; reject unknown IDs without changing the current choice.
     This endpoint updates
     only the avatar, not the account's preferences object.
   - Include optional `user.avatar` (the same descriptor or null) in password/pairing responses
     for immediate display after sign-in. Persist the avatar on the account, not on a device;
     expose the same selection in the web account settings. Reuse device-token authentication,
     session ownership and browser CSRF checks. A user cannot modify another user's avatar.
   - The native app prepares a controller/touch picker in **Compte → Photo de profil**, a reset
     to the default image, cached display when offline and immediate header refresh after a confirmed
     save. It only calls the proposed routes and shows the picker when `account_avatars` is
     advertised. Older servers keep the initial, and a failed save keeps the previous avatar.
     Profile changes from the web are re-read during account sync and when opening account settings.
   - App-side validation: selecting an avatar, confirming its server value and restoring the
     account default pass against the local RetroHub backend in both debug and optimized builds.
     JVM and instrumented tests cover cached choices, rejected saves, account isolation, controller
     navigation and larger text. Server release validation is tracked separately from these client tests.
   - Avatar selection must not be lost by `PUT /settings`, a device rename, sign-in on another
     device or an unrelated library change. Do not cache a user's selection under another account;
     a missing or unreadable image falls back to the initial without blocking navigation.
   - Acceptance tests: catalogue and pinned license inventory are included in the shipped image;
     all listed images resolve without third-party requests; a new/existing account gets a stable
     default; choose/reset from web and a device converge; invalid IDs and unauthorized writes leave
     the current avatar intact; older clients still decode auth responses; offline display and
     a subsequent reconnect preserve the choice.

   - Implemented in **2026.46.4**: 64 images per selected collection, pinned DiceBear core
     **10.7.0** and styles **10.6.0** with exact commits, reviewed seeds/options/source and image
     hashes. API and quality builds generate the same 256 distinct 128-pixel PNGs; the Python
     wheel and API image include their manifest, four source definitions and license notices.
     Generation refuses changes to published IDs or the fixed default-assignment pool.
   - Server validation covers existing-account migration, deterministic defaults, password and
     pairing responses, web/native choose/reset convergence, preference/device/library changes,
     backup/restore preservation, invalid IDs, CSRF and account isolation. Missing or corrupt
     assets hide the capability without preventing sign-in.
   - Web browser validation covers the paginated picker in the right drawer, keyboard selection,
     confirmed header updates, failed-save preservation, native changes refreshed on focus,
     offline display/reconnect and image-error fallback. Mobile layouts were inspected in both themes.
   - Deployed via [release run 19](http://192.168.0.151/simonverbois/retrohub/actions/runs/19).
     Live checks verified all 256 HTTPS images against the reviewed PNG hashes, authenticated
     access, immutable caching and 304 responses, the mobile picker and unchanged account selection
     after closing an unsaved preview. The production package includes all sources and notices;
     a separate container check passed without networking. The temporary test device was revoked.

   - Replacement validation: all 260 final PNGs are nonempty, 128 × 128 and packaged with their
     originals and notices (including the editable Sonic Aseprite source). Rendering preserves
     pixels, isolates fixed poses and removes the Sonic custom sheet background. DiceBear choices
     transparently resolve to a stable new account default; the new default pool is fixed and
     balanced across the four universes. The web picker filters by the optional descriptor field
     `collection`; native clients may ignore that additive field.
   - Release **2026.46.5** passed the full Forgejo pipeline and live HTTPS checks of every avatar,
     its hash, authenticated access, caching and all four web picker filters. The temporary
     verification device was removed. The standalone HTML comparison was deleted as requested.

9. [x] **Hide disabled sources from Explorer and its filters.**
   - `GET /games` excludes games available only from disabled providers before pagination and
     counting, including searches and explicit source filters. `GET /sources` returns enabled
     providers only; `/admin/providers` still lists disabled providers for reactivation.
   - A merged game stays visible when another version has an enabled provider or is a local
     import. Detail pages omit disabled remote versions; stale download/file-choice requests are
     rejected with 409. Re-enabling the provider restores discovery without another sync.
   - Personal library/favorites, installed ROMs, progressions and `/native/library` remain
     available. Catalogue sort/filter options cannot bypass source visibility.
   - Tests cover browser-session and native-token reads, administration disable/re-enable,
     merged games, totals/pagination, stale requests, local imports and installed progression.
   - Production verification: before release, all 14 disabled providers still exposed games.
     In **2026.46.5**, each returns zero Explorer results and all are absent from `/sources`.
     The production activation settings were kept intact during verification.

## Pending implementation brief (2026-11-13)

Requests 10–18 cover the current native-app requirements. Implement the server contracts and their
server-side validation here; the Android/KMP interface and emulator host stay in `retrohub-app`.
Do not interpret a completed backend task as proof that a console runs on a physical phone.
Preserve the completed requests 1–9, existing user data and compatibility with older app versions.

Recommended order: 10 (console contracts), 11 (metadata), 15 (content validation), 16 (catalogue
regression coverage), then the additive capabilities in 13–14 and 17–18. Request 12 supplies
diagnostics; it is not a server-side graphics fix. Optional account synchronization in request 13
must not delay local folders, pins, favorites or offline play.

### Required contracts and acceptance criteria

10. [ ] **Nintendo 3DS and PlayStation 2 native contracts.** These are new contracts, not aliases of
    DS and PlayStation. Agree on stable console IDs (`3ds`, `ps2`) and native core IDs before the
    client sends new `X-Emulator-Core` values. Candidate maintained cores are `azahar` and `armsx2`;
    Android ARM64 artifacts exist, but frontend context compatibility and real saves need validation.
    Sources: [Azahar](https://docs.libretro.com/library/azahar/),
    [ARMSX2](https://docs.libretro.com/library/armsx2/).
    - Add registry/discovery, catalogue providers and filters, import detection, extension validation,
      covers and native-only capability flags. Never advertise browser play without a web core.
    - Detect decrypted 3DS NCSD/NCCH and 3DSX content; do not classify arbitrary ELF files as 3DS.
      CIA installation and encrypted ROMs need a separate explicit contract; never rename them to
      a supported format. Expose a player-actionable reason when conversion or system data is needed.
    - Detect PS2 discs independently of GameCube and PSX, including ambiguous ISO and CHD containers;
      validate playlists and serve every referenced disc/track together with stable relative paths.
    - Provide optional system-resource descriptors with paths, sizes, SHA-256 and authenticated URLs.
      PS2 requires user-supplied BIOS under `pcsx2/bios` and redistributable resources under
      `pcsx2/resources`; never distribute proprietary BIOS. Azahar may need user-owned Mii/system
      data. System-data import must be account isolated and must not expose secrets in logs.
    - Saves require explicit per-kind formats and limits. Azahar uses a directory tree (`Azahar/`,
      including NAND/SDMC); ARMSX2 memory cards are files, not a libretro raw SRAM buffer. Define
      validated per-game archives, stable names, conflict/base-revision/epoch semantics and atomic
      restoration. Keep caches, shaders, system secrets and other games out of save uploads.
    - State compatibility must include core/build identity and ROM SHA; never load PSX states into
      PS2 or DS states into 3DS. Add tests for unsupported/mismatched cores, large card archives,
      traversal, quota limits, interrupted transfer, duplicated sessions and offline reconciliation.
    - Client fallback until this contract is available: retain existing consoles and save behavior;
      do not claim that merely registering a new console makes its emulation and sync functional.

11. [x] **Optional console generations and icons in discovery.** Expose additive `generation`,
    `family` and a versioned, licensed icon descriptor (asset URL, checksum, attribution). Clients
    must keep local ordering/icons for older servers. Automatically generated console folders are
    derived from library membership and cannot be deleted; manual folders remain separate.

12. [ ] **Emulator diagnostics and capability reporting.** Allow opt-in device/core build, ABI,
    Android version, GPU/driver, renderer/context and last startup stage in a diagnostic report.
    Distinguish a graceful quit from native process death; omit ROM contents, BIOS, tokens and save
    data. Native startup/render failures must not upload an empty save or erase the last good state.
    Reproduce GameCube audio-with-black-video and controller-triggered exit on Snapdragon/Adreno
    hardware before declaring a driver fix; server changes alone cannot repair a graphics context.

13. [x] **Collections, permanent console folders and ordered pins.**
    - A console folder exists as soon as the user's library contains a game of that console. Derive
      membership from the library, not from a manually maintained list. Use stable IDs distinct from
      manual collections (`console:<console_id>`); membership updates on import, merge, game removal
      and newly visible installed versions. Removing the last game may hide the empty folder, but
      must retain its stable identity and favorite/pin preferences for when games return.
    - Derived folders cannot be renamed, deleted or manually emptied. Removing a game from a manual
      collection never deletes the game, its ROM or any saves. Deleting a manual folder deletes only
      the collection. A user can favorite and pin either kind of folder; games remain pinnable too.
    - Mobile currently stores manual folders, pins and folder favorites locally. If account-wide
      synchronization is implemented, make it an additive, announced capability such as
      `library_collections`; define a migration that imports local data with consent and never
      silently replaces it with an empty server list. Keep offline local behavior when absent.
    - For that optional contract, provide authenticated CRUD for manual collection name and ordered
      game IDs, collection favorites and an explicitly ordered home-pin list. Catalogue is a fixed
      client entry and cannot be pinned, moved or deleted. Validate names/lengths/count limits,
      account ownership, game references and unique pin keys. Do not store pixels or screen positions.
    - Use revision/ETag preconditions and atomic updates so two clients cannot lose edits. Define
      deletion tombstones and stale-reference cleanup; an offline edit must not resurrect a deleted
      collection without a conflict decision. Include the feature in library invalidation/SSE and
      exports/backups. Reject mutation of derived console membership even through manual endpoints.
    - Cover idempotent retries, duplicate IDs, unknown games, cross-account access, merge rewrites,
      revoked tokens, concurrent reorder/delete, offline replay and migration from local-only data.

14. [x] **Activity ordering: downloads and play.**
    - Keep `last_played_at` and played time as play/session facts. Do not change them when a ROM is
      downloaded, pinned or favorited. Expose a separate server installation/completion timestamp
      if catalogue/library recency needs it; document timezone and ordering/tie-break semantics.
    - Distinguish server installation, download to one phone, and game launch. A completed server
      job is not proof a phone has the ROM, and requesting a download is not a completed download.
      The client already knows verified local download completion and first rendered local play.
    - Current client fallback combines those local timestamps with server play time. No new write
      endpoint is required for that device-local sort. If cross-device activity is added, announce
      it separately and accept idempotent, device-scoped events with stable event IDs; reject
      arbitrary future timestamps and preserve offline replay without counting play twice.
    - Return activity changes through conditional library reads/SSE. Sorting must remain stable
      through pagination, equal timestamps, timezone changes and account switching. A finished
      download must not disappear just because `last_played_at` is still null.

15. [x] **Disc/ROM packaging, BIOS and save compatibility.**
    - Keep PSX CUE/BIN and Dreamcast GDI track sets complete in validated archives. Validate every
      descriptor/playlist reference, reject absolute paths, traversal, symlinks, missing tracks,
      duplicate/conflicting names and oversized expansion. Preserve relative paths and filename
      case; do not return only one track of a multi-track game or guess a console from `.bin` alone.
    - Multi-disc playlists must list the intended order and all discs. If returning a content
      manifest, announce it and include relative paths, sizes and hashes. Existing ZIP downloads
      remain supported by old clients. The client extracts the bundle and selects its descriptor;
      the server does not need to unpack it differently for each emulator.
    - Keep the original downloadable ROM SHA as the synchronization identity. Extracted CUE/GDI
      file hashes must not replace it. Changed ROMs invalidate derived client extraction, but do
      not discard prior saves. Keep progression/version selection explicit when content differs.
    - Verify PSX BIOS descriptors and authenticated downloads, including expected names, checksums
      and archive paths. Missing user-provided BIOS must produce an actionable response; never
      distribute proprietary firmware automatically or mark an absent file as installed.
    - Extend these rules to 3DS/PS2 using request 10. Tests must include large/ranged/resumed ROM
      downloads, checksum mismatch, changed-ROM restart, conflicting equal-content saves, unsafe
      archives, mixed-case tracks, multiple discs, unsupported encrypted content and maintenance.

16. [x] **Catalogue filter for games already downloaded on the server.**
    - Reuse the existing `installed` catalogue filter; first verify its implementation instead of
      adding a duplicate endpoint. `true` means usable ROM content installed on the server, not
      merely favorited, imported as metadata, queued, or downloaded to the phone.
    - Preserve `true` / `false` / omitted behavior, counts and pagination. Define installed status
      for merged games with multiple versions consistently between list, detail and download routes.
      A failed/unfinished job must not be reported as a completed installation.
    - Combine the filter with search, console/generation, language, source and enabled-provider
      restrictions. Installed personal-library content must remain accessible under the existing
      disabled-source rules. Verify that filtering does not leak other accounts' library state.
    - The mobile search needs all matching results via documented pagination and stable ordering;
      do not silently cap responses to 40 or discard the query after the first result. Search
      persistence and scrolling themselves are client UI responsibilities.

17. [x] **Per-console emulator capabilities and settings metadata.**
    - Discovery remains authoritative for accepted `X-Emulator-Core` names and save formats. An
      optional native capability descriptor may identify supported renderer APIs, ABI/platform,
      controller/analog features, dual-screen/touch features, savestates and required resources.
      Unknown optional fields must remain harmless to old clients.
    - If offering option metadata, version it by console, core and core build. Categorize Image,
      Audio, System, Controls and Advanced, with value labels, defaults, valid values and whether
      restart is required. Mark device-dependent options instead of applying one GPU's choice to
      every phone. The client must fall back to the loaded core's own variables when absent.
    - Emulator settings are device preferences. Do not globally share a graphics driver, internal
      resolution or core-specific key between all consoles. Optional account preferences require
      explicit per-console/per-core keys and must not overwrite device compatibility overrides.
    - All display controls belong to one client page: rotation/aspect, internal resolution, filters,
      touch-control placement and, for DS/3DS, screen arrangement and touch-screen selection. The
      server must not create two competing preference fields for the same setting.
    - Include reset/migration tests, unknown keys/values, a changed selected core, retired options,
      old clients and different renderer capabilities. Do not label a core as tested merely because
      a download artifact or an option definition exists.

18. [ ] **Autosave/session integrity on Home hold, crash and offline return.**
    - Reuse the existing save/session APIs and retain their base revision, epoch and idempotent
      client-session behavior. A three-second Guide/Home hold is handled by the app: it saves and
      closes the game before returning to the library. No new remote controller-command API is needed.
    - Verify retry/reconnect after a final offline save, stale epochs, revocation while exiting,
      maintenance and interruption between save upload and session submission. Preserve the last
      good revision if native rendering or the process fails; do not fabricate a successful save.
    - Cross-core states remain incompatible unless explicitly proven. A same-content conflict may
      resolve automatically by checksum; different progressions require the player's decision.
      SRAM/card/directory compatibility must follow the declared per-console format in request 10.
    - Ensure an unexpected GameCube exit can be diagnosed without treating it as an intentional
      library navigation or resetting progress. A graphics/input crash requires native/device logs;
      backend success cannot establish that the S25 Ultra video or controller problem is fixed.

### Coverage of the player's interface requests

| Request | Server responsibility | Client responsibility |
|---|---|---|
| Compact search/avatar/time/battery bubbles; avatar ring | Existing avatar assets/account contract; no new endpoint | Sizes, safe areas, visible ring |
| Stable bold section labels | None | Reserve text bounds |
| PS5-style Home; Catalogue first tile; fixed enlarged second slot | Stable library/console identities | Horizontal carousel, transition and focus anchor |
| Portrait library instead of console UI | Preserve searchable/paginated library data | Full-width vertical cover grid and scrollable game page |
| Permanent automatic console folders | Request 13 if synchronized; otherwise derived locally | Derive membership; no delete action; horizontal folder strip |
| Console icons from GitHub, consistent with app strokes | Request 11 optional versioned metadata | Bundle licensed vector assets and notices; work offline |
| PSP-style vertical game carousel inside folders | Stable ordered game identities; existing title/year/genre/play-time fields | Small title beside its cover; marquee only for overflow; lightweight bottom-right metadata, right aligned, one value per line; back/focus restoration |
| Controller hold acceleration/inertia | None | Time-based repetition, interruptible motion, reduced-motion support |
| Manual folders in favorites/Home | Request 13 optional synchronized collections | Local CRUD, favorite, membership and deletion without deleting games |
| Pin/reorder games and folders | Request 13 optional ordered keys | Catalogue stays fixed; explicit left/right order |
| Recent download/play ordering | Request 14; keep play facts distinct | Verified local completion and offline play activity |
| Installed-on-server catalogue filter | Request 16; existing filter regression checks | Direct filter chip and retained query |
| Resume button clipped by phone cutout | None | Safe insets and focus/scroll padding |
| Search retained after validation; scroll all results | Request 16 pagination/search contract | Keep field/results open; scroll; no focus-triggered reopening |
| Favorite fills only the heart | Existing game favorite endpoint; optional folder favorite in 13 | Icon fill without recoloring entire control |
| Uneven grid rows cannot navigate down | None | Clamp to nearest existing column and scroll focus into view |
| PSX failure on S25 Ultra | Request 15 for valid content/BIOS; 12 for diagnostics | Disc preparation, native/core/driver validation on the device |
| N64 saturated/crackling audio | None beyond retaining correct core identity | Audio buffering/resampling; verify actual game/device audio |
| GameCube black video with audio, button-triggered exit | Requests 12 and 18; preserve saves | Native renderer/input diagnostics and actual hardware reproduction |
| Pause menu adapted to landscape/controller and portrait/touch | Request 17 optional metadata only | Distinct layouts, clear categories, focus and reachable value selectors |
| Emulator settings isolated; Advanced last | Request 17 if metadata/preferences are served | Persist by console and core; validate against active core |
| Merge the two screen-settings pages | Avoid duplicate preference contracts in 17 | One display page, including dual-screen controls |
| Show original console controls and highlight Bluetooth input | Request 17 optional controller capabilities | Per-console diagrams, remaps, sticks/triggers and pressed states |
| Short Guide opens menu; hold 3 s saves/quits to Home | Existing APIs; request 18 regression checks | Hold timer, consume release, finish saving before navigation |
| 3DS and PS2 | Request 10 and associated 15/17/18 changes | Verified native integration, input/display and save-directory adapters |

### Completion requirements for the server work

- Update `docs/native-api.md` as the authoritative contract, API schemas and feature discovery in
  the same server change. Supply exact example payloads and compatibility/fallback rules. Document
  the final console/core names; the app must not infer them from a filename or a provider label.
- Add database migrations only for persisted new state. Preserve existing account/library/save
  data, ensure backups/restore cover new entities and document rollback constraints.
- Exercise native bearer-token and browser-session authorization, CSRF where relevant, account
  isolation, revoked devices, conditional reads, SSE invalidation and optional features absent.
- Run the server's required lint/type/unit/integration/browser checks and inspect relevant web
  screenshots if its UI changes. Record exact new validation results separately from historical
  requests 1–9 below. Do not reuse their old counts as evidence for requests 10–18.
- Do not mark any new request complete until its stated acceptance criteria and contract tests
  pass. Record remaining client/device work explicitly; server completion is not app validation.
- No production deployment or release is authorized merely by this specification. Prepare a
  reviewable server change using the server repository's own delivery instructions.

## Server implementation verification — requests 10–18 (2026-11-13)

The server changes were published and deployed as **2026.46.6** on 2026-11-13, after explicit
production deployment authorization. [Forgejo run 21](http://192.168.0.151/simonverbois/retrohub/actions/runs/21)
passed quality, image publication and deployment. A complete verified pre-migration backup was
created; all six production pods are ready, PostgreSQL was not restarted, and authenticated
desktop/mobile browser checks passed. No production metadata batch or manual merge was triggered
by the deployment checks. Checked items 11 and 13–17 mean their server scope passed; they do not
certify the Android interface or native host.
Requests 10, 12 and 18 remain unchecked because their physical-device acceptance is still pending,
although their server contracts and regression tests are implemented.

- Authoritative contract: [`../retrohub/docs/native-api.md`](../../retrohub/docs/native-api.md).
  The additive discovery flags cover console metadata, native contracts, private system resources,
  capabilities, diagnostics, collections and server installation activity. Final IDs are `3ds` /
  `azahar` and `ps2` / `armsx2`; neither console advertises browser play.
- Content tests cover original generated NCCH/NCSD/3DSX, ISO9660 and actual CHD files, encrypted
  content rejection, safe complete disc bundles, case/order, private BIOS/resources, a ROM over
  2 GiB, ranged/resumed transfers and the unchanged downloadable-object SHA identity.
- Save tests cover game/ROM-bound archive manifests, 3DS title isolation, PS2 card files, core/build
  compatibility, quotas, interrupted uploads, last-good revision, conflicts, epochs, maintenance,
  revocation, offline-return retries and idempotent sessions. PS2 cards must be dedicated to one
  game by the client; their internal filesystem is not certified by the server. Initial 3DS saves
  support validated title data directories, excluding whole NAND and unverified extdata.
- Collections use atomic revision/ETag snapshots, deletion tombstones and merge rewrites. Tests
  cover account isolation, stale edits, concurrent updates, revoked devices, conditional reads,
  SSE invalidation and backup/restore of the new entities and private resources. Optional feature
  absence retains the client's local fallback; local-data import requires client consent.
- Native option metadata is deliberately absent (`options: null`, source `loaded_core`). Device,
  console, core and build scope is declared; the actual core remains authoritative for values,
  reset and retired/unknown options. No global GPU preference is introduced.
- Backend quality: Ruff lint/format and mypy passed; complete SQLite suite **264 passed, 6 skipped**
  (PostgreSQL-only cases), complete PostgreSQL 17 suite **270 passed**. Fresh migration upgrade,
  Alembic schema comparison and full downgrade passed. The Python wheel includes the icon licence
  and new modules, without 3DS/PS2 firmware.
- Frontend quality: typecheck, formatting and production build passed; Playwright **29 passed**.
  Relevant desktop/mobile screenshots and the new scheduler in both themes were inspected.
- Additional player requests are implemented: multilingual scored reversible merges, IGDB alias
  lookup and catalogue-wide enrichment, immediate metadata sync and optional job time windows.
  See [`metadata-and-scheduling.md`](../../retrohub/docs/metadata-and-scheduling.md).
- Production logs were inspected read-only. Regression fixes cover interrupted-upload handling,
  overlapping cancelled/retried download temporary files, and sanitized HTTP logging. Details,
  evidence limits and the client/device validation matrix are recorded in
  [`native-server-validation-2026-11-13.md`](../../retrohub/docs/native-server-validation-2026-11-13.md).

Still required in the app: native 3DS/PS2 adapters and atomic restore on real cores, device-scoped
settings/reset validation, physical-phone video/input reproduction (including S25 Ultra GameCube),
and actual Guide/Home hold save-and-exit behavior. No server test establishes that a phone renders
these consoles or that a graphics driver problem is fixed.

## Historical verification — requests 1–9 only

Run from `../retrohub`:

- `uv run --directory backend ruff check src tests migrations`
- `uv run --directory backend ruff format --check src tests migrations`
- `uv run --directory backend mypy src`
- `uv run --directory backend pytest`: **217 passed, 3 skipped** (PostgreSQL-specific tests).
- Frontend `npm run typecheck`, `npm run format:check`, `npm run build`, then
  `npx playwright test`: **27 passed**. Pairing and avatar screenshots were inspected in both themes.
- Compose configuration rendering checked with optional Android fields unset and configured.
- Forgejo production pipeline **2026.46.5**: quality (including PostgreSQL), image publication
  and deployment all succeeded ([run 20](http://192.168.0.151/simonverbois/retrohub/actions/runs/20)).
- Live `/health` is healthy, `/native/status` reports **2026.46.5**, `library_events` and `account_avatars`, and
  all application pods are ready. The PostgreSQL pod identity and restart count were unchanged.

The verification above covers server requests 1–9. Request 8's separate local client validation
is recorded in the request itself. The app now consumes the SSE capability;
older servers and interrupted streams retain conditional polling. Protocol, coalescing, reconnection,
revocation and polling fallback are covered separately in the app unit tests; the Android end-to-end
journey checks live imports against the real stream and suspension during gameplay. No Android
build or physical-camera test is claimed by the server validation above.
