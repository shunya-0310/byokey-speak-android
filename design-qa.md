# Tutorial Design QA

- Date: 2026-07-26
- Device: Google Pixel 10a
- Viewport: 1080 x 2400
- References:
  - `J:\マイドライブ\Screenshot_20260726-150117.png`
  - `J:\マイドライブ\Screenshot_20260726-150134.png`
  - `J:\マイドライブ\Screenshot_20260726-150312.png`

## Verification

- Daily News: the spotlight follows the rendered news section bounds.
- New Chat: the spotlight follows only the New Chat button.
- Chat controls: Auto mode, Quick Assist, Web search, and microphone each receive an independent one-control spotlight.
- Settings system: API key and Help receive independent spotlights.
- Settings coach: the tutorial highlights the section tab, switches screens, then highlights CEFR and Coach Skills.
- Coach Skills: the settings content scrolls before the spotlight is shown.
- Launcher icon: the foreground is enlarged without altering the supplied logo, and no dark strip is visible at the mask edge.
- Interaction: Next, Close, OK, automatic section switching, and first-New-Chat triggering were exercised on device.

## Result

No P0, P1, or P2 visual or interaction defects remain in the requested flow.

final result: passed
