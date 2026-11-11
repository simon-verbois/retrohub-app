# RetroHub for Android

The Android app for RetroHub (iOS prepared): your RetroHub library in a handheld-console interface,
with built-in emulators (NES, Super Nintendo, Game Boy / Color / Advance, Master System, Mega Drive,
PlayStation, Nintendo DS, Atari 2600, Nintendo 64, GameCube, Dreamcast), offline play and progress
synced with the web.

It is a client for the [RetroHub](https://github.com/simon-verbois/retrohub) server
([overview](https://simon-verbois.github.io/retrohub/)): the library, games and saves come from it.
App website: https://simon-verbois.github.io/retrohub-app/

<a href="https://buymeacoffee.com/simonverbois"><img src="docs/buy-me-a-coffee.png" width="178" height="50" alt="Buy Me a Coffee"></a>

The app interface is in French for now; more languages will follow.

## Install

Each version's signed APK is published in the
[GitHub releases](https://github.com/simon-verbois/retrohub-app/releases), with its SHA-256 sums.
You need a RetroHub server: the app links to it with a pairing code shown on the web
(Settings → Apps and devices) or with your password.

## Develop

### Requirements

Linux x86_64 with KVM (`/dev/kvm`), `curl`, `unzip`, `uv`, and the `../retrohub` repository for the
end-to-end tests. No Android Studio or Gradle installation needed.

### Get started

```sh
scripts/setup-sdk.sh        # JDK 21 and Android SDK in ~/Android (idempotent)
scripts/fetch-cores.sh      # libretro cores verified against cores.lock
./gradlew :app:assembleDebug
scripts/emulator.sh up && scripts/emulator.sh wait
scripts/test.sh             # unit, instrumented, e2e (or a single level)
scripts/ci.sh               # the whole chain, cleanup included
scripts/start-retrohub.sh   # emulator window, landscape, app launched
```

`scripts/start-retrohub.sh --backend` also starts a disposable RetroHub server
(`http://127.0.0.1:8011`, account `e2e-admin` / `test-browser-password-only`); `--fresh` starts over
from a first launch.

### On a real phone

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch: server address, then pairing code (web: Settings → Apps and devices) or password,
then three starting settings.

For a lighter APK targeting a single device type: `./gradlew :app:assembleDebug -Pretrohub.abis=arm64-v8a`.

See `docs/` for design decisions, licences and server requests.

## License

Code under the MIT license (`LICENSE`). The APK bundles LibretroDroid (GPL-3.0) and libretro cores
under their own licenses: see `docs/licenses.md`.

---

Personal project, developed with the help of AI. It is experimental and may contain
errors: please review the code and report any issues you find.
