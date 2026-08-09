# Card Model & Launcher Design

**North star:** rabbitOS (Rabbit r1). **Scope:** we replicate rabbitOS's
*interaction shell* on a general Android device — the Home launcher, push-to-talk,
scroll/swipe card navigation, card-based results, gesture nav. We do **not**
replicate its cloud "LAM" web-agent backend; Paseo provides action.

This document is the design spec for the launcher UI. It exists because the
biggest unknown isn't "can we draw r1-like cards" — it's **does the r1 card
model survive being pointed at long-lived, streaming agent sessions instead of
ephemeral consumer answers.** That has to be designed, then tested.

---

## 1. The canvas — Bluefox NX1

| | |
|---|---|
| Screen | 4.0" LCD, 540 × 1168 px, 60 Hz, 500 nits |
| Density | ~321 ppi → **xhdpi (2.0×)** → design at **270 × 584 dp** |
| Reach | one-handed; thumb arc covers bottom ~60% |
| Input | touch + side **programmable key** (r1's push-to-talk analogue) + IR |

**Rule:** every composable is previewed at `widthDp = 270, heightDp = 584`. If it
doesn't read there, it doesn't ship. Compose `@Preview` is the primary design
surface until the NX1 arrives.

---

## 2. What is a card? (the content model)

**rabbitOS card** = an *ephemeral result* (a transit time, a song, a one-shot
answer). Produced, consumed, scrolled away.

**xagent card** = an **agent session** — long-lived, stateful, streaming.
Created when a task starts, mutated as it runs, persistent in the stack after it
finishes. This is the core divergence and the thing L1 tests.

### Card anatomy
```
┌───────────────────────────────────────┐
│ (○ provider)  Title          [ state ] │  ← identity row
│               provider/model           │
│                                        │
│  Summary / latest message (≤3 lines)   │  ← body
│  …                                     │
│                                        │
│  ▓▓▓▓▓▓▓░░░░░  progress (when running) │  ← progress (conditional)
└───────────────────────────────────────┘
```
- **Identity** — provider avatar (initials in a tinted circle), session title,
  `provider/model` label.
- **State chip** — see states below.
- **Body** — summary or latest turn; clamped to 3 lines in collapsed view.
- **Progress** — linear bar, only while `Running`.
- **Expand** — tap → full streaming view (L4).

### Card states
| State | Meaning | Visual |
|---|---|---|
| `Idle` | agent available, nothing running | neutral chip |
| `Queued` | task accepted, not yet started | tertiary chip |
| `Running` | working now | primary chip + progress bar |
| `AwaitingInput` | agent asking a question | secondary chip + pulse |
| `Done` | finished successfully | success-green chip *(deviation: spec said primary-tinted; changed to green so Done≠Running per AC-L1-2)* |
| `Error` | failed / cancelled | error chip |

### Card lifecycle
`created (Queued→Running) → (AwaitingInput↔Running) → Done | Error → archived`
Cards are never silently deleted; Done/Error cards remain in the stack and can be
dismissed by swipe (L2) or archived.

---

## 3. The stack — navigation model

- A **roller deck** (`VerticalPager`, `pageSize = Fill`): the **focused** card sits
  ~centred (~360 dp, ~62 % of canvas); up to **three previous cards fan out above
  it**, each recessed (scale, alpha, lower z) so only their top edges peek. The
  next card rests off-screen below and slides up into focus on swipe.
- Motion is **upward**: swiping forward moves every card up — the current card
  lifts into the peek fan, the next rises from below into focus. This mirrors a
  physical rolodex/roller, not a flat list.
- The r1 **scroll wheel** maps to **vertical swipe** on the NX1 (same gesture,
  different input — this is the direct analogue and must feel equivalent).
- **Tap** a card → expand to its streaming detail (L4).
- **Back gesture** → collapse to deck.
- **Swipe** (horizontal) on a card → dismiss/archive (with undo).
- **Empty state** — a single centred prompt: "Hold the button to start."

The stack *is* the multi-agent view (the Paseo differentiator). No separate
"agents" tab — parallelism is shown by multiple cards each streaming.

---

## 4. Input model

- **Push-to-talk** is primary. The side programmable key (or an on-screen
  hold-to-talk button on emulator) raises an **input card** overlay:
  - pulsing "listening…" state
  - live partial transcript
  - **release to commit / drag to cancel**
- **Text** is secondary — a text-entry card for silent/precise input.
- Transcript review: if recognition confidence is low, show the transcript with a
  **Send / Edit** choice before committing (no launching garbage).

---

## 5. r1 borrowings vs divergences

| Borrow from rabbitOS | Diverge (because agents) |
|---|---|
| Home = full-screen launcher | Cards are stateful, not ephemeral |
| Push-to-talk as primary input | Bodies **stream** and grow over time |
| Scroll/swipe card navigation | Cards persist after completion |
| Bold typography, single focus | Multiple cards run concurrently |
| Gesture nav (back, home) | Per-card actions: stop, follow-up |
| Card-based results | Card = session, not result |

---

## 6. What we test first (the unknowns)

These are the questions L1–L4 answer with real testers:

1. **Legibility at 270 dp** — can a person read identity + body + state in <1 s?
2. **State comprehension** — do the six states read as distinct without a legend?
3. **One-handed reach** — is primary action (start/input) in the thumb arc?
4. **"Card = session" model** — does a non-dev tester understand that a card is a
   live thing they can return to, not a finished answer? *(most important)*
5. **Streaming legibility** — is a live-updating body readable, not nauseating?
6. **PTT feel** — does hold-to-talk + listening card feel "like an r1"?

---

## 7. Tokens

Implemented in `apps/nx1-launcher/.../ui/theme/`:

- **Primary:** `R1Orange #FF5A1F` (the r1 signature), `onPrimary` white.
- **Surfaces:** `Paper #F7F5F2` (light) / `Ink #111014` (dark).
- **Shape:** cards `RoundedCornerShape(28.dp)` (r1 uses large radii).
- **Type:** system default at L0; custom type scale deferred to polish.
- State tints map to Material3 `*Container`/`on*Container` roles (AA-safe at
  `labelSmall`); **Done** uses dedicated success-green tokens
  (`DoneContainerLight/OnDoneLight`, `…Dark`) ≈ 6:1 because white-on-r1-orange
  and orange-on-wash both fail AA (4.5:1) for small text. `AwaitingInput` chip
  pulses (alpha 1↔0.45, 900 ms) per §2.
