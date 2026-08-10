# L3 — Push-to-talk (user test)

- **Increment:** L3 — Input card + push-to-talk
- **Build:** `be7f9e2` (per-card mic, real SpeechRecognizer)
- **Tester:** non-dev (first non-dev-judged increment)
- **Device:** small Android device (hardware)

## Acceptance criteria

- **AC-L3-1** — Hold→speak→release produces a transcript on the addressed card.
- **AC-L3-2** — Permission denial / unavailable recognizer surfaces a message,
  not a silent dead-end.
- **AC-L3-3** — *(deferred)* Low-confidence prompts Send/Edit.

## Protocol (run on the reference device)

1. **Permission prompt** — first mic press → system RECORD_AUDIO dialog → Allow.
   Confirm listening begins immediately after granting.
2. **Capture** — hold the mic on a card, say a short phrase ("list the files in
   the repo"), release. Confirm the "Listening…" line shows the partial, then
   the final transcript renders as a bubble on that card.
3. **Per-card addressing** — speak to card A, then to card B. Confirm each
   transcript attaches to the correct card, not the other.
4. **Misfire** — tap-and-release the mic without speaking. Confirm a graceful
   "Didn't catch that" snackbar (not a silent hang or stuck listening state).
5. **No recognizer** — *(if the device lacks a recognizer service)* confirm the
   "Voice input unavailable on this device" snackbar appears and the mic does
   not get stuck.

## Thumb-reach audit

- The mic sits bottom-right of each card (~44 dp), inside the thumb arc for the
  centred focused card. Peeking cards' mics are mostly occluded — acceptable;
  the focused card is the primary target.

## r1-feel question (the real test)

After the tasks, ask: *"Does holding that button and talking feel like using an
r1?"* Record the answer verbatim — this is the subjective signal L3 exists to
capture.

## Result

**Confirmed acceptable** (2026-08-09).

- AC-L3-1: not formally run — deferred to next hardware session.
- AC-L3-2: not formally run — deferred to next hardware session.
- AC-L3-3: deferred (as planned).
- r1-feel: User stated: *"Push to talk is acceptable for now."*

The L3 increment is considered closed for the UI-first phase. The formal AC
checklist can be run alongside the next on-device test session if desired, but
it is not blocking.
