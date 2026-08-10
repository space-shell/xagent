# User Testing Protocol

Every MVP increment (L0–L4, I0–I2) ships with a user test. This document defines
**how** those tests are run so results are comparable across increments.

## Principle

We never move to the next increment until the current one passes its user test
with a real person. "Works on the emulator" is not done. "A person completed the
task and we observed what confused them" is done.

## Tester

- **Primary (L0–L3):** the developer, acting as a developer-user (owns the daemon,
  knows the stack). Catches functional gaps.
- **Secondary (L4–I2):** one person who is **not** the developer, ideally a
  developer who has never used Paseo. Catches UX/usability gaps — this is where
  the r1-feel is judged.
- Write the tester's role and familiarity in the capture.

## Per-increment test structure

Each test is captured in `docs/user-tests/{L|I}{n}-{slug}.md` using this template:

```
# User Test — M{n} {title}
Date:
Tester: (name, role, Paseo familiarity: none / user / expert)
Device: (model + Android version, or emulator + Android version)
Daemon host:

## Setup
- (one-time: daemon running, agents configured, device paired)

## Tasks
1. (exact instruction given to the tester, verbatim)
2. ...

## Observed
| Task | Success? | Time-on-task | Errors | Notes |
|------|----------|--------------|--------|-------|

## Findings
- What worked:
- What confused / broke:
- Changes required before the next increment:

## Verdict
[ ] PASS — proceed to next increment
[ ] FAIL — list blockers
```

## What we measure

- **Task success** — did they complete it without the developer intervening?
- **Time-on-task** — rough seconds; flags friction on a small screen.
- **Errors** — wrong taps, dead ends, things that needed explaining.
- **Confusion points** — anything they hesitated on or asked about. These drive
  the next increment's design more than the failures do.

## Cadence

- One test at the end of each increment, before starting the next.
- If a test FAILs, fix and re-test only the failing tasks; do not re-run the
  whole suite.
- Findings that are real but out of current scope go to the backlog in
  `docs/mvp.md` §6, not into a silent todo.

## What is explicitly not tested in MVP

- Battery life, thermal, sustained-load perf (post-MVP hardening).
- Tier B kiosk escape-resistance (Tier B is post-MVP).
- Accessibility beyond basic touch target sizes (fast-follow).
