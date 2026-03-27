# Player Guide

## What This Plugin Adds

Airdrops are loot crates that fall into the world at intervals or during events. Players race to reach, defend, and open them for rewards.

## Crate Tiers

| Tier Key | Display Name | Key Required |
| --- | --- | --- |
| `tier1` | Salvage | No |
| `tier2` | Patchwork | No |
| `tier3` | Ironclad | No |
| `tier4` | Aetheric | Yes |
| `tier5` | Brass | Yes |
| `tier6` | Sovereign | Yes |
| `tier7` | Eclipse | Yes |

## Typical Player Loop

1. Watch chat/bossbar announcements for drop alerts.
2. Use `/airdrop status` to check tier and coordinates.
3. Use `/airdrop compass` to receive or retarget your airdrop tracker.
4. Reach and secure the location.
5. Open the crate (high tiers may require a key).
6. Track your progress with `/airdrop top` or `/airdrop top <player>`.

## Useful Player Commands

- `/airdrop status` - show active drop world, coordinates, and tier
- `/airdrop compass` - get/update a lodestone compass for the active crate
- `/airdrop top` - show top 10 openers
- `/airdrop top <player>` - view a specific player's stats

## Events You May See

- `double` period: increased drop frequency for a configured duration
- `eclipse`: manually triggered top-tier event-style drop
- `surge`: rapid sequence of multiple lower-tier drops
- mystery crates: random chance to produce a surprise crate variant
- seasonal windows: date-range loot/event modifications
- crate defence: guards can spawn around higher-tier drops

## Keys And Access

Higher tiers (`tier4` to `tier7`) are keyed by default. Keys may be crafted (if recipes are enabled) or granted by staff.

## Common Player Troubleshooting

- No active drop: wait for scheduler, ask staff to verify scheduling, or check if an event is currently running.
- Compass not updating: run `/airdrop compass` again while a drop is active.
- Cannot open crate: verify you have the right key tier.
