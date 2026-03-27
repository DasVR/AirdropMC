# Admin Guide

## Operations Overview

Use `/airdrop` to control drops, events, zones, keys, and maintenance actions.

## Command Cookbook

| Command | Purpose | Required Permission |
| --- | --- | --- |
| `/airdrop drop tier3` | Spawn a tier3 crate at your location | `airdrop.admin.drop` |
| `/airdrop drop tier6 250 -100` | Spawn a crate at explicit coordinates | `airdrop.admin.drop` |
| `/airdrop event double 45` | Start a 45-minute double-drop window | `airdrop.admin.event` |
| `/airdrop event eclipse` | Trigger eclipse event drop | `airdrop.admin.event` |
| `/airdrop event surge` | Trigger surge sequence | `airdrop.admin.event` |
| `/airdrop zones list` | List configured zones | `airdrop.admin.zones` |
| `/airdrop zones add docks 100 -50 240 80` | Add a named zone | `airdrop.admin.zones` |
| `/airdrop zones remove docks` | Remove a zone | `airdrop.admin.zones` |
| `/airdrop key give PlayerName tier5` | Grant a key | `airdrop.admin.keys` |
| `/airdrop history` | Show recent entries from history log | `airdrop.admin.history` |
| `/airdrop replayfx` | Replay effects at active crate | `airdrop.admin.replayfx` |
| `/airdrop delete nearest` | Remove nearest active crate | `airdrop.admin.delete` |
| `/airdrop delete all` | Remove all active crates | `airdrop.admin.delete` |
| `/airdrop reload` | Reload config, loot, and zones | `airdrop.admin.reload` |

## Permission Matrix

- `airdrop.use` (default true): player utility commands (`status`, `compass`, `top`)
- `airdrop.admin.drop`
- `airdrop.admin.schedule`
- `airdrop.admin.event`
- `airdrop.admin.zones`
- `airdrop.admin.reload`
- `airdrop.admin.history`
- `airdrop.admin.keys`
- `airdrop.admin.replayfx`
- `airdrop.admin.delete`

## Balancing Checklist

- Review `airdrop.tier-weights` and `airdrop.tier-cooldowns` to shape rarity pacing.
- Keep keyed tiers aligned with your economy in `airdrop.keyed-tiers`.
- Tune player conflict with `competition.proximity-radius` and squad split settings.
- Use `airdrop.location-mode` (`zones`, `world-border`, `both`) to control placement quality.
- If automated placement fails often, increase world-border/zoning attempt limits.

## Live Operations Best Practices

- Use `event double` for peak-time activity windows.
- Keep `history.log` enabled for moderation traceability.
- Reserve `delete all` for stuck-state recovery and announce it to players.
- Test all integration triggers after major config changes.

## Troubleshooting

- Drops not appearing:
  - confirm `airdrop.auto-schedule: true`
  - verify at least one valid location mode path is configured
  - check world name and placement constraints
- Placeholder values blank:
  - ensure PlaceholderAPI is installed and plugin toggle is enabled
- Discord alerts missing:
  - validate webhook URL and trigger list in config
