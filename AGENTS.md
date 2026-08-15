# Glossary

Terminology used in this repo. See `docs/card-model.md` for the full UI spec
and `README.md` for build instructions.

## UI surfaces

- **Card** — generic term for one full-screen panel in the deck. Cards are
  `RoundedCornerShape(28.dp)` with a 2dp border, sized ~270 × 360 dp on the
  reference canvas.
- **Deck (roller deck)** — the card stack / navigation model in
  `LauncherScreen.kt`. The focused card sits centred; up to three previous
  cards fan out above (alpha ramp, lower z-index); the next card slides up
  from below. Vertical drag or nested-scroll overflow pages the deck.
- **Session card (agent card)** — a card representing one Paseo agent session
  (`AgentCard.kt`). Long-lived and stateful: background colour + watermark
  icon encode the agent state; contextual bottom row shows mic or Allow/Deny.
- **Home card** — the first card in the deck (`HomeCard.kt`). Hosts the
  session-creation wizard and session shortcuts.
- **Apps card** — quick-launch app shortcuts plus an "All apps" in-card
  browser of every launchable app on the device (`AppsCard.kt`).
- **Settings card** — toggles (status bar, sidebar side, keep-alive) and the
  add-connection entry point (`SettingsCard.kt`).
- **Connection card** — one per connection profile; host/password entry, QR
  scanner, connect/disconnect/delete (`ConnectionCard.kt`).
- **Sidebar (status rail)** — the vertical icon rail on the screen edge
  (`StatusRail` in `LauncherScreen.kt`): Home, attention dots, Apps,
  Settings. Position is user-configurable (left / right / off).

## Session lifecycle

- **Wizard** — the multi-step session-creation flow on the Home card:
  Welcome → (Server) → Project → Model → Prompt → Creating → Created.
  Server step is skipped when only one daemon is connected. Swiping the
  wizard right reveals a red "Restart" affordance.
- **Session shortcut** — a saved `server · workspace · model` chip on the
  Welcome step; tapping it jumps straight to the Prompt step.
- **Agent states** — `Idle`, `Queued`, `Running`, `AwaitingInput` (permission
  gate), `Done`, `Error`. Encoded redundantly via card background colour,
  watermark icon, and the status rail.
- **Attention dot** — a red dot in the sidebar for a session card in
  `AwaitingInput` or `Error`; tap to jump to that card.
- **Plan / Build modes** — per-session toggle (long-press the card). Plan is
  read-only; Build executes with permission gates.
- **Archive** — removes a session card and archives the underlying agent.
  Optimistic: the card disappears immediately and is restored only if the
  daemon rejects the request.
- **Push-to-talk (PTT)** — hold the mic button to speak; input goes through
  Android `SpeechRecognizer` (`VoiceController.kt`).
- **Transcript** — the recognised speech shown as a bubble for confirmation
  before it is sent (or used as the new session's prompt).

## Connectivity

- **Daemon (Paseo)** — the server-side process that actually runs agents
  (Claude Code, Codex, OpenCode, Copilot, Pi). The launcher is a thin remote
  control over it.
- **Connection profile** — a saved daemon connection (`ConnectionProfile`).
  Two types:
  - **DIRECT** — WebSocket to `host:port` with optional bearer password
    (LAN / Tailscale).
  - **RELAY** — connection routed through the Paseo relay, end-to-end
    encrypted.
- **QR connection (QR pairing)** — scanning a Paseo offer URL/QR on a
  connection card configures a RELAY profile (server id, daemon public key,
  relay endpoint) and connects.
- **E2EE** — X25519 key agreement + encryption applied to all relay traffic;
  the relay only sees ciphertext.
- **Remerge** — `ConnectionManager`'s merge of the per-connection agent lists
  into the single deck order (direct connections first, deduplicated).
