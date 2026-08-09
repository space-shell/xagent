# L2 — Card stack & navigation (user test)

- **Increment:** L2 — Card stack & navigation
- **Build:** (pending — see commit hash in caption)
- **Tester:** dev (Sam)
- **Device:** Bluefox NX1 (hardware)

## Acceptance criteria

- **AC-L2-1** — A 10-card stack scrolls smoothly at 60 Hz.
- **AC-L2-2** — Swipe dismisses a card; an Undo affordance restores it within 5 s.
- **AC-L2-3** — Empty state renders and is self-explanatory ("Hold the button to start.").

## Protocol (run on the NX1)

1. **Scroll** — flick through the 10-card stack top→bottom and back. Note any jank.
2. **Dismiss + undo** — swipe a card fully left (or right); tap **Undo** in the
   Snackbar before it times out (~5 s); confirm the card returns to its place.
3. **Dismiss (commit)** — swipe another card and let the Snackbar time out;
   confirm it stays gone.
4. **Detail** — tap a card → detail view opens (title, state, summary, L4 stub).
   Press **back** → returns to the stack.
5. **Empty state** — dismiss cards until none remain → centered prompt renders.

## Thumb-reach audit

- No primary affordance exists yet (push-to-talk lands in **L3**).
- L2 affordances: card tap (full-width, anywhere — reachable) and the Undo
  Snackbar (bottom of screen, in the thumb arc).
- **L3 requirement:** the PTT button must sit in the bottom ~60% of the 584 dp
  canvas. Reserve that region now; do not push it to the top bar.

## Result

_(to fill in after running)_

- AC-L2-1: pass / fail — notes
- AC-L2-2: pass / fail — notes
- AC-L2-3: pass / fail — notes
