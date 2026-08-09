# Adaptive Nemesis Changelog

---

## v1.0.15 (2026-08-09)

### 修复: 铁魔法法术抗性/强度机制失控（非宿敌怪也被逐只减免）

- `IronsSpellsCompat.java`: 修复 `maxSpellPowerMultiplier` / `maxSpellResistMultiplier` 配置项为死配置的问题，`applyMobBuffs` 现在真正读取这两个值，将法术强度/施法资源倍率封顶到 `maxSpellPowerMultiplier`（默认 4.0），法术抗性倍率封顶到 `maxSpellResistMultiplier`（默认 3.0），改动后修改 `adaptive_nemesis-common.toml` 的 `[enemyBonusCaps]` 段落即可控制怪物法术抗性/强度
- `IronsSpellsCompat.java`: `applyMobBuffs` 增加宿敌判定——仅对打有宿敌 NBT 标记（`NemesisSystem.NEMESIS_TAG`）的怪物应用法术抗性/强度/法力加成，普通怪物不再获得法术类加成，法师玩家的多段、短 CD、召唤类法术在打群怪时不再被逐只减免
- `NemesisSystem.java`: 新增宿敌 NBT 标记常量 `NEMESIS_TAG`，`convertToNemesis` / `convertToNemesisManual` 转化时打上标记
- `SummonNemesisCommand.java`: `/an nemesis` 命令召唤的宿敌同样打上宿敌标记
- `AdaptiveNemesisMod.java`: 将宿敌日常生成系统（`NemesisSystem`）注册提前到敌人强化处理器之前——保证 `EntityJoinLevelEvent` 中宿敌转化（打标记）先于自适应缩放执行，否则宿敌会被当作普通怪处理而拿不到法术类加成（原先注册在 `commonSetup` 中，晚于 `EnemyScalingHandler`）
- `IronsSpellsCompat.java`: `getPlayerSpellStrength` 改用最大法力值（`AttributeRegistry.MAX_MANA`）评估玩家法术强度，不再使用当前法力值，避免施法耗蓝导致强度评估波动、怪物缩放下降

---

## v1.0.15 (2026-08-09)

### 修复: 配置界面滚动动画低帧率导致文字闪烁

- `AdaptiveNemesisConfigScreen.java`: 修正滚动动画帧间插值公式,改用 `Mth.lerp(partialTick, lastScroll, currentScroll)` 标准插值,滚动内容不再以 20fps 逐 tick 跳变,文字平滑无闪烁
- `AdaptiveNemesisConfigScreen.java`: `tick()` 中记录上一 tick 滚动位置,并在接近目标时数值收敛对齐,消除动画拖尾抖动
- `AdaptiveNemesisConfigScreen.java`: 缓存条目翻译组件(`Component`),避免每帧为 130+ 个条目重复创建翻译对象,降低渲染开销
- `AdaptiveNemesisConfigScreen.java`: 滚动条滑块位置同步使用插值后的滚动值,滚动过程整体更平滑
