# xagent

A Rabbit-r1-inspired Android launcher that orchestrates **Paseo** coding-agent
sessions running on a host machine. (The app is called **xagent**; **Paseo** is
the daemon it talks to.)

xagent is aimed at **physically small Android devices** — pocket-sized screens
where a one-handed, scroll-and-speak remote controller is the right shape. The
device is a thin control surface (voice + touch). The Paseo daemon — which
actually runs the agents (Claude Code, Codex, OpenCode, Copilot, Pi) — lives on
your dev box or a server. The device pairs over LAN / Tailscale, or via the
E2EE Paseo relay (QR pairing).

**North star:** rabbitOS (the interaction shell — Home launcher, push-to-talk,
scroll/swipe card nav, card-based results). Not the r1 cloud backend.

## Status

L0–L3 (UI/stub) and I0a–I0e (daemon integration) are **complete and on-device**.
Multi-connection support (DIRECT + E2EE RELAY) is **complete and verified** —
two simultaneous daemon connections merge their agent decks on-device.

See [`docs/mvp.md`](docs/mvp.md) for the increment plan (L0–L4 UI → I0–I2
integration), [`docs/card-model.md`](docs/card-model.md) for the card UX spec,
and [`docs/user-testing-protocol.md`](docs/user-testing-protocol.md) for how
each increment is tested.

### Reference canvas

Every composable is previewed at `widthDp = 270, heightDp = 584` — a
representative small-device canvas (~4", xhdpi, one-handed thumb arc). The app
runs on any Android device with `minSdk = 26`; the geometry is tuned for the
small form factor.

## Build

Toolchain is provided by Nix. From the repo root:

```sh
nix develop                         # enter devshell (jdk17, gradle, android-sdk, adb)
gradle :app:assembleDebug           # build the launcher APK
adb install -r apps/nx1-launcher/build/outputs/apk/debug/app-debug.apk
```

Or with the Gradle wrapper (no Nix required — needs JDK 17 and ANDROID_HOME):

```sh
./gradlew :app:assembleDebug
```

CI is configured in [`.github/workflows/android.yml`](.github/workflows/android.yml):
every push to `main` and every PR build a debug APK and upload it as a workflow
artifact; every tag matching `v*` publishes a GitHub Release with the APK
attached.

Previews (no device needed) live in
`apps/nx1-launcher/.../ui/components/AgentCard.kt` — `@Preview(widthDp = 270,
heightDp = 584)`.

## Layout

```
apps/nx1-launcher/      Kotlin + Compose launcher
  src/main/kotlin/sh/paseochat/launcher/
    ui/components/       AgentCard, ConnectionCard, QrScanner, AppsCard, SettingsCard
    ui/screens/          LauncherScreen (custom DeckScroller)
    daemon/              PaseoDaemonClient, ConnectionManager, E2eeCrypto
    model/               Agent, ConnectionProfile, ConnectionOffer
  src/main/AndroidManifest.xml   CATEGORY_HOME launcher
docs/                   mvp plan, card-model spec, user-testing protocol
flake.nix               devshell (per AGENTS.md)
```

## License

AGPL-3.0-only — see [`LICENSE`](LICENSE). Matches Paseo (`getpaseo/paseo`).
