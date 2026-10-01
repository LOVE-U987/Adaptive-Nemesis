# Adaptive Nemesis Changelog

---

## v1.0.17 (2026-10-02)

### 修复：末影龙带冠军词条（Champions 2.10.1.2）生成时血量异常（血条不足 10%）

**问题现象**：MC 1.20.1 + Adaptive Nemesis 最新 + Champions 2.10.1.2 环境下，末影龙在拥有冠军词条生成时血量异常，只有最大生命值 10% 不到。

**根因分析**（Champions 2.10.1.2 源码级确认）：
- Champions 监听 `EntityJoinLevelEvent`（`EventPriority.HIGHEST`，先于 AN 的 NORMAL 优先级执行），通过数据包 `modifier_setting`（默认 `minecraft:max_health`：value=0.35、MULTIPLY_TOTAL，随等级 growthFactor 放大）给实体最大生命值挂 permanent attribute modifier
- AN 的 `BossDamageCapHandler.applyBossBuffs` 设置 Boss 当前血量时使用 `setHealth((float) newMaxHealth)`——该值仅为基础值，不含第三方模组 modifier，导致血量/最大生命值比例 = 1/(1+0.35×等级)，词条等级越高血条越低
- 兜底的"延迟填血"逻辑只存在于 `EnemyScalingHandler.applyHealthBonus`，当自适应缩放被跳过（附近无玩家/实体黑名单/缩放超时）时无任何兜底，血量比例失衡固化到存档

**修复方案**：
- `BossDamageCapHandler.java`: Boss 血量设置改为 `setHealth((float) healthAttr.getValue())`——含第三方模组永久 modifier 的实际最大生命值，血条比例恢复 100% – 新增独立的延迟填血兜底（下一 tick 将血量同步到实际最大值）
- `EnemyScalingHandler.java`: `ORIGINAL_HEALTH_TAG` / `ORIGINAL_DAMAGE_TAG` 改为 public，`getDefaultAttributeBase` 改为 public static 并接受 LivingEntity（1.21.1 为 `Holder<Attribute>`），供 Boss 侧复用统一"真·原始值"判定口径

### 修复：Boss 血量/伤害倍率双重叠加（影响所有 Boss）

- `BossDamageCapHandler.java`: 原始值判定优先级调整为 `BOSS_ORIGINAL_HEALTH_TAG`（旧存档兼容）→ `EnemyScalingHandler.ORIGINAL_HEALTH_TAG`（缩放前记录的真原始值）→ `DefaultAttributes` 查询实体类型默认值 → 当前基础值兜底（伤害同理）
- 此前直接读取当前 `baseValue` 作为原始值——而 EnemyScalingHandler 可能已先执行并把基础值改为缩放后的值，公式 `newMaxHealth = originalHealth × existingScaleMultiplier × bossMultiplier` 中 AN 倍率被应用两次，导致末影龙/凋灵等 Boss 血量数值双重放大

### 功能：宿敌额外自定义掉落战利品表

- `NemesisSystem.java`: 新增 `isNemesis()` 静态识别方法（基于已有 `NEMESIS_TAG` 标记）+ `LivingDeathEvent` 处理——宿敌死亡时 roll 全部配置的战利品表并将产物掉落在死亡位置（原版掉落基础上额外掉落），单表异常不影响其他表
- `NemesisLootDataLoader.java`（新增）: 数据包加载器，从 `data/<namespace>/nemesis_loot/<name>.json` 读取 `loot_tables` 数组，多文件自动合并去重，支持 F3+T 热重载
- `NemesisConfig.java`: 新增 `nemesisLootEnabled`（是否启用，默认 false）与 `nemesisLootTables`（逗号分隔战利品表 ID，默认空）两个配置项，toml 配置与数据包配置合并生效
- 内置示例：`data/adaptive_nemesis/loot_tables/nemesis_example_loot.json`（示例掉落表：钻石/绿宝石/金苹果/附魔金苹果）+ `data/adaptive_nemesis/nemesis_loot/example.json`（引用示例表）；用户数据包参考示例见 `examples/datapack/data/adaptive_nemesis/nemesis_loot/example.json`
- **使用方式**：配置中开启 `nemesisLootEnabled = true` 即可体验内置示例掉落；进阶用户可配置 `nemesisLootTables` 或编写 `nemesis_loot` 数据包定义自己的掉落表

---

## v1.0.16 (2026-08-30)

### 功能：宿敌自动消失

- `NemesisConfig.java`: 新增 `ENABLE_NEMESIS_AUTO_DISAPPEAR`（是否启用）、`NEMESIS_AUTO_DISAPPEAR_SECONDS`（存在时间，默认 300 秒/5 分钟）、`NEMESIS_DISAPPEAR_MESSAGE`（消失时发送提示）三个配置项
- `NemesisSystem.java`: 添加 `nemesisSpawnTimes` Map 追踪每个宿敌的生成时间戳，新增 `onEntityTick()` 定期轮询检查，存活时间超过配置值后自动调用 `discard()` 移除实体
- `NemesisSystem.java`: 在 `convertToNemesis()` 和 `convertToNemesisManual()` 中记录宿敌生成时间，确保命令召唤的宿敌同样受自动消失功能控制
- `zh_cn.json` / `en_us.json`: 新增 `adaptive_nemesis.nemesis.disappear` 翻译键（中文："%s 已经消失了..."，英文："%s has disappeared..."）
- **效果**：宿敌在自然生成或被命令召唤后，经过指定时间自动从世界中消失，避免长期留存造成性能负担或游戏体验失衡

---


### 修复：配置类型改为 SERVER，解决联机时服务端配置不生效

- `AdaptiveNemesisMod.java`: 配置注册类型从 `COMMON` 改为 `SERVER`，服务端成为配置权威，NeoForge 自动同步给所有客户端
- `Config.java`: 新增 `isServerSide()` 方法判断当前环境，`saveToFile()` 增加服务端保护，联机客户端不再尝试写入只读配置
- `AdaptiveNemesisConfigScreen.java`: 增加 `readOnly` 字段，联机客户端控件置灰、显示只读提示，防止误操作
- `AdaptiveNemesisConfigScreen.java`: `markChanged()` 增加只读保护，联机客户端禁止保存
- `en_us.json` / `zh_cn.json`: 新增只读提示本地化键（`readonly_hint`）
- **注意**：配置文件从 `adaptive_nemesis-common.toml` 改为 `adaptive_nemesis-server.toml`，用户需迁移已有配置

### 修复：装备附魔导致存档崩溃（小退后第二次进存档连接中断）

- `EnchantmentScalingHandler.java`: 在服务器关闭时重置附魔缓存（`resetCaches()` 方法），防止单例模式导致 `Holder.Reference<Enchantment>` 引用失效
- `EnchantmentScalingHandler.java`: 在 `applyCoreEnchantments()` 中验证 Holder 有效性，双重检查 `ResourceKey` 是否存在于服务器注册表
- `EnchantmentScalingHandler.java`: 在 `applyModCompatibleEnchantments()` 中添加 `holder.isBound()` 验证，防止网络编码失败
- `InvasionSystem.java`: 在 `applyFrostWalkerBoots()` 中添加 Holder 验证，防止冰霜行者附魔导致网络编码异常
- `ModEventHandler.java`: 在 `ServerStoppingEvent` 中调用 `EnchantmentScalingHandler.resetCaches()`，确保服务器关闭时清除失效引用
- `EnchantmentScalingHandler.java`: 修复 `collectCompatibleEnchantments()` 中的 `Holder.unwrapKey()` API 调用

### 修复：入侵事件无限触发（越杀越多）

- `InvasionSystem.java`: 新增 `playerInvasionCooldowns` Map 跟踪玩家冷却时间（UUID -> 冷却结束时间戳）
- `InvasionSystem.java`: 在 `incrementUndeadKillCount()` 中添加冷却检查，冷却期内显示剩余时间提示
- `InvasionSystem.java`: 入侵触发后自动设置冷却时间（默认 15 分钟，可配置）
- `InvasionSystem.java`: 在 `handleInvasionVictory()` 中清除冷却时间，允许正常触发下一次入侵
- `zh_cn.json` / `en_us.json`: 新增冷却警告翻译（`cooldown_warning` / `cooldown_warning_minutes` / `cooldown_warning_seconds`）
- `InvasionConfig.java`: 已有 `invasionCooldownMinutes` 配置项（默认 15 分钟），无需新增配置

---

## v1.0.15 (2026-08-09)

### 修复：铁魔法法术抗性/强度机制失控（非宿敌怪也被逐只减免）

- `IronsSpellsCompat.java`: 修复 `maxSpellPowerMultiplier` / `maxSpellResistMultiplier` 配置项为死配置的问题，`applyMobBuffs` 现在真正读取这两个值，将法术强度/施法资源倍率封顶到 `maxSpellPowerMultiplier`（默认 4.0），法术抗性倍率封顶到 `maxSpellResistMultiplier`（默认 3.0），改动后修改 `adaptive_nemesis-common.toml` 的 `[enemyBonusCaps]` 段落即可控制怪物法术抗性/强度
- `IronsSpellsCompat.java`: `applyMobBuffs` 增加宿敌判定——仅对打有宿敌 NBT 标记（`NemesisSystem.NEMESIS_TAG`）的怪物应用法术抗性/强度/法力加成，普通怪物不再获得法术类加成，法师玩家的多段、短 CD、召唤类法术在打群怪时不再被逐只减免
- `NemesisSystem.java`: 新增宿敌 NBT 标记常量 `NEMESIS_TAG`，`convertToNemesis` / `convertToNemesisManual` 转化时打上标记
- `SummonNemesisCommand.java`: `/an nemesis` 命令召唤的宿敌同样打上宿敌标记
- `AdaptiveNemesisMod.java`: 将宿敌日常生成系统（`NemesisSystem`）注册提前到敌人强化处理器之前——保证 `EntityJoinLevelEvent` 中宿敌转化（打标记）先于自适应缩放执行，否则宿敌会被当作普通怪处理而拿不到法术类加成（原先注册在 `commonSetup` 中，晚于 `EnemyScalingHandler`）
- `IronsSpellsCompat.java`: `getPlayerSpellStrength` 改用最大法力值（`AttributeRegistry.MAX_MANA`）评估玩家法术强度，不再使用当前法力值，避免施法耗蓝导致强度评估波动、怪物缩放下降

---

## v1.0.15 (2026-08-09)

### 修复：配置界面滚动动画低帧率导致文字闪烁

- `AdaptiveNemesisConfigScreen.java`: 修正滚动动画帧间插值公式，改用 `Mth.lerp(partialTick, lastScroll, currentScroll)` 标准插值，滚动内容不再以 20fps 逐 tick 跳变，文字平滑无闪烁
- `AdaptiveNemesisConfigScreen.java`: `tick()` 中记录上一 tick 滚动位置，并在接近目标时数值收敛对齐，消除动画拖尾抖动
- `AdaptiveNemesisConfigScreen.java`: 缓存条目翻译组件 (`Component`)，避免每帧为 130+ 个条目重复创建翻译对象，降低渲染开销
- `AdaptiveNemesisConfigScreen.java`: 滚动条滑块位置同步使用插值后的滚动值，滚动过程整体更平滑
