# Card Model & Launcher Design

**North star:** rabbitOS (Rabbit r1). **Scope:** we replicate rabbitOS's
*interaction shell* on a general Android device — the Home launcher, push-to-talk,
scroll card navigation, card-based results. We do **not** replicate its cloud
"LAM" web-agent backend; Paseo provides action.

This document is the design spec for the launcher UI.

---

## 1. The canvas — small Android device

xagent is aimed at **physically small Android devices** (~4", one-handed). The
reference canvas is representative of that form factor:

| | |
|---|---|
| Reference size | ~4" at xhdpi (~321 ppi) → design at **270 × 584 dp** |
| Reach | one-handed; thumb arc covers bottom ~60% |
| Input | touch + (device-dependent) side **programmable key** as r1's push-to-talk analogue |

**Rule:** every composable is previewed at `widthDp = 270, heightDp = 584`. The
app runs on any device with `minSdk = 26`; the geometry and reach assumptions
are tuned for the small form factor.

---

## 2. What is a card? (the content model)

**rabbitOS card** = an *ephemeral result* (a transit time, a song, a one-shot
answer). Produced, consumed, scrolled away.

**xagent card** = an **agent session** — long-lived, stateful, streaming.
Created when a task starts, mutated as it runs, persistent in the stack after it
finishes.

### Design pivot: remote controller (2026-08-09, `4990e08`)

The small device is **not a reading device** — it's a **remote controller**. You
don't drill into a card to read detailed output; you glance at state,
approve/deny actions, switch modes, and talk to steer. This pivot drove:

- **No detail/tap-through.** The card is the full interface. `AgentDetail`
  composable deleted.
- **No swipe-to-dismiss.** Cards persist in the stack (`9d5bd82`).
- **Card background colour = state.** The entire card surface communicates state
  at a glance — no icon, chip, or progress bar needed.
- **State icon as full-card watermark.** The icon fills the card behind content
  at alpha 0.07, reinforcing state recognition subconsciously.
- **Approval gates on-device.** When an agent needs permission, the card shows
  `[Allow] [Deny]` buttons.
- **Plan/Build mode toggle.** Long-press cycles Plan (read-only) ↔ Build
  (execute). Maps to Paseo's `plan` / `auto` session modes.

### Card anatomy
```
┌───────────────────────────────────────┐  ← bg: state container colour
│                                ⚡(faded)│  ← watermark icon fills card
│ Title                                  │     (alpha 0.07, ContentScale.Crop)
│ provider/model · build                 │  ← subtitle: mode always visible
│                                        │
│ Summary / latest action (≤4 lines)     │  ← body
│ …                                      │
│                                        │
│              [🎤]   or   [Allow] [Deny]│  ← contextual bottom row
└───────────────────────────────────────┘
```

- **Title** — full-width, `titleMedium`, Bold, `maxLines = 2`. No avatar circle.
- **Subtitle** — `"provider/model · plan"` or `"provider/model · build"`.
  Mode is always visible because the toggle is gesture-only (long-press).
- **Body** — summary or latest action; clamped to 4 lines.
- **Watermark** — `Image(imageVector, ContentScale.Crop, alpha=0.07)` filling
  the card. `contentDescription = null`. Tinted with `onContainerColor`.
- **Bottom row** — contextual:
  - `AwaitingInput` → `ApprovalBar`: `[Allow]` (primary) + `[Deny]` (error),
    each `weight(1f)`. Mic hidden — the gate IS the primary action.
  - All other states → `MicButton` right-aligned.

### State encoding (three redundant channels)
1. **Card background colour** (primary signal — visible at a glance)
2. **Watermark icon** (reinforces recognition)
3. **Status dot rail** (left gutter — see §3)

| State | Container | Icon | Meaning |
|---|---|---|---|
| `Idle` | `surfaceVariant` | `HourglassEmpty` | available, nothing running |
| `Queued` | `tertiaryContainer` | `HourglassEmpty` | accepted, not started |
| `Running` | `primaryContainer` | `Bolt` | working now |
| `AwaitingInput` | `secondaryContainer` | `PauseCircleOutline` | permission gate |
| `Done` | `DoneContainer*` (success-green) | `CheckCircle` | finished OK |
| `Error` | `errorContainer` | `ErrorOutline` | failed / cancelled |

Text uses matching `onContainerColor` for AA contrast. `Done` uses dedicated
success-green tokens (not primary-tinted) so Done ≠ Running.

### Card lifecycle
`created (Queued→Running) → (AwaitingInput↔Running) → Done | Error → persists`
Cards remain in the stack after completion. No dismiss/delete.

### Mode toggle (Plan / Build)
- **Long-press** card surface (outside mic/buttons) → haptic `LONG_PRESS` →
  cycles Plan ↔ Build → snackbar confirms (`"plan mode"` / `"build mode"`).
- **Plan** — agent proposes but does not execute (read-only). Maps to Paseo `plan`.
- **Build** — agent executes, surfacing permission gates. Maps to Paseo `auto`.

### Approval gates
When `state == AwaitingInput`:
- Body describes the requested action (e.g. "Wants to run `npm install`").
- `[Allow]` → haptic `CONFIRM` → `state = Running`.
- `[Deny]` → haptic `REJECT` → `state = Idle`.
- In production (I0–I2), wires to `paseo_respond_to_permission` (allow/deny).

---

## 3. The stack — navigation model

- A **roller deck** (custom `DeckScroller` in `LauncherScreen.kt`, replaced the
  original `VerticalPager`): the **focused** card sits centred (~360 dp); up to
  **three previous cards fan out above**, each recessed (alpha ramp, lower
  z-index). Next card slides up from below on swipe.
- Cards are **solid** (alpha = 1 inside fan) and **same size** (no scale fan).
  Peeks differ only in alpha and z-index.
- **Status dot rail** — left gutter (start 6dp). Each dot's colour matches its
  card's container colour. Focused = elongated pill (8×24dp); others = circles
  (8×8dp). Dots bunched centre (`spacedBy(8dp, CenterVertical)`). Tap to jump.
- Card content inset `start=40dp` to clear the rail.
- **No tap-through** to detail. **No swipe-to-dismiss.** Scroll is the only
  gesture on the deck. Z-index uses an asymmetric `+0.1f` bias for the outgoing
  card so the focused card never clips behind its neighbour mid-scroll.
- **Empty state** — centred: "Hold the button to start."

The stack *is* the multi-agent view — no separate "agents" tab.

---

## 4. Input model

### Push-to-talk (per-card)
Mic button bottom-right (~44dp, r1-orange when active). Hold to talk to *that*
agent; transcript attaches to the associated session.
- While held: pulsing ring + filled mic; "Listening…" line with live partial.
- On release: final transcript commits to `session.userInput`, renders as tinted
  bubble (`onContainerColor` at alpha 0.12).
- Mic press triggers `CLOCK_TICK` haptic.
- Speech source: Android `SpeechRecognizer`. All error codes handled.

### Approval (per-card)
`AwaitingInput` → `[Allow] [Deny]` replaces mic (see §2).

### Mode toggle (per-card)
Long-press cycles Plan ↔ Build (see §2).

### Deferred
- Text entry (per-card text affordance).
- Transcript review (Send/Edit on low confidence).
- New-card creation on voice commit (attaches to existing stub for now).

---

## 5. r1 borrowings vs divergences

| Borrow from rabbitOS | Diverge (because agents) |
|---|---|
| Home = full-screen launcher | Cards are stateful, not ephemeral |
| Push-to-talk as primary input | Bodies stream and grow over time |
| Scroll card navigation | Cards persist after completion |
| Bold typography, single focus | Multiple cards run concurrently |
| Card-based results | Card = session, not result |
| | **Remote controller:** approve/deny from device |
| | **Mode toggle:** Plan vs Build per card |
| | **No detail drill-in:** card IS the interface |

---

## 6. What we test (the unknowns)

1. **Legibility at 270 dp** — **L1: PASS** (non-dev, all 6 states identified).
2. **State comprehension** — **L1: PASS** (distinguishable without legend).
3. **One-handed reach** — **L2: PASS** (centred card, mic bottom-right).
4. **"Card = session"** — validated informally; formal test deferred.
5. **Streaming legibility** — **deferred** (L4 on hold).
6. **PTT feel** — **L3: "acceptable for now"** (user confirmed 2026-08-09).

---

## 7. Tokens

Implemented in `apps/nx1-launcher/.../ui/theme/`:

- **Primary:** `R1Orange #FF5A1F`, `onPrimary` white.
- **Surfaces:** `Paper #F7F5F2` (light) / `Ink #111014` (dark).
- **Shape:** cards `RoundedCornerShape(28.dp)`.
- **Card backgrounds:** Material3 `*Container` colours per state (see §2 table).
  Text uses matching `on*Container` for AA contrast.
- **Done:** dedicated success-green tokens ≈ 6:1.
- **Watermark:** state icon at `alpha = 0.07`, `ContentScale.Crop`,
  tinted with `onContainerColor`.
- **Status dot rail:** dots use `containerColor` per state. Focused = pill
  (`RoundedCornerShape(50)`); unfocused = `CircleShape`.

### Haptics (`ui/Haptics.kt`)
| Action | Constant | API |
|---|---|---|
| Long-press (mode cycle) | `LONG_PRESS` | 3+ |
| Allow | `CONFIRM` | 30+ (fallback `LONG_PRESS`) |
| Deny | `REJECT` | 30+ (fallback `LONG_PRESS`) |
| Mic press | `CLOCK_TICK` | 21+ |
