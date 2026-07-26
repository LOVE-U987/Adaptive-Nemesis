<div align="center">

<h1>
  ⚔️
  <span style="color: #ff4444; text-shadow: 0 0 20px #ff0000;">Adaptive Nemesis</span>
  <span style="color: #888888;">v1.0.12</span>
  ⚔️
</h1>

<img src="https://img.shields.io/badge/🎯_Dynamic_Difficulty-FF6B6B?style=for-the-badge&logo=minecraft&logoColor=white">
<img src="https://img.shields.io/badge/⚖️_Smart_Balance-9B59B6?style=for-the-badge&logo=shield&logoColor=white">
<img src="https://img.shields.io/badge/📦_KubeJS_Support-2ECC71?style=for-the-badge&logo=code&logoColor=white">
<img src="https://img.shields.io/badge/🌩️_Invasion_Events-F39C12?style=for-the-badge&logo=fire&logoColor=white">

<p><i>"Your power feeds their evolution."</i></p>
<p><i>They weren't kidding.</i></p>

</div>

---

## 🎬 What Is This?

You know the drill. You start a modpack. Early game: you're scratching zombies with a wooden stick, questioning your life choices. Late game: you're in full god gear one-shotting everything that moves, bored out of your mind.

**Adaptive Nemesis** breaks that loop.

It watches you. It learns. And it makes sure the world stays dangerous — no matter how strong you get.

---

## ⚔️ Core Systems

### 🧠 Player Strength Evaluation

The mod doesn't just look at your armor bar. It reads your entire combat profile:

<div align="center">

| What It Sees | Why It Matters |
|:---|:---|
| 🛡️ **Armor & Health** | Tank builds don't go unpunished |
| ⚔️ **Attack Stats** | Glass cannons meet glass resistance |
| ✨ **Apotheosis Gear** | That mythic affix? They noticed. |
| 🔮 **Iron's Spells Mana** | Mages get scaled against too |
| ⚔️ **Epic Fight Stamina** | Your combat style is on file |

</div>

> 💡 *Everything is weighted. Everything counts. And yes — you can tweak the weights in config.*

---

### 📈 Dynamic Enemy Scaling

Every hostile mob that spawns gets a personal gift from the system:

<div align="center">

| Attribute | Scaling | Hard Cap |
|:---|:---|:---:|
| **Health** | Based on nearby player strength | 500% |
| **Damage** | You hit hard? They hit harder. | 500% |
| **Armor** | Your sword will feel it. | 300% |
| **Attack Speed** | No more infinite stunlock. | 2.0x |
| **Movement Speed** | Running won't save you. | 2.0x |

</div>

**The twist:** Every mob rolls 70%~130% of the base scaling. Two zombies from the same spawn? Different personalities. Different threat levels. You'll never know which one is the problem child.

> 🔥 *"I saw a zombie with 80 HP and laughed. Then I saw the one next to it had 240 HP and a diamond sword. I stopped laughing."*

**Anti-Explosion Safeguards:** Base values are stored in NBT. If tags get cleared, the mod falls back to `DefaultAttributes`. No exponential HP inflation. No broken saves.

**Equipment Scaling:** Humanoid enemies can spawn with scaled gear and enchantments. The system scans `ItemTags` to pull from your modpack's weapon/armor pool. Dangerous enchants are blacklisted. Drops can be disabled so you don't drown in loot.

---

### 🌍 World Stage System

This is server-wide. Permanent. And it doesn't care about your feelings.

<div align="center">

| Stage | How You Got Here | What Changes |
|:---:|:---|:---|
| **0** | Fresh world | Baseline. Enjoy it while it lasts. |
| **1+** | A player killed a Boss (Dragon, Wither, Warden...) | +50% enemy strength per stage |

</div>

> ⚡ *Only player kills count. The stage is shared across all dimensions. And it only goes up.*

---

### 🎚️ Adaptive Float System

The mod doesn't just scale once and call it a day. It watches your **performance in real time**:

<div align="center">

| You Do This | The World Does This |
|:---|:---|
| Chain kills like a maniac | Float multiplier **+10%** — they grow |
| Die. A lot. | Float multiplier **-15%** — breathing room |
| AFK at your mob farm | Decay kicks in. The world forgets you were scary. |
| Glass cannon build (high damage, low survivability) | Efficiency check. Difficulty reels back. |

</div>

> 🧠 *"The system learned to read your true skill. Not just your DPS."*

---

### 💀 True Damage Conversion (Iron Turtle Terminator)

Wearing 100+ armor and feeling invinc? Cute.

<div align="center">

| Your Armor | What Gets Through |
|:---:|:---:|
| ≤20 | 5% true damage |
| 20~50 | 15% true damage |
| 50~100 | 25% true damage |
| >100 | 35% true damage |

</div>

> *Armor still matters. But it no longer makes you a god.*

---

### 👹 Nemesis Memory System

Every player gets a **personal file**. The enemies remember:

<div align="center">

| Memory Type | What They Learn | How They Adapt |
|:---|:---|:---|
| **Kill Preference** | How you usually kill | They gain resistances to your favorite method |
| **Death Records** | What killed you last | They start using that against you |
| **Equipment History** | What gear you run | They evolve counters to your build |
| **Behavior Patterns** | Melee / Ranged / Magic | Their AI shifts to exploit your habits |

</div>

> *"I always opened with a fireball. Then the skeletons started spawning with Fire Resistance. Every. Single. Time."*

---

### 🛡️ Newbie Protection

Not everyone starts as a legend. The mod knows:

<div align="center">

| Trigger | Effect |
|:---|:---|
| Player strength below threshold | Monster stats **-30%** |
| First death | Protection extended by 10 minutes |
| 3 deaths in a row | Forced protection until you kill something |

</div>

> *The system wants you to suffer. But it wants you to stick around to suffer more.*

---

### 🐉 Boss Mechanics

Boss fights shouldn't end in two hits.

<div align="center">

| Mechanic | What It Does |
|:---|:---|
| **Damage Cap** | Single-hit damage capped (default 100), cap rises as Boss HP drops |
| **Attribute Amp** | Boss HP ×5, damage ×3 |
| **Combat Tracking** | Records fight duration and total damage dealt |
| **Phase Evolution** | Attack patterns shift as the fight drags on |
| **Smart ID** | Recognizes Bosses by tag, type, name, or health threshold |

</div>

---

### 🌩️ Invasion Event System

Sometimes the world doesn't wait for you to find trouble.

<div align="center">

| Feature | Detail |
|:---|:---|
| **Trigger** | Kill 25 surface undead (first time), 100 (after) |
| **Weather** | Thunderstorm. Because atmosphere matters. |
| **Waves** | Multi-wave assaults scaling with world stage |
| **Direction** | Enemies come from North, South, East, or West |
| **UI** | Boss bar tracks wave progress |
| **Rewards** | Loot, XP, and effects for nearby players |

</div>

> Custom invasions via datapack: `data/<namespace>/invasions/<name>.json`

---

## 🔗 Mod Compatibility

<div align="center">

| Mod | What Gets Integrated |
|:---|:---|
| 🔮 **Iron's Spells 'n Spellbooks** | Spell power, mana, cooldowns, magic resistance |
| ⚔️ **Epic Fight** | Hit resistance, impact, armor breaking, combos, stamina |
| ✨ **Apotheosis** | Gear quality, mythic affix tier evaluation |
| 🐗 **L2Hostility** | Compatibility mode skips overlapping scaling |
| 📦 **KubeJS** | Full event API, script extensions, config hot reload |

</div>

---

## ⌨️ Commands

All `/an` commands. OP level 2 required.

<div align="center">

| Command | What It Does |
|:---|:---|
| `/an status` | Is the mod alive? What's it thinking? |
| `/an strength [player]` | Full combat profile breakdown |
| `/an difficulty` | Tweak the numbers |
| `/an protection [player]` | Manage newbie shields |
| `/an memory [player]` | Read the Nemesis file |
| `/an scan [range]` | Inspect nearby scaled enemies |
| `/an nemesis [type]` | Summon a personal nightmare |
| `/an invasion trigger [type] [waves] [difficulty]` | Start the storm manually |
| `/an test [module]` | Debug tools |
| `/an reload` | Hot reload configs |
| `/an help` | When you forget the above |

</div>

---

## 🔧 KubeJS Integration

Script your own chaos.

<div align="center">

| Event | When It Fires | What You Can Do |
|:---|:---|:---|
| `adaptive_nemesis.entity_scale` | Mob spawns | Override scaling multipliers per entity |
| `adaptive_nemesis.damage_calculation` | True damage triggers | Adjust or cancel conversion |
| `adaptive_nemesis.player_strength_evaluation` | Strength recalculated | Modify final score |
| `adaptive_nemesis.nemesis_memory_update` | Memory milestone | Custom rewards, triggers |
| `adaptive_nemesis.world_stage_change` | Stage advances | React to global progression |
| `adaptive_nemesis.invasion_start` | Invasion begins | Modify waves/difficulty or **cancel** |
| `adaptive_nemesis.invasion_wave_start` | Each wave starts | Inject custom logic |
| `adaptive_nemesis.invasion_end` | Invasion ends | Modify rewards based on outcome |

</div>

---

## 📦 Datapack Support

No code required. Just JSON and spite.

- `data/<namespace>/world_stages/<name>.json` — Stage rules, caps, invasion params
- `data/<namespace>/invasions/<name>.json` — Custom invasions: waves, enemies, effects, rewards
- `data/<namespace>/nemesis/<name>.json` — Nemesis profiles and behaviors

Hot-reloaded with F3+T.

---

## 🚀 Performance

| Problem | Solution |
|:---|:---|
| Entity calculation lag | Regional caching + async updates |
| TPS drops on servers | Player-list traversal + distance checks |
| Config reload lag | Incremental sync, not full rebuild |
| Memory bloat | On-demand loading, periodic archiving |
| Scaling timeout | Watchdog detects and marks bad entities |

---

## ⚔️ The Point

> *"Your power feeds their evolution."*

This isn't a mod that makes the game harder. It's a mod that makes the game **pay attention**.

You want to feel your progression? You'll feel it. The enemies will make sure of it.

You want to coast in god gear? Not here. The world scales. It learns. It remembers.

**Adaptive Nemesis** doesn't nerf you. It respects you enough to stay dangerous.

---

<div align="center">

**⚔️ Every time you grow stronger, your Nemesis evolves. ⚔️**

[![CurseForge](https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://curseforge.com)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com)
[![GitHub](https://img.shields.io/badge/GitHub-Source-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com)

</div>

