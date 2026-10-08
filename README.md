# Wake Ladder

Tell it when you *need* to be up; it rings a ladder of alarms leading to that time
(default: 4 rungs, 20 minutes apart, soft to loud). Replaces setting several alarms by hand
every night.

Key behaviours:

- One input per night; per-weekday default times; optional bedtime nudge.
- Dismissing an early rung only silences it. Only "I'm up" cancels the rest, followed by a
  "still up?" re-check.
- Per-rung volume, ringtone and vibration (vibration optional); optional dismissal task
  (none by default; math / shake / steps).
- Power-off alarm on devices with the Qualcomm power-off alarm service (as in DeskClock).

See [AGENTS.md](AGENTS.md) for build instructions, architecture and current status. License: not yet decided.
