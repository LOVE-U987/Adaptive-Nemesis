# Adaptive Nemesis Changelog

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
