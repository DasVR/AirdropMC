# Configuration Reference

All runtime files are generated under `plugins/AirdropSystem/`.

## Core Files

- `config.yml` - main behavior, balancing, integrations, and presentation controls
- `events.yml` - seasonal windows, event defaults, mystery/defence settings
- `zones.yml` - named placement zones and weights
- `loot/*.yml` - per-tier and seasonal loot pools

## High-Impact `config.yml` Sections

## `airdrop`

- `auto-schedule`, `min-interval`, `max-interval`, `require-players-online`
- `location-mode`: `zones`, `world-border`, or `both`
- `tier-weights`: weighted random chance by tier key
- `tier-cooldowns`: minimum delay before same tier can appear again
- `keyed-tiers`: which tiers require keys

## `competition`

- race tracker and first-arrive pressure settings
- proximity bossbar radius tuning
- squad split tier controls

## `flares`

- enable/disable, cooldown, recipe materials, and resulting tier

## `crate_defence`

- enable guard waves, tier scope, pool composition, stat multipliers, armor presets

## `discord`

- webhook URL, embed mode, and trigger controls

## `placeholderapi`

- toggle for PlaceholderAPI expansion registration

## `vote-integration`

- threshold window and reward behavior

## `bossbar`

- incoming/landed/claimed text templates and timeout fallback messaging

## Placement Controls

- world-border settings under `airdrop.world-border`
- player-centric placement under `airdrop.player-bias`
- zoning safety controls under `zoning`

## Recommended Baseline For Production

- keep `log-drops: true` for moderation/audit
- start with conservative high-tier weights (`tier6`, `tier7`)
- avoid enabling too many simultaneous event amplifiers on low-population servers
- validate all external integrations before announcing new season/event cycles

## Tier Naming Source

The project uses these canonical tier keys and names:

- `tier1` Salvage
- `tier2` Patchwork
- `tier3` Ironclad
- `tier4` Aetheric
- `tier5` Brass
- `tier6` Sovereign
- `tier7` Eclipse
