# L2 — Card stack & navigation (user test)

- **Increment:** L2 — Card stack & navigation
- **Build:** roller deck — `044631b` (peek-stack fix) onward
- **Tester:** dev (Sam)
- **Device:** Bluefox NX1 (hardware)

## Acceptance criteria

- **AC-L2-1** — A 10-card deck scrolls smoothly at 60 Hz.
- **AC-L2-2** — Swipe dismisses a card; an Undo affordance restores it within 5 s.
- **AC-L2-3** — Empty state renders and is self-explanatory ("Hold the button to start.").

## Protocol (run on the NX1)

1. **Scroll** — swipe up/down through the 10-card roller deck; confirm the focused
   card centres and up to three previous cards fan out above it. Note any jank.
2. **Dismiss + undo** — swipe a card fully left (or right); tap **Undo** in the
   Snackbar before it times out (~5 s); confirm the card returns to its place.
3. **Dismiss (commit)** — swipe another card and let the Snackbar time out;
   confirm it stays gone.
4. **Detail** — tap a card → detail view opens (title, state, summary, L4 stub).
   Press **back** → returns to the deck.
5. **Empty state** — dismiss cards until none remain → centred prompt renders.

## Thumb-reach audit

- No primary affordance exists yet (push-to-talk lands in **L3**).
- The focused card is centred (~360 dp), leaving the bottom ~112 dp empty — the
  natural seat for the L3 push-to-talk control (bottom ~60% of the 584 dp canvas).
- L2 affordances: card tap (full-width — reachable) and the Undo Snackbar (bottom).

## Result

- **AC-L2-1: PASS** — roller deck scrolls smoothly; fan of three peeks renders
  correctly above the centred focused card (confirmed on-device, build `044631b`).
- **AC-L2-2: carried** — dismiss+undo code retained from the flat-list build;
  quick re-verify on the roller build on the next device session.
- **AC-L2-3: carried** — empty state unchanged; re-verify with AC-L2-2.
