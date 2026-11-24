# Interface architecture

RetroHub has two interfaces built from the same Compose Multiplatform module (`shared/`):

- **Mobile** (portrait): a phone app with a section bar, cards and lists.
- **Console** (landscape): a handheld-console interface inspired by the PSP and the PS5, driven by a
  controller first, with touch and keyboard as equals.

They share state and logic, never layouts, so a change to one cannot move the other.

## Packages

All under `shared/src/commonMain/kotlin/ovh/verbois/retrohub/ui/`:

| Package | Content |
| --- | --- |
| `ui.mobile` | Portrait only: shell, section bar, library grid, game page, portrait-only menus. |
| `ui.console` | Landscape only: shell, XMB home, control center, full-screen pages, console widgets. |
| everything else under `ui` | Common: `UiController` and other state, navigation (`nav`), theme, icons, low-level components, the game screen (`play`), and screens both interfaces still render. |

Two entry points know both interfaces and choose one from the orientation: `RetroHubRoot.kt` and
`AppShell.kt` (`AppShell`, `AppScreen`, `HomeScreen`).

## Rules

- `ui.console` never references `ui.mobile`, and `ui.mobile` never references `ui.console`.
- Common code references neither, except the two entry points.
- `InterfaceSeparationTest` (JVM) fails the build on any violation, including fully qualified names.
- Logic both interfaces need (filters, actions, models) lives in common code as plain state or
  functions; each interface draws it its own way.
- A common screen that one interface replaces moves to the other interface's package once nothing
  else uses it.

## Adding a screen

1. Put shared state or logic in common code (`ui.<feature>` or the domain packages).
2. Draw it in `ui.console` and/or `ui.mobile`.
3. Route it in `ConsoleScreen` (`ui.console.ConsoleScreens.kt`) and/or `MobileScreen`
   (`ui.mobile.MobileScreens.kt`).

## Console layout rules

- **One screen, no scrolling** for menus and pages: landscape phones are short, so `ConsoleDensity`
  (common, used by the console style scope and the in-game menu) draws the console interface for a
  470 dp tall screen and scales it down to fit, never below 80 %. Touch controls in a game keep the
  real density (`LocalBaseDensity`). Prefer tiles and side panels over long lists; only open-ended
  lists (saves, notifications, a long settings section) may scroll.
- **Focus** turns a control white (no outline); a chosen value keeps a cyan mark.
- **Round icons** (`RoundIcon`) show their label only with the focus, under them (header) or above
  them (control center).
- **Tabs** (`ConsolePage`) switch the content as soon as they get the focus; Up from the content
  returns to the current tab, and new content never takes the focus away from the tabs.
- **Text fields** open the keyboard only with A or a tap; the keyboard's « Next » goes on typing in
  the next field.
