<!-- 
Adaptive Nemesis
Dynamic Difficulty Balancing Mod - Designed for Modpacks
English Version
-->

<div align="center">

<h1>
  ⚔️
  <span style="color: #ff4444; text-shadow: 0 0 20px #ff0000;">Adaptive Nemesis / 自适应宿敌</span>
  ⚔️
</h1>

<p><i>"Your power feeds their evolution"</i></p>

<img src="https://img.shields.io/badge/🎯_Dynamic_Difficulty-FF6B6B?style=flat-square">
<img src="https://img.shields.io/badge/⚖️_Smart_Balance-9B59B6?style=flat-square">
<img src="https://img.shields.io/badge/📦_KubeJS_Support-2ECC71?style=flat-square">
<img src="https://img.shields.io/badge/🌩️_Invasion_Events-F39C12?style=flat-square">

</div>

---

## 📖 Overview

**Adaptive Nemesis** is a **NeoForge 1.21.1** dynamic difficulty balancing mod designed specifically for Minecraft modpacks.

It solves the common modpack problem where early game feels like "scratching with a toothpick" while late game becomes "one-shotting everything." By intelligently evaluating player strength and dynamically adjusting enemy attributes, combat always maintains that "a bit hard, but not too much" thrilling tension.

---

## ⚔️ Core Mechanics

### 1. Player Strength Evaluation System

The mod continuously monitors each player's comprehensive combat power, assessing based on multi-dimensional data:

<div align="center">

| Dimension | Description | Weight |
|---------|------|:---:|
| 🛡️ **Defense** | Armor value, max health, armor toughness | High |
| ⚔️ **Offense** | Attack damage, attack speed, total enchantment coverage | High |
| ✨ **Mythic Affixes** | [Apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis) equipment quality and tier | Medium |
| 🔮 **Iron's Spells** | [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) mana pool, spell power | Medium |
| ⚔️ **Epic Fight** | [Epic Fight](https://www.curseforge.com/minecraft/mc-mods/epic-fight-mod) stamina value, combat style | Medium |

</div>

> All weights can be customized via configuration files, with KubeJS script support for extended evaluation logic!

### 2. Dynamic Enemy Scaling

When hostile mobs spawn, the mod provides adaptive attribute bonuses based on the **average strength of nearby players**:

| Scaling Type | Description | Cap |
|:---|:---|:---|
| **Health** | Max health increase | 500% |
| **Damage** | Attack damage increase | 500% |
| **Armor** | Physical defense increase | 300% |
| **Attack Speed** | Prevents infinite stunlock | 2.0x |
| **Movement Speed** | Keeps pressure during combat | 2.0x |
| **Armor Toughness** | Additional damage mitigation | - |
| **Spell Power** | Iron's Spells compatibility | - |
| **Spell Resistance** | Iron's Spells compatibility | - |
| **Hit Resistance** | Epic Fight compatibility | - |
| **Knockdown Resistance** | Epic Fight compatibility | - |
| **Stamina / Weight** | Epic Fight compatibility | - |

**Attribute Random Distribution**: Each spawned enemy's attributes will fluctuate around the base value (70%~130%), making every battle unpredictable!

```
Base Scaling = Player Comprehensive Strength × Difficulty Coefficient
Float Adjustment = Base Scaling × (0.7 ~ 1.3)  // Attribute random distribution
```

> 🔧 **Anti-Explosion Safeguards**: Original base values are stored in entity NBT and fall back to `DefaultAttributes` when tags are cleared, preventing repeated scaling from causing exponential HP inflation.

**Equipment & Enchantment Scaling**: Humanoid enemies may spawn with scaled gear and enchantments. The system scans `ItemTags` to integrate modded weapons and armor, with a configurable chance to replace vanilla equipment. Dangerous enchantments are automatically blacklisted, and gear drops can be disabled to prevent loot flooding.

### 3. World Stage System

A **server-wide shared** progression layer that permanently raises difficulty as bosses fall:

| Stage | Trigger | Effect |
|:---|:---|:---|
| **Stage 0** | Fresh world | Baseline difficulty |
| **Stage 1+** | Boss killed by a player (Ender Dragon / Wither / Warden, etc.) | +50% enemy strength per stage (configurable) |

> World stage advancement only counts **player kills**, and the stage is shared across all dimensions and players on the server. Maximum stage is configurable to prevent infinite growth.

### 4. Adaptive Float System

Dynamically adjusts difficulty based on real-time player performance:

| Player Behavior | System Response |
|:---|:---|
| **Consecutive Kills** | Float multiplier **+10%** (enemies grow stronger) |
| **Frequent Deaths** | Float multiplier **-15%** (giving you breathing room) |
| **Long Inactivity** | Float multiplier decays toward baseline, then resets |
| **Low Combat Efficiency** | Float multiplier gradually decreases |

> 💡 **Goal**: Always make players feel "a bit hard, but not too much" — just the right challenge.

### 5. True Damage Conversion (Iron Turtle Terminator)

For high-armor players, the mod forcibly converts a portion of damage into **armor-ignoring true damage**:

| Armor Level | True Damage Ratio |
|---------|------------|
| Standard Armor (≤20) | 5% |
| Enhanced Armor (20~50) | 15% |
| High Armor (50~100) | 25% |
| Iron Turtle (>100) | 35% |

> Never fear becoming invincible with god gear! Enemies will find your weakness 🔥

### 6. Nemesis Memory System

Each player has an independent **Nemesis profile**. Enemies remember your combat style and evolve accordingly:

| Memory Type | Records | Enemy Evolution Direction |
|:---|:---|:---|
| **Kill Preference** | Player's common kill methods | Enemies gain corresponding resistances |
| **Death Records** | Damage sources when player dies | Enemies learn corresponding attack patterns |
| **Equipment History** | Equipment combinations player has used | Enemies evolve targeted counter-strategies |
| **Behavior Patterns** | Melee/ranged/magic preferences | Enemies adjust AI behavior |

### 7. Newbie Protection Mechanism

Provides thoughtful protection for low-strength players:

| Trigger Condition | Effect | Duration |
|:---|:---|:---|
| Player Comprehensive Strength < Threshold | Monster attributes **-30%** | Configurable |
| First Death | Protection time **+10 minutes** | Cumulative cap |
| 3 Consecutive Deaths | Force enable protection | Until any enemy is killed |

### 8. Boss Mechanics

Prevents players from one-shotting Bosses, extending epic combat experiences:

- ✅ **Damage Cap**: Single-hit damage has an upper limit (default 100), which increases as Boss health decreases
- ✅ **Attribute Amplification**: Boss health ×5, damage ×3
- ✅ **Combat Tracking**: Records combat duration and cumulative damage
- ✅ **Phase Evolution**: Bosses dynamically adjust attack patterns based on combat duration
- ✅ **Smart Identification**: Recognizes Bosses by keywords, health threshold, or built-in tags

### 9. Invasion Event System

A state-machine driven Undead Invasion that escalates tension across the server:

| Feature | Description |
|:---|:---|
| **Trigger** | Surface undead kill count (25 for first, 100 for subsequent) |
| **Weather** | Triggers thunderstorm on activation |
| **Waves** | Multi-wave assaults scaling with world stage |
| **Spawn Pattern** | Enemies approach from one of four cardinal directions |
| **Boss Bar UI** | Wave progress and elite enemy health bars |
| **Rewards** | Loot tables, experience, and effects for nearby players |

> Custom invasion types can be defined via datapack at `data/<namespace>/invasions/<name>.json`.

---

## 🔗 Mod Compatibility

Adaptive Nemesis natively supports the following popular mods:

<div align="center">

| Mod | Compatible Content |
|:-----|:---------|
| 🔮 **Iron's Spells 'n Spellbooks** | Spell power, mana pool, cooldown reduction, magic resistance |
| ⚔️ **Epic Fight** | Hit resistance, impact, armor breaking, combos, stamina |
| ✨ **Apotheosis** | Equipment quality, mythic affix tier evaluation |
| 🐗 **L2Hostility** | Compatibility mode skips overlapping health/speed scaling |
| 📦 **KubeJS** | Custom events, script extensions, config hot reload |

</div>

---

## ⌨️ Commands

All commands use `/an` prefix, requiring OP permission (level 2):

| Command | Description |
|-----|------|
| `/an status` | View mod current running status |
| `/an strength [player]` | View specified player's strength evaluation data |
| `/an difficulty` | View/adjust difficulty settings |
| `/an protection [player]` | View/manage newbie protection status |
| `/an memory [player]` | View Nemesis memory profile |
| `/an scan [range]` | Scan surrounding enemies' scaling data |
| `/an nemesis [type]` | Summon Nemesis |
| `/an invasion trigger [type] [waves] [difficulty]` | Manually trigger an invasion |
| `/an test [module]` | Test mod module functions |
| `/an reload` | Reload configuration files |
| `/an help` | Display help information |

---

## ⚙️ Configuration System

All mod mechanisms can be finely adjusted through configuration files.

- Config file path: `config/adaptive_nemesis-common.toml`
- Configuration changes made through the in-game config screen are persisted to disk.

---

## 🔧 KubeJS Integration

Automate mod configuration through scripts:

### Available Events

| Event Name | Trigger Timing | Purpose |
|:---|:---|:---|
| `adaptive_nemesis.entity_scale` | When entity attributes are scaled | Customize specific entity scaling multipliers |
| `adaptive_nemesis.damage_calculation` | When true damage is calculated | Adjust damage values or cancel conversion |
| `adaptive_nemesis.player_strength_evaluation` | When player strength is evaluated | Modify final strength calculation |
| `adaptive_nemesis.nemesis_memory_update` | When Nemesis memory updates | Listen for milestones or custom rewards |
| `adaptive_nemesis.world_stage_change` | When world stage advances | React to global progression |
| `adaptive_nemesis.invasion_start` | When invasion begins | Customize waves/difficulty or cancel |
| `adaptive_nemesis.invasion_wave_start` | When each invasion wave starts | Inject custom logic |
| `adaptive_nemesis.invasion_end` | When invasion ends | Modify rewards based on victory/defeat |

### Practical Scenarios

| Scenario | Implementation |
|:---|:---|
| Extra zombie scaling | Check entity ID, multiply scaling |
| Boss double scaling | Check boss type, multiply scaling |
| High-level player difficulty | Add strength based on player level |
| Milestone rewards | Listen for every-10-kills event, grant rewards |
| Global difficulty adjustment | Define global coefficient, unified multiplication |
| Custom invasion types | Create datapacks + hook into KubeJS events |

---

## 📦 Datapack Support

Extend mod content through datapacks without writing code:

- `data/<namespace>/world_stages/<name>.json` — Define stage multipliers, caps, and invasion parameters
- `data/<namespace>/invasions/<name>.json` — Define custom invasion types, waves, enemies, effects, and rewards
- `data/<namespace>/nemesis/<name>.json` — Configure Nemesis profiles and behaviors

Datapacks are hot-reloaded alongside Minecraft's resource reload system.

---

## 🚀 Performance Optimization

| Optimization | Solution | Effect |
|:---|:---|:---|
| **Entity Calculation** | Regional caching + async update | Reduce real-time calculation overhead |
| **Multiplayer Server TPS** | Player-list traversal + distance checks | Reduce server load and avoid chunk-loading deadlocks |
| **Config Hot-Update** | Incremental sync, not full reload | Avoid lag spikes |
| **Memory System Storage** | On-demand loading, periodic archiving | Control save file size |
| **Watchdog Service** | Detects scaling timeouts and marks problematic entities | Prevents server freezes |

---

## 📖 Design Philosophy

> *"Your power feeds their evolution"*

Adaptive Nemesis's core concept is **dynamic balance**:

1. **Never weaken players** — Players can still enjoy the thrill of growing stronger
2. **Match enemies to strength** — Enemies always scale with player power
3. **Learn and counter** — Enemies learn player combat styles and evolve targeted counters
4. **Protect newbies** — Give new players enough room to grow
5. **Challenge veterans** — Keep experienced players constantly challenged
6. **Reward progression milestones** — World stages and invasions turn power spikes into memorable events

Whether you're a newcomer just starting a modpack or a veteran in full god gear, Adaptive Nemesis provides the right combat experience!

---

<div align="center">

**⚔️ Every time you grow stronger, your Nemesis evolves ⚔️**

[![CurseForge](https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://curseforge.com)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/anadaptive-nemesis)
[![GitHub](https://img.shields.io/badge/GitHub-Source-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/LOVE-U987/Adaptive-Nemesis/issues)

</div>
