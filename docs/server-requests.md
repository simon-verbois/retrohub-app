# Requests to the RetroHub server

Needs found on the app side. Checked items are implemented and tested in the `../retrohub`
server as of **2026-11-11**, deployed to production in **2026.46.4** (requests 1–6 first shipped
in **2026.46.2**, request 7 in **2026.46.3**, request 8 in **2026.46.4**).
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
   - Selected upstream: [DiceBear](https://github.com/dicebear/dicebear), whose generator is
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
   - `GET /account/avatars` returns `{"avatars": [{"id": "pixel-art-001", "name": "Pixel 1",
     "url": "/api/v1/account/avatars/pixel-art-001.png?v=<content-hash>"}], "default_avatar": <item>}`.
     The default is one of the catalogue items. Return the complete bounded catalogue in stable
     order. Names are short and readable; IDs are opaque to clients.
     Images are served by RetroHub as PNG with immutable, versioned URLs, not arbitrary remote URLs
     or executable SVG content. The app accepts only `/api/v1/account/avatars/…` image paths.
   - `GET /account/avatar` returns `{"avatar": <catalogue item or null>}` for the authenticated
     user (the chosen image, or the resolved default). `PUT /account/avatar` with
     `{"avatar_id": "pixel-art-001"}` returns the same shape after saving. `{"avatar_id": null}`
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

## Verification

Run from `../retrohub`:

- `uv run --directory backend ruff check src tests migrations`
- `uv run --directory backend ruff format --check src tests migrations`
- `uv run --directory backend mypy src`
- `uv run --directory backend pytest`: **211 passed, 3 skipped** (PostgreSQL-specific tests).
- Frontend `npm run typecheck`, `npm run format:check`, `npm run build`, then
  `npx playwright test`: **27 passed**. Pairing and avatar screenshots were inspected in both themes.
- Compose configuration rendering checked with optional Android fields unset and configured.
- Forgejo production pipeline **2026.46.4**: quality (including PostgreSQL), image publication
  and deployment all succeeded ([run 19](http://192.168.0.151/simonverbois/retrohub/actions/runs/19)).
- Live `/health` is healthy, `/native/status` reports **2026.46.4**, `library_events` and `account_avatars`, and
  all application pods are ready. The PostgreSQL pod identity and restart count were unchanged.

The verification above covers server requests 1–8. Request 8's separate local client validation
is recorded in the request itself. The app now consumes the SSE capability;
older servers and interrupted streams retain conditional polling. Protocol, coalescing, reconnection,
revocation and polling fallback are covered separately in the app unit tests; the Android end-to-end
journey checks live imports against the real stream and suspension during gameplay. No Android
build or physical-camera test is claimed by the server validation above.
