# AirdropSystem (AirdropMC)

Steampunk-themed Paper plugin that spawns competitive airdrop crates with events, keys, leaderboards, and server-side integrations.

Licensed under GPL-3.0; see [LICENSE](LICENSE).

## Highlights

- 7-tier crate ladder: Salvage, Patchwork, Ironclad, Aetheric, Brass, Sovereign, Eclipse
- Manual and automatic drop scheduling with zone/world-border placement modes
- Player competition systems (race pressure, first-arrive bonus, leaderboard tracking)
- Event operations (double drops, eclipse trigger, surge waves, seasonal windows, mystery crates)
- Craftable flares and keyed high-tier crates
- Integrations for PlaceholderAPI, WorldGuard, voting workflows, and Discord webhooks

## Requirements

- Java 17+
- Paper 1.20.1+ (Arclight compatible)

Optional integrations:

- WorldGuard 7.x
- PlaceholderAPI
- VotingPlugin or NuVotifier

## Quick Start (Server Owner)

1. Build the plugin:
   - Windows PowerShell: `.\gradlew.bat build`
   - Linux/macOS: `./gradlew build`
2. Copy the generated JAR from `build/libs/` to your server `plugins/` folder.
3. Start server once to generate plugin files.
4. Configure `plugins/AirdropSystem/config.yml`, `events.yml`, `zones.yml`, and loot files under `loot/`.
5. Reload with `/airdrop reload` or restart server.

## Command Snapshot

| Command | Permission | Purpose |
| --- | --- | --- |
| `/airdrop status` | `airdrop.use` | Show active drop details |
| `/airdrop compass` | `airdrop.use` | Get/update tracking compass |
| `/airdrop top [player]` | `airdrop.use` | Show leaderboard or player stats |
| `/airdrop drop [tier] [x] [z]` | `airdrop.admin.drop` | Force a drop |
| `/airdrop event <double\|eclipse\|surge> [minutes]` | `airdrop.admin.event` | Trigger event flow |
| `/airdrop zones <list\|add\|remove> ...` | `airdrop.admin.zones` | Manage zones |
| `/airdrop key give <player> <tier>` | `airdrop.admin.keys` | Give crate keys |
| `/airdrop reload` | `airdrop.admin.reload` | Reload configs |
| `/airdrop history` | `airdrop.admin.history` | Show recent history entries |
| `/airdrop replayfx` | `airdrop.admin.replayfx` | Replay active drop FX |
| `/airdrop delete <all\|nearest\|uuid>` | `airdrop.admin.delete` | Remove active crates |

## Full Documentation

- Player guide: `docs/PLAYER_GUIDE.md`
- Admin guide: `docs/ADMIN_GUIDE.md`
- Developer guide: `docs/DEVELOPER_GUIDE.md`
- Architecture overview: `docs/ARCHITECTURE.md`
- Configuration reference: `docs/CONFIG_REFERENCE.md`
- Repository and release workflow: `docs/REPOSITORY_AND_RELEASE.md`
