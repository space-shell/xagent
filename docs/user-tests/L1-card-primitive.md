# L1 — Card primitive (user test)

- **Increment:** L1 — Card primitive
- **Date:** 2026-08-09
- **Build:** `d0fef56` (all six states rendered on-device)
- **Tester:** non-dev (walkthrough)
- **Device:** small Android device (hardware)

## Protocol (AC-L1-2)

Show the six-state stack with **no legend**. Ask the tester to point to:
**running**, **waiting**, **failed**, **done**. Pass = ≥5/6 correct.

## Result

**PASS** — all six states identified correctly, no hesitation or misreads reported.

## Notes

- No state confused the tester; the success-green `Done`, pulsing `AwaitingInput`,
  and `Error` container were read as intended.
- `card-model.md` deviation (Done = success-green, not primary-tinted) is
  validated by this result — kept as-is.

## Follow-ups

- None for L1. Proceed to L2 (card stack & navigation).
