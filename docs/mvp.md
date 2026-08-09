# MVP Plan — paseo-chat (UI-first)

A Rabbit-r1-style launcher on the Bluefox NX1 that orchestrates Paseo coding
agents running on a host machine.

**Approach (revised):** the Paseo daemon and its official app are a known
quantity. The unknown — and the USP — is the **r1-style card UI on a 4" device**.
So we build and user-test the **launcher UI first, driven by stub data**, and
integrate the real daemon only after the card model is validated.

This document breaks the MVP into increments **L0–L4 (UI)** then **I0–I2
(integration)**. Each increment is independently demoable, ends with a real user
test (see [`user-testing-protocol.md`](./user-testing-protocol.md)), and is the
only thing in progress at one time. The card UX spec lives in
[`card-model.md`](./card-model.md) — read that first.

---

## 1. Product vision (one sentence)

> Pick up the NX1, press the button, speak a task, and watch a coding agent do it
> on your machine — and do that for several agents at once.

## 2. Persona

**Sam** — a developer who runs Paseo on their workstation with one or more agent
CLIs configured (Claude Code / Codex / OpenCode / Copilot / Pi). They want to
kick off, monitor, and steer coding work from a pocket device without sitting at
the computer. Sam tolerates setup friction (they own the daemon) but is
intolerant of slowness and dead-ends on the 4" screen.

## 3. North star & scope

**North star:** rabbitOS (Rabbit r1). We replicate its *interaction shell* — Home
launcher, push-to-talk, scroll/swipe card navigation, card-based results, gesture
nav. We do **not** replicate its cloud "LAM" backend; Paseo provides action.

| In scope (MVP) | Out of scope (post-MVP) |
|---|---|
| r1-style launcher = default Home (no root) | Tier B kiosk/theming (root-gated) |
| Card primitive + states (idle…error) | Custom type scale, animations (polish) |
| Card stack: scroll/swipe nav, quick-switch | Boot animation, SystemUI theming |
| Push-to-talk affordance + "listening" card | Offline ASR (Whisper/Vosk); wake-word |
| Streaming card (live agent output) | Diff/file viewer; skills UI; teach mode |
| Connect to one daemon (LAN/Tailscale) | Paseo relay pairing; multi-host |
| Run/switch ≥2 agents in parallel | Camera vision hand-off |

## 4. Key assumptions

- UI is built and tested **on an Android 15 emulator sized to the NX1 canvas
  (270 × 584 dp, xhdpi)** until the device arrives. See `card-model.md` §1.
- All UI increments use **stub data**; the real daemon only enters at I0.
- Daemon stays on the dev box; NX1 is a client. MVP connectivity = direct
  `host:port` + `PASEO_PASSWORD` over LAN/Tailscale.
- Compose + Kotlin; one APK; Tier B features runtime-gated behind root detection.
- NX1 programmable-key mapping and root specifics are **blocked on hardware**.

---

## 5. Increments — UI (L0–L4)

Each increment: **Goal · Build tasks · User stories · Acceptance criteria · User
test · Exit criteria.** IDs: `US-L{n}-{seq}`, `AC-L{n}-{seq}`.

### L0 — Foundations & launcher shell
**Goal:** a buildable Compose app that registers as an Android Home launcher and
renders an r1-inspired theme at the NX1 canvas size. The skeleton every later
increment hangs on.
**Build tasks**
- [x] `flake.nix` devshell (jdk17, gradle, android-sdk, adb, scrcpy).
- [x] Compose project (`apps/nx1-launcher`), `compileSdk`/`targetSdk` 35, `minSdk` 26.
- [x] `CATEGORY_HOME` launcher manifest; r1 color tokens; `@Preview` at 270×584.
- [x] Verify build: `nix develop -c gradle :app:assembleDebug` (green, `f67ff29`).
- [ ] Install on an emulator (540×1168 / xhdpi) and confirm it appears as a Home option.
      **DEFERRED to hardware** — emulator skipped per decision (2026-08-09); AC-L0-2 verified on NX1 when it arrives.
**Stories** — `US-L0-1` As the developer, I want a building launcher shell so all
UI work has a home. `US-L0-2` As Sam, I want the app installable as my Home, so
the device becomes the assistant.
**AC** — `AC-L0-1` `:app:assembleDebug` succeeds. `AC-L0-2` The app is offered as
a Home launcher on the emulator. `AC-L0-3` Theme renders the r1 orange + dark/light.
**User test** — *dev*. Build, install, press Home, pick Paseo, see themed screen.
Capture `docs/user-tests/L0-shell.md`. **Exit** — AC pass; build reproducible via flake.

### L1 — Card primitive
**Goal:** the `AgentCard` composable — anatomy + six states — validated at 270 dp.
Spec: `card-model.md` §2.
**Build tasks**
- [x] `AgentSession` model + `AgentState` enum.
- [x] `AgentCard` (identity row, state chip, body, conditional progress).
- [x] State previews (light + dark) at 270×584.
- [x] Ergonomic pass: tap targets ≥48 dp; body clamp ≤3 lines; contrast AA.
      (card tap target >48dp; body maxLines=3; StateChip now uses Material3
      `*Container`/`on*Container` pairs for AA; Done recoloured success-green
      ~6:1 — see card-model.md §7.)
- [ ] Non-dev walkthrough of the six states (do they read without a legend?).
**Stories** — `US-L1-1` As Sam, I want each card to tell me at a glance *which*
agent, *what* it's doing, and *what state* it's in. `US-L1-2` As Sam, I want the
six states to be visually distinct without reading text.
**AC** — `AC-L1-1` Identity + state + body are legible at 270 dp in <1 s (tester
confirms). `AC-L1-2` All six states distinguishable in a side-by-side without a
legend (≥5/6 correct from a non-dev). `AC-L1-3` Running cards show progress;
non-running do not.
**User test** — *dev, then one non-dev*. Show the states preview; ask "which one
is running / waiting / failed / done?" Capture `docs/user-tests/L1-card-primitive.md`.
**Exit** — states read to a non-dev; any redesigns folded into `card-model.md`.

### L2 — Card stack & navigation
**Goal:** the vertical stack (the r1 scroll model) + swipe/quick-switch + empty state.
**Build tasks**
- [ ] `LazyColumn` stack, newest-on-top, stable keys, swipe-to-dismiss (with undo).
- [ ] Back gesture collapses detail; tap expands (detail stub for now).
- [ ] Empty state: centered "Hold the button to start."
- [ ] Thumb-reach audit (primary affordance in bottom 60%).
**Stories** — `US-L2-1` As Sam, I want to scroll through my agents like the r1
wheel, so navigation is muscle-memory. `US-L2-2` As Sam, I want to dismiss a
finished card with a swipe, so the stack stays relevant. `US-L2-3` As Sam, I want
an obvious empty state, so a bare screen isn't confusing.
**AC** — `AC-L2-1` A 10-card stack scrolls smoothly at 60 Hz on the emulator.
`AC-L2-2` Swipe dismisses with undo within 5 s. `AC-L2-3` Empty state renders and
is self-explanatory.
**User test** — *dev*. Load 10 stub cards; navigate; dismiss; reach empty state.
Capture `docs/user-tests/L2-stack.md`.

### L3 — Input card + push-to-talk affordance
**Goal:** the r1 defining interaction — hold-to-talk raises an input card with a
pulsing "listening" state and transcript review. First **non-dev**-judged increment.
**Build tasks**
- [ ] Hold-to-talk button (emulator surface; HW key is blocked). Haptic + tone.
- [ ] Input card overlay: pulsing listening state, live partial transcript, cancel.
- [ ] Low-confidence transcript → Send/Edit confirmation before commit.
- [ ] Text-entry card as secondary input.
- [ ] On commit, append a new `Queued` card to the stack (stub; no daemon yet).
**Stories** — `US-L3-1` As Sam, I want to press and speak to start a task, hands-free.
`US-L3-2` As Sam, I want to see my words captured before committing. `US-L3-3` As
Sam, I want to cancel a misfire.
**AC** — `AC-L3-1` Hold→speak→release produces a transcript and a new card.
`AC-L3-2` Low-confidence prompts Send/Edit, not auto-launch. `AC-L3-3` Cancel
discards and starts nothing.
**User test** — **non-dev.** "Press and hold, say 'list the files in the repo',
release." Then cancel. Then an ambiguous one. This answers "does it feel like an
r1?" Capture `docs/user-tests/L3-push-to-talk.md`.

### L4 — Streaming/live card
**Goal:** a card whose body streams live output (stubbed source), with scroll/pause.
The visually hardest card — tests streaming legibility on 4".
**Build tasks**
- [ ] Expand-to-detail: full streaming body per card.
- [ ] Stub stream source (e.g. emits a line every ~400 ms from a fixture).
- [ ] Autoscroll + pause-on-scroll-up + "jump to latest".
- [ ] Windowed buffer (cap memory; drop oldest) to avoid jank.
**Stories** — `US-L4-1` As Sam, I want to watch an agent work live. `US-L4-2` As
Sam, I want scrolling up to pause autoscroll so I can read.
**AC** — `AC-L4-1` Streaming text appears without visible jank at 60 Hz for ≥2 min.
`AC-L4-2` Scroll-up pauses; a jump-to-latest affordance resumes.
**User test** — *dev, then non-dev*. Watch a stub stream for ~1 min; scroll up;
jump back. Note legibility/nausea. Capture `docs/user-tests/L4-streaming.md`.

---

## 6. Increments — Integration (I0–I2)

Swap stub data for the real Paseo daemon behind the **same** L0–L4 UI.

### I0 — Daemon client
- [ ] **Protocol audit:** read `getpaseo/paseo` `packages/cli` + `packages/server`
      → `docs/daemon-protocol.md` (handshake, auth, list/run/attach/send/status).
- [ ] `packages/paseo-client` Kotlin module: connect, auth, `listAgents()`.
- [ ] Reconnect-with-backoff; surface disconnected state in the stack header.
**AC** — auths to a live daemon; agent list matches `paseo ls`; wrong-creds fails fast.

### I1 — Run + stream (real)
- [ ] Wire text/voice commit → daemon `run`; map returned agent → `Queued→Running` card.
- [ ] Wire `attach` → the L4 streaming card with real output.
- [ ] Stop/follow-up where the daemon supports it.
**AC** — a task started on the NX1 appears in `paseo ls`; its output streams on-device ≤1 s lag.

### I2 — Multi-agent (real)
- [ ] Run ≥2 agents concurrently; switch streams (L2 stack + L4 detail).
- [ ] Surface per-agent completion/failure.
**AC** — two providers run in parallel, independent streams, ≤1-tap switch.

**MVP release** = L0–L4 + I0–I2 single-agent loop, shippable on stock Android.

---

## 7. Backlog (post-MVP)
- **Tier B (root):** system-app, kiosk/lock-task, LSPosed SystemUI theming, global
  programmable-key capture — runtime-gated behind root detection.
- **Connectivity:** Paseo relay pairing; multi-host; NX1 hardware-key mapping.
- **Input:** offline ASR; wake-word.
- **Vision:** rear-camera → agent.
- **Agent UX:** skills UI; teach mode; diff/file viewer; worktree picker.
- **Hardening:** Keystore creds; offline queue; Magisk OTA-survival; battery/thermal.
- **Polish:** custom type scale; r1 motion; dark-mode tuning.

## 8. Open questions
1. Exact daemon WebSocket contract (resolved by the I0 protocol audit).
2. NX1 programmable-key keycode/launch intent (blocked on hardware).
3. NX1 bootloader-unlock vs Magisk-only root (affects Tier B scope).
4. License: AGPL-3.0 to match Paseo, or other?
