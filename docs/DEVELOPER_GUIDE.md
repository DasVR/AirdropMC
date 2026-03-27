# Developer Guide

## Stack And Requirements

- Language: Java 17
- Build: Gradle 8.4 wrapper
- Platform API: Paper 1.20.1
- Optional compile-time hooks: WorldGuard, PlaceholderAPI
- Bundled libraries: `org.json`, `sqlite-jdbc` (shaded artifact build)

## Local Build

Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

Output: `build/libs/AirdropSystem-2.0.0.jar`

## Runtime Install Loop

1. Copy JAR to test server `plugins/`.
2. Start server once.
3. Edit generated plugin files in `plugins/AirdropSystem/`.
4. Use `/airdrop reload` while iterating config behavior.
5. Restart server after code changes.

## Core Modules

- `AirdropPlugin`: bootstrap and service wiring
- `AirdropManager`: spawn lifecycle and active crate control
- `AirdropScheduler`: automatic cadence and timing
- `AirdropCommand`: command handlers and tab completion
- `EventDropManager`: event modes and seasonal checks
- `ZoneManager`: location selection and zone persistence
- `KeyManager`: key items and recipe registration
- `LeaderboardManager`: SQLite stats storage
- `PlaceholderHook`: `%airdrop_*%` expansion
- `DiscordHook`: webhook payload delivery

## Extension Points

- Add command features in `AirdropCommand`.
- Add event behavior in `EventDropManager`.
- Add integrations by following the pattern in `PlaceholderHook` / `DiscordHook`.
- Add new balancing controls via `ConfigManager` + `config.yml`.

## PlaceholderAPI Keys

- `%airdrop_next%`
- `%airdrop_active%`
- `%airdrop_active_tier%`
- `%airdrop_active_coords%`
- `%airdrop_total_opens%`
- `%airdrop_top_player%`
- `%airdrop_event_active%`
- `%airdrop_event_name%`

## Testing Checklist (Manual)

- Spawn each tier manually with `/airdrop drop tierN`.
- Verify key-gated tiers reject/open correctly.
- Validate event commands (`double`, `eclipse`, `surge`).
- Confirm `status`, `compass`, `history`, `delete` behavior.
- Confirm PlaceholderAPI expansion resolves values.
- Confirm Discord triggers fire for enabled events.

## Troubleshooting

- Plugin loads but commands fail:
  - verify command registration in `plugin.yml`
  - check startup log for manager initialization failures
- Build works, runtime fails:
  - verify Java version on server is 17+
  - confirm server is Paper/Arclight-compatible
- Data issues:
  - inspect plugin data directory for `airdrop_stats.db`, history log, and config correctness
