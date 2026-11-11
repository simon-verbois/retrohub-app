# Changelog

What's new in the RetroHub app, written from the player's point of view.

<!--
Every visible change adds its note in the same commit, under the Unreleased heading.
Sections, in order: Added, Changed, Fixed, Removed, Internal (when needed). One bullet per
change, in plain text. Never rewrite the notes of a published version.
-->

## [Unreleased]

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
