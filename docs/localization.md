# Adaptive text and localization

French remains the application's default language. The first shared Compose string catalogue is
`shared/src/commonMain/composeResources/values/strings.xml`. It covers the welcome screen, setup
wizard, new settings explanations, and download confirmation. This is the starting point for
localizing the rest of the application, not a complete language switch.

## Adding languages

Add the same keys in `composeResources/values-en/strings.xml`, `values-de/strings.xml`, etc.
Compose resources select the platform locale on Android and iOS and fall back to the default
catalogue for missing keys. Keep resource identifiers stable and translate complete messages:
`setup_greeting` uses `%1$s`, and `setup_progress` uses `%1$d` and `%2$d`. Never concatenate a
translated sentence, insert manual line breaks to fit a particular phone, or translate serialized
enum names, core identifiers or server API values.

Migrate existing labels, enum presentation, errors and formatting to resources incrementally.
Before advertising a language or adding an in-app language selector, finish its catalogue and
check plural rules, dates, numbers and units. The game's language preference is a separate setting;
it must never be reused as the interface language.

## Layout rules

- Let interface labels, selected settings values and descriptions wrap at the user's text size.
  Use minimum heights, never a fixed height for translated text. Do not shrink text to conceal
  an overflow or override Android's font scaling.
- Put a setting's selected value below its title, with its short explanation underneath. Keep only
  compact controls such as switches and disclosure arrows at the trailing edge.
- Keep recommendation badges separate from option labels. All options remain available through
  scrolling on a short display.
- Action groups measure their actual labels with the current font scale. When they cannot fit
  side by side, stack them and let the primary button grow vertically.
- Setup has a scrollable body and a separate action area. Settings and dialogs scroll naturally.
  Keep system insets around navigation and controls, even when the game image uses the camera area.
- Ellipsis is reserved for compact library summaries where opening or selecting the game reveals
  its full title. It must not hide an action, preference or selected value.

## Verification

`AdaptiveLayoutTest` checks a 320 dp portrait viewport, 200% text, a long account name, expanded
German-like messages, right-to-left layout, and both sky themes. It inspects Compose text layout
results for actual clipping, and saves screenshots under the application's `test-screens` directory.
The complete end-to-end journey also verifies portrait and landscape screens and Android's back
gesture. Review screenshots as well as test results; a message fitting in its layout is necessary
but not sufficient for a readable interface.
