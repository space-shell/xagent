# MVP Plan — xagent (UI-first)

A Rabbit-r1-inspired launcher on a physically small Android device that orchestrates
Paseo coding agents running on a host machine.

> **Target form factor.** This project is aimed at physically small Android
> devices (~4", one-handed). All composables are previewed at `270 × 584 dp`,
> which represents a small device at xhdpi. The app runs on any `minSdk = 26`
> device; the geometry and reach assumptions are tuned for the small form
> factor.

**Approach:** the Paseo daemon and its official app are a known quantity. The
unknown — and the USP — is the **r1-inspired card UI on a small screen**. So we
build and user-test the **launcher UI first, driven by stub data**, and
integrate the real daemon only after the card model is validated.

This document breaks the MVP into increments **L0–L4 (UI)** then **I0–I2
(integration)**. Each increment is independently demoable, ends with a real
user test (see [`user-testing-protocol.md`](./user-testing-protocol.md)), and is
the only thing in progress at one time. The card UX spec lives in
[`card-model.md`](./card-model.md) — read that first.

---

## 1. Product vision (one sentence)

> Pick up the device, press the button, speak a task, and watch a coding agent
> do it on your machine — and do that for several agents at once.

## 2. Persona

**Sam** — a developer who runs Paseo on their workstation with one or more agent
CLIs configured (Claude Code / Codex / OpenCode / Copilot / Pi). They want to
kick off, monitor, and steer coding work from a pocket device without sitting at
the computer. Sam tolerates setup friction (they own the daemon) but is
intolerant of slowness and dead-ends on the small screen.

## 3. North star & scope

**North star:** rabbitOS (Rabbit r1). We replicate its *interaction shell* —
Home launcher, push-to-talk, scroll/swipe card navigation, card-based results,
gesture nav. We do **not** replicate its cloud "LAM" backend; Paseo provides
action.

| In scope (MVP) | Out of scope (post-MVP) |
|---|---|
| r1-inspired launcher = default Home (no root) | Tier B kiosk/theming (root-gated) |
| Card primitive + states (idle…error) | Custom type scale, animations (polish) |
| Card stack: scroll nav, status dot rail | Boot animation, SystemUI theming |
| Push-to-talk affordance + "listening" card | Offline ASR (Whisper/Vosk); wake-word |
| Remote controller: approve/deny, Plan/Build | Streaming card (L4 deferred) |
| Connect to one daemon (LAN/Tailscale) ✅ | ~~Paseo relay pairing; multi-host~~ **shipped** |
| Run/switch ≥2 agents in parallel | Camera vision hand-off |

> **Scope changes since plan revision (2026-08-10):**
> - **Relay pairing shipped** — DIRECT (host:port + password) **and** RELAY
>   (E2EE via QR-scanned `#offer=…` URL) both work, and multiple connections
>   merge their agent decks. See §6b.

## 4. Key assumptions

- UI is built and tested at the **270 × 584 dp reference canvas** for the small
  form factor; development is done on any Android device meeting `minSdk = 26`.
  See `card-model.md` §1.
- All UI increments used **stub data**; the real daemon entered at I0.
- Daemon stays on the dev box; the device is a client. Connectivity now supports
  both DIRECT (`host:port` + `PASEO_PASSWORD` over LAN/Tailscale) and RELAY
  (E2EE via Paseo relay + QR pairing).
- Compose + Kotlin; one APK; Tier B features runtime-gated behind root detection.

---

## 5. Increments — UI (L0–L4)

Each increment: **Goal · Build tasks · User stories · Acceptance criteria · User
test · Exit criteria.** IDs: `US-L{n}-{seq}`, `AC-L{n}-{seq}`.

### L0 — Foundations & launcher shell  ✅
**Goal:** a buildable Compose app that registers as an Android Home launcher and
renders an r1-inspired theme at the 270 × 584 dp reference canvas. The skeleton
every later increment hangs on.
**Build tasks**
- [x] `flake.nix` devshell (jdk17, gradle, android-sdk, adb).
- [x] Compose project (`apps/nx1-launcher`), `compileSdk`/`targetSdk` 35, `minSdk` 26.
- [x] `CATEGORY_HOME` launcher manifest; r1 color tokens; `@Preview` at 270×584.
- [x] Verify build: `nix develop -c gradle :app:assembleDebug` (green, `f67ff29`).
- [x] Install on device and confirm it appears as a Home option.
**Stories** — `US-L0-1` As the developer, I want a building launcher shell so all
UI work has a home. `US-L0-2` As Sam, I want the app installable as my Home, so
the device becomes the assistant.
**AC** — `AC-L0-1` `:app:assembleDebug` succeeds. `AC-L0-2` The app is offered as
a Home launcher. `AC-L0-3` Theme renders the r1 orange + dark/light.
**User test** — *dev*. Build, install, press Home, pick Paseo, see themed screen.
Capture `docs/user-tests/L0-shell.md`. **Exit** — AC pass; build reproducible via flake.

### L1 — Card primitive  ✅
**Goal:** the `AgentCard` composable — anatomy + six states — validated at 270 dp.
Spec: `card-model.md` §2.
**Build tasks**
- [x] `AgentSession` model + `AgentState` enum.
- [x] `AgentCard` (identity row, state chip, body, conditional progress).
- [x] State previews (light + dark) at 270×584.
- [x] Ergonomic pass: tap targets ≥48 dp; body clamp ≤3 lines; contrast AA.
- [x] Non-dev walkthrough of the six states.
      (2026-08-09: passed — six states distinguishable, no confusion reported.
      See `docs/user-tests/L1-card-primitive.md`.)
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

> **Post-L1 redesign** (2026-08-09, `4990e08`): the card anatomy changed
> significantly after L1 passed. Avatar circle, state chip, and progress bar were
> removed; state is now encoded by card background colour + full-card watermark
> icon. The L1 validation (states distinguishable without a legend) still holds —
> arguably stronger, since the full card surface now carries state. See
> `card-model.md` §2 "Design pivot: remote controller."

### L2 — Card stack & navigation  ✅
**Goal:** the roller deck (r1 scroll model) + status dot rail + empty state.
**Build tasks**
- [x] Roller deck (`VerticalPager`, `pageSize=Fill`, per-page transform) — focused
      card centred; up to three previous cards fan out above; next slides up on
      swipe. `getOffsetDistanceInPages` drives all transforms continuously.
- [x] ~~Swipe-to-dismiss with undo~~ **removed** (`9d5bd82`) — cards persist; the
      device is a remote controller, not a content browser.
- [x] ~~Tap-to-detail~~ **removed** (`4990e08`) — card is the full interface;
      `AgentDetail` composable deleted.
- [x] Status dot rail (left gutter): colour per state, focused = pill, tap-to-jump.
      Dots bunched centre (`spacedBy(8dp, CenterVertically)`).
- [x] Solid cards (no translucency); same-size peeks (no scale fan).
- [x] Empty state: centred "Hold the button to start."
- [x] Thumb-reach audit (primary affordance in bottom 60%).
- [x] **Custom `DeckScroller`** (`LauncherScreen.kt`, post-I0) — replaced
      `VerticalPager` with a hand-rolled scroller using `Animatable<Float>` offset,
      `detectVerticalDragGestures`, `VelocityTracker`,
      `NestedScrollConnection`, and `derivedStateOf` for `currentPage` / zIndex.
      Motivation: tighter control over z-index ordering, bounce tuning, and
      symmetric transitions between cards. Verified on-device across builds
      `7d07243` → `2b4af3d`.
**Stories** — `US-L2-1` As Sam, I want to scroll through my agents like the r1
wheel, so navigation is muscle-memory. `US-L2-2` ~~dismiss by swipe~~ **removed**
— cards persist. `US-L2-3` As Sam, I want an obvious empty state.
`US-L2-4` As Sam, I want z-index transitions between cards to feel symmetric so
the focused card never visually clips behind its neighbours mid-scroll.
**AC** — `AC-L2-1` A 10-card stack scrolls smoothly at 60 Hz. **PASS.**
`AC-L2-2` ~~swipe dismisses with undo~~ **obsolete** (swipe removed).
`AC-L2-3` Empty state renders and is self-explanatory. **PASS.**
`AC-L2-4` Z-index bias (`+0.1f` for the outgoing card when `offsetFromCurrent > 0`)
pushes the crossover α from 0.5 to 0.75, eliminating mid-scroll clipping. Tuned
across `7ce1139` and `adb7e2d`. **PASS.**
**User test** — *dev*. Load 10 stub cards; navigate; reach empty state.
Capture `docs/user-tests/L2-stack.md`.

### L3 — Input card + push-to-talk affordance  ✅
**Goal:** the r1 defining interaction — hold-to-talk captures speech and attaches
the transcript to the addressed card. First **non-dev**-judged increment.
**Build tasks**
- [x] Per-card mic button (small circle, bottom-right of each card); hold via
      `detectTapGestures.onPress` with try/finally to guarantee stop on cancel.
- [x] Real Android `SpeechRecognizer` via `VoiceController` (Compose-observable
      state: `isListening`, `partialText`, `error`; handles availability + all
      error codes). `be7f9e2`.
- [x] Live partial transcript streams onto the card ("Listening…" line); on
      release the final transcript commits to `session.userInput` and renders
      as a `primaryContainer` bubble.
- [x] `RECORD_AUDIO` runtime permission (first-press prompt; resumes listening
      on grant).
- [ ] Text-entry card as secondary input (deferred to post-L3).
- [ ] Low-confidence Send/Edit review (deferred — needs recognizer confidence).
- [ ] On commit, create a new `Queued` card (deferred — currently attaches to
      the existing stub card; new-card creation waits on I1).
**Stories** — `US-L3-1` As Sam, I want to press and speak to steer an agent,
hands-free. `US-L3-2` As Sam, I want to see my words captured on the card before
it acts. `US-L3-3` As Sam, I want a misfire to fail gracefully, not silently.
**AC** — `AC-L3-1` Hold→speak→release produces a transcript on the addressed
card. `AC-L3-2` Permission denial / unavailable recognizer surfaces a message,
not a silent dead-end. `AC-L3-3` (deferred) Low-confidence prompts Send/Edit.
**User test** — **non-dev.** "Press and hold the mic on a card, say 'list the
files in the repo', release." Then try with permission denied / no recognizer.
This answers "does it feel like an r1?" Capture `docs/user-tests/L3-push-to-talk.md`.
**Status** — **confirmed acceptable** (2026-08-09). User: "Push to talk is
acceptable for now." Formal AC checklist deferred to next on-device session.

### L4 — Streaming/live card
**Status: DEFERRED** (2026-08-09) — user decision. The remote-controller pivot
reduced the priority of detailed streaming output; the card shows a summary, not
a live stream. Streaming will return when integration (I1) requires it.
**Goal:** a card whose body streams live output (stubbed source), with scroll/pause.
The visually hardest card — tests streaming legibility on the small screen.
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

### I0 — Daemon client  ✅

I0 was decomposed into sub-increments `I0a`–`I0e`, all shipped between
`94ea50c` and `1fa4942`.

#### I0a — WebSocket transport  ✅
**Goal:** speak the daemon's wire protocol at the socket layer.
- [x] `PaseoDaemonClient` (`daemon/`) — WebSocket connect, `hello` /
      `auth_request` / `auth_response` handshake, ping/pong keepalive, exponential
      backoff reconnect. `94ea50c`.
**Stories** — `US-I0a-1` As Sam, I want the device to connect to my daemon over
LAN/Tailscale and stay connected across transient drops.
**AC** — `AC-I0a-1` Wrong credentials fail fast (≤3 s); correct credentials
maintain a stable session across transient network drops with exponential
backoff reconnect. **PASS.**

#### I0b — Agent list subscription  ✅
**Goal:** mirror the daemon's view of running agents into the deck.
- [x] Subscribe to `fetch_agents` / `agent_update`; mirror the daemon's agents
      into `AgentSession` models that populate the deck. `11712d0`.
**Stories** — `US-I0b-1` As Sam, I want my deck to reflect exactly what `paseo ls`
shows on the daemon, updating live as agents start / finish.
**AC** — `AC-I0b-1` A new agent started via `paseo run` on the host appears on
the device within ≤2 s. **PASS.**

#### I0c — Permission flow (approve / deny)  ✅
**Goal:** wire the card's `[Allow] [Deny]` gate to the daemon.
- [x] Wire `paseo_respond_to_permission` to the `ApprovalBar` on
      `AwaitingInput` cards. Permission requests surface as state changes;
      `Allow` / `Deny` send the matching response payload. `37a63e7`.
**Stories** — `US-I0c-1` As Sam, I want to approve or deny a tool execution from
my pocket without touching the keyboard.
**AC** — `AC-I0c-1` Tapping `[Allow]` resumes the agent within ≤1 s; `[Deny]`
surfaces the rejection cleanly without leaving the card in a stuck state. **PASS.**

#### I0d — Session mode toggle (Plan / Build)  ✅
**Goal:** wire long-press card cycle to daemon mode change.
- [x] Wire `paseo_set_agent_mode` to the long-press Plan↔Build toggle.
      `plan` ↔ `auto` mapping; UI snackbar confirms. `865961d`.
**Stories** — `US-I0d-1` As Sam, I want to flip an agent between propose-only and
execute modes from the device, so I can review plans before committing to
side-effects.
**AC** — `AC-I0d-1` Toggling mode on-device is reflected in `paseo ls --json`
within ≤1 s and the card subtitle updates to match. **PASS.**

#### I0e — Push-to-talk → send task  ✅
**Goal:** wire voice transcript to the daemon as a task.
- [x] Wire `paseo_send_agent_message` to the L3 voice commit. Transcript on
      release becomes a `send_agent_message_request` to the addressed agent.
      `1fa4942`.
**Stories** — `US-I0e-1` As Sam, I want to speak a follow-up instruction and have
the agent receive it as if I'd typed it at the keyboard.
**AC** — `AC-I0e-1` A spoken instruction appears in the agent's transcript on the
host within ≤2 s. **PASS.**

### I1 — Run + stream (real)
- [ ] Wire text/voice commit → daemon `run`; map returned agent → `Queued→Running` card.
- [ ] Wire `attach` → the L4 streaming card with real output.
- [ ] Stop/follow-up where the daemon supports it.
**AC** — a task started on the device appears in `paseo ls`; its output streams
on-device ≤1 s lag.

### I2 — Multi-agent (real)
- [x] Run ≥2 agents concurrently; switch streams (L2 stack + L4 detail).
      **Shipped** as part of multi-connection support (§6b) — multiple connections
      each carrying multiple agents merge into a single deck.
- [x] Surface per-agent completion/failure (via `agent_update` + `Done` / `Error`
      state encoding from I0b).
**AC** — two providers run in parallel, independent streams, ≤1-tap switch.
**Status** — **PARTIAL** — switching works, streaming deferred (L4 still on hold).

**MVP release** = L0–L4 + I0–I2 single-agent loop, shippable on stock Android.

---

## 6b. Shipped post-I0 — multi-connection & E2EE relay

This section captures work that landed between I0 and the I1 wiring, originally
listed as "post-MVP" in the §3 scope table. All items below are **complete and
verified on-device** across commits `7d07243` → `2b4af3d`.

### Multi-connection support  ✅
**Goal:** connect to multiple Paseo daemons simultaneously and merge their
agents into one deck.
- [x] `ConnectionManager` owns N `PaseoDaemonClient` instances keyed by
      `ConnectionProfile`.
- [x] `remerge()` dedupes agents by ID across connections; DIRECT connections
      win over RELAY when the same agent appears on both (`7ce1139`).
- [x] Connection lifecycle: add / remove / state changes propagated to the UI.
**Stories** — `US-MC-1` As Sam, I want to monitor agents on both my laptop and my
home server from the same pocket device without switching apps.
`US-MC-2` As Sam, I want the same agent (visible from two connections) to appear
only once, with the more direct connection winning.

### E2EE relay (QR pairing)  ✅
**Goal:** connect to a remote daemon via the Paseo relay with end-to-end
encryption, paired by scanning a QR code.
- [x] `ConnectionOffer.parseOfferFromUrl()` parses `#offer=BASE64` URLs emitted
      by `paseo relay offer`.
- [x] `E2eeCrypto` (Lazysodium-Android) — X25519 key exchange, sealed-box
      decryption.
- [x] `QrScanner` (CameraX + ZXing) — square aspect-ratio scanner, sized to
      `min(maxWidth, maxHeight)` inside the connection card (`2b4af3d`).
- [x] Relay handshake: `e2ee_hello` → relay `key` event → ciphertext frames.
- [x] Ciphertext frames accepted as **both TEXT (base64) and BINARY** — the
      relay daemon sends base64 TEXT frames regardless of the client's declared
      `binaryCiphertext` preference; client handles both (`7ce1139`).
**Stories** — `US-RL-1` As Sam, I want to pair to my daemon from outside my LAN
by scanning a QR code on the host, with no plaintext crossing the relay.
`US-RL-2` As Sam, I want the pairing flow to be forgiving of either TEXT or
BINARY ciphertext framing on the wire.

### Deep link to Paseo app  ⚠️ partial
**Goal:** tapping "Open in Paseo" on a card launches the Paseo app at that agent
via the `paseo://h/<serverId>/agent/<agentId>` deep link.
- [x] xagent constructs and dispatches the intent; `serverId` is read from the
      daemon's `server_info` message. `7d07243`.
- [x] xagent wraps `startActivity` in `try/catch (Throwable)` so a Paseo-side
      failure surfaces as a snackbar rather than crashing xagent. `adb7e2d`,
      hardened `2b4af3d`.
**Known issue (Paseo-side).** Cold-starting the Paseo app via deep link crashes
Paseo with `"Attempted to navigate before mounting the Root Layout component"` —
an Expo Router / React Navigation race that processes the deep link before the
root layout is mounted. The crash happens **inside Paseo** after xagent's
`startActivity` returns successfully; it cannot be fixed from xagent. xagent's
defensive `Throwable` catch protects itself but does not prevent the Paseo-side
crash. The fix has to land in Paseo: queue the deep link until
`navigation.isReady()` returns true.
**Stories** — `US-DL-1` As Sam, I want to hand off to the full Paseo app for
deep inspection of an agent's work without losing context.
**AC** — `AC-DL-1` Tapping "Open in Paseo" launches the Paseo app focused on the
same agent. **BLOCKED** on the Paseo-side navigation race.

### Timeline summary display  ✅
**Goal:** show what each agent is currently doing in the card body, sourced from
real daemon timeline events.
- [x] `handleAgentStream` maps `assistant_message` events (LLM response text)
      into the card's `timelineSummaries[agentId]`.
- [x] `reasoning` events map to `"Thinking…"` so the card surfaces model
      reasoning as a live state. `2b4af3d`.
- [x] `tool_call` events do not override the summary (the assistant's last
      stated intent is the better one-line description).
- [x] Full message display — removed the 140-char truncation; card now shows the
      complete last assistant message, scrollable via nested scroll. `e86c675`.
**Stories** — `US-TL-1` As Sam, I want to glance at a card and see what the agent
is currently doing, in the agent's own words.
`US-TL-2` As Sam, I want the card to surface "Thinking…" when the agent is
reasoning, so silent contemplation doesn't look like a hang.

### Persistence  ✅
**Goal:** connection profiles survive app restarts.
- [x] `ConnectionProfile` (host, password, type) persisted via
      `SharedPreferences`. `87d19b7`.
**Stories** — `US-PS-1` As Sam, I don't want to re-enter my daemon host and
password every time the device reboots.

---

## 6c. Shipped post-I0 — permissions, scroll & battery (v0.3.2)

This section captures the v0.3.2 work: permission rendering overhaul, nested
scroll coordination, and battery optimizations. All items below are **complete
and verified on-device** across commits `3b5a0fb` → `89b0499`.

### Permission system overhaul  ✅
**Goal:** render and respond to both `kind=tool` and `kind=question` permission
requests with appropriate UX for each.
- [x] `kind=question` permissions render inline: question text + auto-sized option
      buttons (no fixed height), multi-question cycling with arrow nav, and answer
      collection into `{behavior:"allow", updatedInput:{answers:{...}}}`. `3b5a0fb`.
- [x] `kind=tool` permissions use the ApprovalBar (Allow/Deny, hold for allow-always,
      NO mic while a permission is pending). `3b5a0fb`.
- [x] Multi-permission cycling: when an agent has multiple pending permissions, a
      pager with arrows cycles through them. `af9ee56`.
- [x] Model: `QuestionChoice`, `PendingQuestion`, `PendingPermission.questions`.
      Parsing: `parseQuestions()` in `PaseoDaemonClient`. `3b5a0fb`.
- [x] Auto-sized option buttons + scrollable card content area. `b1aee65`.
**Stories** — `US-PR-1` As Sam, I want to answer a question from the agent
(multiple choice) from my pocket without touching a keyboard.
`US-PR-2` As Sam, I want to approve/deny tool execution with a single tap.
`US-PR-3` As Sam, I want to cycle through multiple pending permissions on the same
agent without losing context.

### Nested scroll coordination  ✅
**Goal:** inner scroll areas (card content, settings) coordinate with deck paging
so that scrolling within a card works naturally, and continuing to scroll past the
boundary pages the deck.
- [x] `NestedScrollConnection` on the deck Box: `onPostScroll` processes only
      `UserInput` source, moving the deck when the child can't consume the delta.
      `1ba1692`.
- [x] Sign convention: `-available.y` to match drag gesture convention (screen
      coordinates, positive = down). `bc610f6`.
- [x] `onPreFling` intercepts velocity before the child's fling animation; tracks
      `childOverflowed` flag from `onPostScroll` to decide whether to consume.
      Velocity prediction: `predicted = current - (v / pageHeightPx) * 0.15f *
      SWIPE_SENSITIVITY`. `c86eb0b`.
- [x] `onPostFling` remains as a snap-to-nearest fallback. `c86eb0b`.
**Stories** — `US-NS-1` As Sam, I want to scroll through a long agent message
within a card, and when I reach the end, continue scrolling to page to the next
card — just like a native scroll container.
`US-NS-2` As Sam, I want fling gestures to carry through the boundary — flinging
up at the top of a card's content should page the deck with momentum.

### Battery optimizations  ✅
**Goal:** reduce battery consumption without degrading real-time monitoring.
- [x] **Smart wake lock** — `PARTIAL_WAKE_LOCK` acquired only when any agent is
      Running/AwaitingInput/Queued; released when all agents are Idle/Done/Error.
      The device deep-sleeps between agent runs; kernel network-triggered CPU wake
      delivers events within milliseconds. `7a9b6f0`.
- [x] **Ping interval 30s → 60s** — 50% fewer CPU wakeups per connection. `89b0499`.
- [x] **Reconnect cap at 10** — exponential backoff (1s→30s) for 10 attempts (~3 min
      total), then Error state. Stops perpetual radio wakeups for unreachable daemons.
      `89b0499`.
- [x] **E2EE handshake cap at 20** — 1s retry loop limited to 20 attempts, then
      fails the connection. `89b0499`.
- [x] **Debounced remerge** — 50ms debounce collapses 3 rapid `remerge()` calls
      (agents + serverName + serverId) into 1 on fresh connections. `89b0499`.
- [x] **Off-screen card composition skip** — deck cards >3 pages from `currentPage`
      skip their `when` block entirely. Uses `derivedStateOf` (integer page changes
      only), no per-frame recomposition during animation. `89b0499`.
**Stories** — `US-BA-1` As Sam, I want the device battery to last through a full
day of monitoring agents, not just while agents are actively running.
`US-BA-2` As Sam, I want permission requests and state changes to arrive in near
real-time even when I haven't touched the device in minutes.

---

## 7. Backlog (post-MVP)
- **Tier B (root):** system-app, kiosk/lock-task, LSPosed SystemUI theming, global
  programmable-key capture — runtime-gated behind root detection.
- **Connectivity:** hardware-key mapping for push-to-talk; wake-word.
- **Input:** offline ASR; wake-word; per-card text-entry secondary affordance.
- **Vision:** rear-camera → agent.
- **Agent UX:** skills UI; teach mode; diff/file viewer; worktree picker;
  new-card creation on voice commit (currently attaches to existing card).
- **Deep link:** xagent-side mitigation (warm Paseo via `ACTION_MAIN`, then send
  the deep link as a second intent after ~500 ms) — only worth adding if the
  Paseo-side race isn't fixed first.
- **Hardening:** Keystore creds (currently plaintext in SharedPreferences);
  offline queue; Magisk OTA-survival; thermal monitoring. Battery is addressed
  via smart wake lock (§6c) but further work possible (FCM push instead of
  persistent socket for background delivery).
- **Polish:** custom type scale; r1 motion; dark-mode tuning.
- **Packaging:** `useLegacyPackaging = true` is set for 16 KB-page-size
  compatibility; revisit when the upstream Lazysodium / JNA story improves.

## 8. Open questions
1. ~~Exact daemon WebSocket contract~~ — resolved by the I0 implementation.
2. License: **AGPL-3.0-only** — matches Paseo (`getpaseo/paseo`). ✅
3. New-card creation on voice commit (currently attaches to the addressed stub
   card — proper `run` wiring lands in I1).
4. Paseo-side deep-link race — tracked as a Paseo bug; xagent mitigates only.
