# xagent

A Rabbit-r1-style launcher for the **Bluefox NX1** (4" Android 15) that drives
**Paseo** coding-agent orchestration running on a host machine. (The app is
called **xagent**; **Paseo** is the daemon it talks to.)

The NX1 is a thin control surface (voice + touch). The Paseo daemon — which
actually runs the agents (Claude Code, Codex, OpenCode, Copilot, Pi) — lives on
your dev box or a server. The device pairs over LAN / Tailscale / the Paseo relay.

**North star:** rabbitOS (the interaction shell — Home launcher, push-to-talk,
scroll/swipe card nav, card-based results). Not the r1 cloud backend.

## Status

Building **UI-first**. The launcher scaffold exists; the card primitive is in
place and previews at the NX1 canvas (270×584 dp). See
[`docs/mvp.md`](docs/mvp.md) for the increment plan (L0–L4 UI → I0–I2 integration),
[`docs/card-model.md`](docs/card-model.md) for the card UX spec, and
[`docs/user-testing-protocol.md`](docs/user-testing-protocol.md) for how each
increment is tested.

## Build

Toolchain is provided by Nix. From the repo root:

```sh
nix develop                         # enter devshell (jdk17, gradle, android-sdk, adb, scrcpy)
gradle :app:assembleDebug           # build the launcher APK  (or: gradle wrapper && ./gradlew ...)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Previews (no device needed) live in
`apps/nx1-launcher/.../ui/components/AgentCard.kt` — `@Preview(widthDp = 270,
heightDp = 584)` matches the NX1 canvas.

## Emulator target

Create an AVD at **540×1168, xhdpi (~321 dpi), Android 15** to approximate the NX1.

## Layout

```
apps/nx1-launcher/      Kotlin + Compose launcher (Tier A; Tier B unlocks on rooted devices)
  src/main/kotlin/sh/paseochat/launcher/
    ui/components/AgentCard.kt   the card primitive + 6 states + NX1 previews
    ui/theme/                    r1-inspired tokens (orange/Paper/Ink)
  src/main/AndroidManifest.xml   CATEGORY_HOME launcher
packages/paseo-client/  Kotlin daemon client (WebSocket + auth + flows)   [I0]
docs/                   mvp plan, card-model spec, user-testing protocol
flake.nix               devshell (per AGENTS.md)
```

## License

TBD — likely AGPL-3.0 to remain compatible with Paseo (`getpaseo/paseo`).
