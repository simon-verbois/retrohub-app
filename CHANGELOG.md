# Changelog

What's new in the RetroHub app, written from the player's point of view.

<!--
Every visible change adds its note in the same commit, under the Unreleased heading.
Sections, in order: Added, Changed, Fixed, Removed, Internal (when needed). One bullet per
change, in plain text. Never rewrite the notes of a published version.
-->

## [Unreleased]

## [2026.46.9] - 2026-11-12

### Added

- Choose Comfort, Full screen or Compact touch controls with visual previews, a per-console override and an in-game switch; keep custom positions separate for each preset and orientation.

### Changed

- Enlarge the game area with more compact touch clusters, reposition shoulder and system buttons, and keep Menu and Fast forward in a discreet clock-style capsule at the upper-right corner.
- Apply the azure cyan palette in day and night modes, strengthen secondary text contrast and give buttons, search and status controls a shared glossy, finely textured finish with cyan focus lighting.
- Give profile shortcuts soft gradient shading and lit edges, and point the menu toward the profile button.
- Replace the profile list with a compact quick-action grid, account header and synchronization footer, with directional controller navigation and a layout that resizes and rearranges to keep every action visible without scrolling.
- Replace the portrait app name and second navigation row with a dropdown for Home, Favorites, Downloads and Catalogue.
- Make the search and clock/battery bubbles more compact, keep comfortable touch targets, and show the profile picture at the same diameter without an extra background.
- Expand search to the left inside the top bar and show matching games in an aligned dropdown without replacing the current page.
- Use cover grids for downloaded games and put a compact storage card first, with detailed usage available from that card.
- Replace white controller focus outlines with a soft highlight and scale, including on selected home covers.
- Bring the home title and game information closer together and enlarge covers downward while keeping their top spacing.

### Fixed

- Trigger controller actions only once per A press, including favorite toggles and confirmations.
- Keep the chosen home cover selected and focused when returning from settings or opening a game outside the visible row.
- Keep controller shortcuts inside the active menu or dialog, restore focus after inline choices, support A consistently in dropdown menus, limit shoulder navigation to the four tabs, and return through pause submenus before resuming a game.
- Clearly show whether the selected game is a favorite on the home action button.
- Let home covers scroll to the screen edges when full-screen display is enabled, while keeping the header and game actions clear of the camera cutout.

### Internal

- Restart the owned test emulator before optimized-build checks to isolate native rendering state between variants.

## [2026.46.8] - 2026-11-12

### Added

- Browse the full server catalogue from a tab after Downloaded, with search, console, language, source and installation filters, version and file selection, server download progress, and a separate download to the phone.
- Rotate the game image by 0°, 90°, 180° or 270° from the pause menu or game options, remember the choice per game, and keep the DS stylus aligned with the rotated image.
- Group the clock, phone battery and system-reported controller battery in a compact status pill.

### Changed

- Reorganize game options into adaptive columns, keep the game header compact, expose all four rotations directly, and distinguish game removal from other actions.
- Make settings rows and headers more compact, align selected values on wide screens, and remove explanatory text from device and console settings.
- Open a smaller profile menu over the current page, preserve its content and focus, and dismiss it with Back, Start or an outside click without a heading or close button.

- Hide Android system bars throughout the app, with temporary access by swiping from an edge.
- Keep the home screen within the available height, resize covers to fit the game information and actions, and retain horizontal game browsing.
- Maximize the game image using the space needed by the actual touch controls, without an extra top margin, and choose the larger usable layout in landscape.

### Removed

- Stop showing the changelog automatically at startup; keep it available in About settings.
- Remove offline badges and downloaded-game dots from covers; keep undownloaded games gray.

## [2026.46.7] - 2026-11-12

### Added

- Choose a profile picture from the server-hosted avatar catalogue in Account settings, with cached images and an initials fallback offline.

- Confirm before closing the app, with Cancel focused by default for controller navigation.
- Update the visible library instantly through the server event stream, with coalesced conditional reads, automatic reconnection and a ten-second polling fallback for older servers or interrupted streams. Suspend updates in the background, offline and during gameplay.

- Scan one-use pairing QR codes with the Android camera, with manual-code and password alternatives.
- Add short explanations to device and console settings, and a global option to use the camera-cutout area for the game image while protecting controls.
- Require confirmation before manually downloading a game and show download progress directly on its cover.
- Add an explicit play button to the selected game; selecting a cover or touching its artwork no longer starts a game.

### Changed

- Balance the top bar with equally sized search and profile visuals, a smaller status badge, tighter regular spacing and matching touch targets.

- Make the profile status badge smaller and place it at the upper-right corner of the avatar.

- Mark the active section with bold text instead of a rounded background, while retaining the controller focus outline.
- Place the clock to the right of the profile icon in the top bar.
- Order sections as Home, Favorites, Downloads, Search and Profile without wrapping; keep the library inside Home.
- Enlarge home covers, make library covers more compact and gray out games that are not downloaded.
- Present game information and play time below the title, with a single Play or Resume action and save-choice prompts at launch.
- Move library filters to a side button, remove game counts and replace the controller help background with compact icon hints.
- Show connection and synchronization status on the profile avatar; simplify the profile menu and remove the separate settings and sync icons.

- Rebuild welcome and setup screens with centered content, readable opaque surfaces, natural text heights and actions that reflow for longer labels.
- Let settings titles, selected values, recommendation badges, buttons and menus adapt to small screens and larger text.
- Begin a shared French string catalogue for setup and settings explanations, with a documented path to additional languages.

### Fixed

- Link controller focus explicitly from sections to covers to game actions, returning to the same game and keeping horizontal movement inside each row.
- Keep the background continuous behind the top bar and camera cutout, and make profile and settings content scroll into view with a controller.

- Let Android back gestures navigate immediately in the application by reserving immersive mode for gameplay, and restore controller focus when the window becomes ready.
- Avoid blocking the interface on queued emulator operations and coordinate game resets with snapshots.
- Restart Nintendo DS from an initial machine checkpoint, preserving battery saves, instead of dereferencing invalid game metadata in the legacy core reset callback.

### Removed

- Remove selected-game background cover artwork and the redundant resume, play-time and synchronization activity tiles.

### Internal

- Add emulator regression coverage for DS background saves and repeated resets, long labels, large text and right-to-left layouts.

## [2026.46.6] - 2026-11-11

### Changed

- Rebuilt the app website around large, expandable screenshots, a browsable app preview, clearer installation steps and a restrained layout for phones and desktops.
- The website now focuses on seven Western European languages instead of twelve.
- Save and restore games without blocking navigation or reading the previous save into memory.
- Keep library filters, sorting, scroll position and game focus when returning to a screen; prepare sorting and search away from the interface thread.
- Show smoother download progress and automatically resume interrupted downloads when the network returns.
- Reduce the application size with optimized release builds and removal of the unused Material icon collection.

### Fixed

- Identify saves with the ROM actually running on the phone, and refuse incompatible manual save states.
- Release held buttons, turbo and sticks when pausing or disconnecting a controller, independently for each player.
- Finish the final save before closing the emulator, return to the library after a backgrounded game, and record each completed session only once.
- Merge repeated background sync requests while preserving a follow-up for changes made during a sync.
- Move ROMs through a verified temporary file and clear artwork through its cache manager.

### Removed

- Unused game-detail caching, navigation routes, interface helpers and obsolete dependency declarations.

### Internal

- Exercise optimized builds, native cores and the complete server journey in the Android emulator as part of local CI.
- Add regression coverage for large snapshots, save compatibility, input release, download progress and retries, background sync and large catalogue search.

## [2026.46.5] - 2026-11-11

### Changed

- The app is now signed with its own key: uninstall versions up to 2026.46.3 before installing this one.

### Internal

- The release pipeline reads its configuration from Forgejo variables.

## [2026.46.4] - 2026-11-11

### Changed

- The app is now signed with its own key: uninstall versions up to 2026.46.3 before installing this one.

## [2026.46.3] - 2026-11-11

### Internal

- First version published on GitHub with its signed APK, after an interrupted publication.

## [2026.46.2] - 2026-11-11

### Changed

- The presentation website is available in twelve languages.
- The repository documentation is now in English.

## [2026.46.1] - 2026-11-11

### Added

- First version of the RetroHub app: a home screen in the style of a home console, with a row of covers, the selected game's artwork in large, its play time, its latest save and its sync status. Tapping the game launches it; a long press (or the □ button on a controller) opens its menu: new game, favourite, options.
- NES, Super Nintendo, Game Boy, Game Boy Color, Game Boy Advance, Master System, Mega Drive, PlayStation, Nintendo DS, Atari 2600, Nintendo 64 and GameCube games play directly on the phone, even offline; Dreamcast will follow as soon as the server supports it.
- The phone links to the account with a pairing code shown on the web, or with the password.
- A guided first launch sets how you play (touch, controller or both), performance (Balanced by default) and comfort, on a single screen without scrolling.
- Saves and play time sync with the web: a game started on the phone continues in the browser, and the other way round.
- When a save has moved on both on the phone and elsewhere, both versions are shown side by side and nothing is replaced without your choice.
- The in-game menu offers ten save slots, fast-forward, restart and sync on exit; the game is saved automatically.
- Touch controls faithful to each original controller, hidden as soon as a controller is connected.
- The two Nintendo DS screens stack in portrait and sit side by side in landscape, with an optional large main screen or top screen only.
- The interface uses the website's animated blue sky, in light or dark (following the phone by default, adjustable in Appearance).
- Each console has its own settings: emulator, image rendering (sharp, smooth, CRT, LCD, high definition), 3D console resolution, widescreen and fast-forward speed.
- The library is filtered by console and sorted from a single button.
- Settings are organised in four parts: your RetroHub account (shared with the web), this device, consoles and the app.
- From the phone: change your password, see and close browser sessions, remove a device and show a code to link another one.
- The preference for French versions, the autosave interval and each console's emulator are the same as on the web.
- Background sync at an adjustable interval, a notification when a save needs your choice, and a game's save history with restore of an earlier version.
- Per console: pixel-perfect scaling, language, region, aspect ratio, fast boot or with the intro, frame skip, orientation, DualShock controller for PlayStation, turbo and controller buttons of your choice.
- In game: touch controls can be moved and resized by hand, emulator options are set from the menu, and cheat codes can be turned on or off at any time.
- A game's options gather its cheat codes, its save history, updating and removing it from the phone.
- Game rumble, stick dead zone and sensitivity, several players with several controllers, and a menu shortcut of your choice (Select + Start, guide button or L3 + R3).
- A home screen to your taste: game order, hidden consoles, downloaded games only, relaunch of the last game, text size and navigation sounds.
- Downloads continue with the app closed, with their progress in the notifications; games can go on the SD card, favourites can download on their own and forgotten games can make room.
- Parental control: a code protects the settings and the games of your choice.
- Battery saver, sound muted during fast-forward, confirmation before replacing a save, update notice in About, what's new for each version and a shareable log to report a problem.

### Internal

- Each version publishes its signed APK, its SHA-256 sums and the source code on GitHub, with a presentation website.
- Code shared with the future iOS app (Kotlin Multiplatform, Compose Multiplatform).
- Tests of the server contract, sync, controller navigation and a full journey against a real RetroHub server in an Android emulator.
