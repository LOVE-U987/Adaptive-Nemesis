# Adaptive Nemesis Changelog

---

## v1.0.19 (2026-11-08)

### 修复：全项目代码审查问题批量修复（56 项，2026-11-08）

**背景**：2026-11-07 对全项目（96 个 Java 文件，约 22,500 行）完成系统性代码审查，确认高危 8 项、中危约 20 项、低危与规范债约 30 项。最系统性问题是"断线功能"——11 个子系统注释宣称已实现但实际未接入游戏逻辑。本次按审核通过的计划（plan-full-review-fix v1.2）全部修复，计划文件在 audit 通过后删除。

**断线功能修复**（高危）：
- 新手保护减免接入怪物缩放管线（此前 `isProtected` 只有命令查询在消费，保护从未生效）
- 真实伤害系统接线 + 无敌帧修复：`markInitialized` 接入配置加载；新增自定义 DamageType `adaptive_nemesis:true_damage`（注册名新增，非变更），bypasses_armor/bypasses_cooldown 双标签数据包；延迟任务改用带原始攻击者归因的 DamageSource（死亡消息与仇恨指向攻击方），入口增加再入排除防止二次转化
- 宿敌转化防重复守卫：`loadedFromDisk` + `isNemesis` 双保险，区块重载/跨维度不再重复转化导致属性指数叠加
- 宿敌档案持久化：新建 `NemesisProfileSavedData`（挂 overworld），档案创建即写入、登出序列化+内存移除、登入按需读回；修正"档案已保存"误导日志
- 世界阶段持久化：`WorldStageSavedData` 空壳改为真实现（stage + 已击败 Boss 集合 + 推进时间戳），三处读写统一 `server.overworld()`，`serverLevelRef` 停服置 null；内容不变不写盘避免无效落盘
- 装备/附魔缓存恢复 `cachesBuilt` 快速路径（此前每只怪生成都全量扫描注册表）
- 入侵死亡结算：死亡即按失败结算并发 KubeJS 结束事件、按 `clearMobsOnFailure`（新，默认 true）清理存活入侵怪，不再给死亡玩家发胜利奖励
- 看门狗生命周期：仅专用服务器在 `ServerStartedEvent` 启动、首次心跳前 armed 不判定、UUID 问题实体键、换档可重启；新增只读状态查询
- 假命令修复：`/an protection enable` 真实启用、`/an difficulty set` 真实写配置、`/an invasion setdifficulty` 接入自定义难度配置

**行为与数值修复**（中危）：
- 入侵：目标玩家离线暂停状态机（超时 `offlineAbortTicks` 新配置判失败）、难度倍率下限 0.1 钳制（不再产生 0 血怪）、血条基准改用实体类型默认属性、僵尸村民血量随硬编码表删除、怒气提速死逻辑移除与铁傀儡名单修正
- 数据包 6 个死字段接线：`spawn_distance`、`invasion_max_waves`、每波 `difficulty_multiplier`、波内 `weight` 加权抽样；`nameKey`/`spawn_direction` 加载时 warn 不再静默忽略
- 波数/难度阶段表迁入 `InvasionFixConfig`（默认值与原硬编码一致）；入侵通知半径独立配置（默认 32.0）
- 宿敌：生成计时改随实体 persistentData（重启不再重置）、`nemesisSpawnTimes` 泄漏治理、称号阈值与记忆三系数迁入数据包配置（默认值不变）
- 缩放：非血量属性 NBT 丢失时回退实体类型默认值（清 NBT 后二次缩放不再叠加）、韧性补上限、核心附魔互斥与适用性过滤（移除剑上效率/护甲深海探索者等）、装备难度公式与属性缩放统一（共享缓动/浮动/上限/空间索引）
- 杀牛羊村民不再计入"击杀敌人"（`instanceof Enemy` 判定）、非玩家击杀不再推进世界阶段
- 召唤宿敌：属性加成全部经配置上限钳制、补 `finalizeMobSpawn` 调用（骷髅不再空手落地）、魔数常量化
- 配置界面：值变更才写内存、确认才落盘、ESC 取消做内存快照回滚（打开界面零写盘）；主菜单点配置改为延迟一帧提示屏
- 兼容层：Apotheosis 评估按 1.21.1 data components 实际格式重写（旧 NBT 键已不存在，原实现静默失效），全零结果 warn 一次；`ModCompatManager` 配置开关现读不固化；KubeJS 入侵 API 真实接线（`registerBindings(BindingRegistry)` 注册 `AdaptiveNemesis` 绑定对象，脚本可调 `triggerUndeadInvasion`/`isInInvasion`/`getInvasionProgress`），删除从未生效的 `InvasionKubeJsSupport`
- 击杀记忆键/武器分类改用注册 ID（不再依赖 `toString()` 隐式格式）；KubeJS 反射调用失败熔断（3 次禁用本会话事件，防日志刷屏）

**资源与结构**：
- 战利品表目录 `loot_tables/` 改名 `loot_table/`（1.21.1 单数，原目录下示例表静默解析不到）
- `Config.java` 归位至包声明对应目录；`GeneralConfig` 补 `push("general")`（旧配置顶层 `difficultyBaseMultiplier` 需重新设置一次）
- 删除无引用死资源 `assets/adaptive_nemesis/textture/`（原拼写错误目录）
- 死代码清理约 15 处；全部注释/日志 Emoji 清除；超 50 行方法全部拆分（含 745 行的配置屏 init）；缩放/空闲阈值等硬编码迁配置（新增 `enchantmentChanceCap`、`idleDecayThresholdSeconds` 等，默认值与原行为一致）

**测试**：新增 JUnit 单测 4 组（世界阶段 NBT round-trip、宿敌档案 NBT、缩放钳制、入侵数据容错），扩展真实伤害测试（初始化门槛/再入排除/钳制），共 172 个用例全绿；`/an test verify` 新增 9 项游戏内自检（真实伤害注册、新手保护倍率演示、宿敌守卫、假命令回读、0 倍率钳制、持久化重启验证入口等）。

**迁移提醒**：从 v1.0.18 升级后，`config/adaptive_nemesis-common.toml` 中顶层 `difficultyBaseMultiplier` 不再被读取（结构统一迁入 `general` 分节），请重新设置一次；入侵通知半径默认值由随生成区域 70 格改为独立 32 格，可在配置中调整。

### 修复：KubeJS 伤害事件取消语义改为显式标记（2026-11-08）

**问题现象**：`adaptive_nemesis.damage_calculation` 事件的取消判定此前依赖"返回伤害值等于原始伤害"，导致脚本将真实伤害设为与原始伤害相同（即全额真实伤害）时被静默忽略；同时真实伤害比例配置到 100% 时（即使未安装 KubeJS 也会走同一返回值路径）同样被误判为取消。

**修复**：`KubeJSEventTrigger` 新增 `DamageCalculationResult`（显式 cancelled 标记 + 伤害值），`fireDamageCalculation` 返回该结果，`TrueDamageHandler` 仅依据显式取消标记跳过转化；数值相等不再影响转化判断。脚本取消真实伤害转换仍使用 `event.cancelEvent()`。

### 已知问题：world_stage_change 事件的取消与倍率修改暂不生效（2026-11-08）

`adaptive_nemesis.world_stage_change` 事件中 `cancelEvent()` 与 `setStageMultiplier()` 当前不影响实际逻辑：事件在阶段推进写盘之后触发，返回的阶段倍率仅用于日志展示，后续缩放链路仍按阶段配置重新计算。计划后续版本将事件返回倍率持久化为阶段覆盖值（或明确取消语义），本轮仅记录不修复。

---

## v1.0.18 (2026-10-18)

### 修复：铁魔法联动失效，AN 强化怪物无法获得法术加成（2026-11-07）

**问题现象**：安装 Iron's Spells 'n Spellbooks 后，被 AN 强化的怪物完全吃不到法术类加成（法术强度/法力/施法效率/法术抗性全部无效），联动形同虚设。

**根因分析**（两道门槛互相锁死）：
- `IronsSpellsCompat.applyMobBuffs` 开头检查宿敌标记 `NEMESIS_TAG`，非宿敌直接返回——普通被强化的怪拿不到任何法术加成
- `NemesisSystem.onEntitySpawn` 仅处理 `instanceof Monster` 的实体，而铁魔法施法怪（`AbstractSpellCastingMob` 系）继承 `PathfinderMob` 而非 `Monster`，永远无法转化为宿敌、永远打不上标记
- 两者相乘：会施法的怪永远不是宿敌，是宿敌的怪不会施法，法术加成路径对铁魔法生物完全死路
- 附带数值隐患：铁魔法法术伤害公式为 基础值 x 通用法术强度 x 学派法术强度，旧实现把两个因子乘同一倍率，倍率 2x 实际伤害 4x（平方爆炸）；九大学派抗性用 `(mult-1)*5` 加法，2x 倍率时约 95% 法术减伤接近免疫；通用 `SPELL_RESIST` 完全未使用

**修复方案**：
- `NemesisSystem`：宿敌转化条件由 `instanceof Monster` 放宽为 `Mob + Enemy`（与敌人缩放处理器 `onEntityJoinLevel` 口径对齐），铁魔法施法怪可按概率转为宿敌；`isBoss` 接入全局 Boss 识别服务（null 安全，未初始化时兜底硬编码名单），死灵之王/火焰领主等模组 Boss 不会被转成宿敌；客户端判断前置
- `IronsSpellsCompat`：门槛重构为双通道——实现 `IMagicEntity` 的施法怪获得法术强度/法力/施法效率加成（与是否宿敌无关），宿敌获得法术抗性加成，普通怪零改动（保留法师 AoE 不被逐只减免的设计）
- 数值修复：删除九大学派法术强度乘算（只乘通用 `SPELL_POWER`，消除平方爆炸）；删除九大学派抗性 `(mult-1)*5` 加法，改为通用 `SPELL_RESIST` 温和乘算（属性定义基线 1.0，`softCap` 公式天然渐近封顶：2x 倍率约 75% 减伤，3x 配置默认上限约 87.5%，旧方案 2x 约 95%）
- `NemesisNameGenerator`：`generateNemesisName`/`determinePrefix` 参数由 `Monster` 放宽为 `Mob`（方法体仅使用 `getType` 级 API）
- `maxSpellPowerMultiplier`（默认 4.0）/ `maxSpellResistMultiplier`（默认 3.0）封顶配置语义不变，继续生效

**防复发要点**：
- "谁能成为宿敌"与"谁能被缩放"应使用同一判定口径（`Mob + Enemy` 接口）；用 `Monster` 类判定会系统性排除 `PathfinderMob` 系模组怪
- 铁魔法伤害/抗性公式为多因子相乘结构，同一倍率不得同时乘多个因子，否则按倍率平方增长
- 属性乘算加成依赖属性基线非 0（`SPELL_RESIST`/学派抗性定义基线均为 1.0），接入乘算方案前先核实基线

**验证入口**：安装铁魔法后开启 `ENABLE_DEBUG_LOG`，观察自然生成的死灵法师等施法怪缩放时"法术强度: x -> y"日志；`/an nemesis` 召唤路径回归正常；普通僵尸（非宿敌非施法）无任何法术属性变化

### 功能：入侵敌人涉水自持（冰霜行者路径援助）（2026-10-25）

**背景**：冰霜行者援助此前只在生成瞬间生效——生成在水面上的敌人穿靴 + 预铺霜冰；但陆地生成、移动路径上遇到水的敌人没有任何运行时援助，直接落水。已在 1.20.1-forge 分支实现并实测通过，本条目为同步移植（自 1.20.1-forge，2026-10-25）。

**改动**（`InvasionSystem.java`）：
- 入侵 tick 每 20 tick 扫描一次所有活动入侵的已生成敌人，复用 `enableFrostWalkerOnWater` 配置开关
- 判定"身体在水中或脚下为水"的敌人：脚下无冰霜行者附魔时补穿靴子（已有附魔则跳过，避免覆盖装备缩放结果），随后冻结脚下一层 3x3 水面为霜冰
- 只冻结脚下层、不冻结身体所在格，防止实体被卡入实心方块；深水场景下敌人逐层下沉、逐层冻结，最终站上冰柱恢复 onGround，此后由冰霜行者结冰盘自持扩展
- 霜冰自然融化回水，不永久改变地形
- 1.21.1 适配：附魔检查按 `Holder<Enchantment>` 形态（自 `applyFrostWalkerBoots` 抽取 `getFrostWalkerHolder` 共用解析逻辑，`EnchantmentHelper.getItemEnchantmentLevel` 传 Holder）

### 修复：发光效果配置失效（v1.0.16 回归）+ 宿敌发光无开关（2026-10-25）

**问题现象**：1.0.16 起关闭发光的配置全面失效——修改 `config/` 下的配置文件不生效；主菜单打开配置界面直接崩溃；配置界面内修改关闭发光后宿敌依然发光；服务器上修改同样无效。

**根因分析**：
- 1.0.16 将配置类型由 COMMON 改为 SERVER。NeoForge 21.1 中 SERVER 配置按存档隔离，实际读取 `saves/<世界>/serverconfig/` 目录，`config/` 目录下的同名文件完全不参与加载——修改配置文件自然无效，服务器场景同理
- SERVER 配置仅在服务器（含单机集成服务器）启动时加载，主菜单阶段配置未加载，任何 `ConfigValue.get()` 调用抛出 `IllegalStateException: Cannot get config value before config is loaded`——配置界面第一行取值即崩溃
- 宿敌系统（NemesisSystem）两处发光 buff 为硬编码，不受任何配置项控制，与 `enableGlowingEffect`（入侵怪发光开关）无关，配置界面关闭后宿敌依旧发光

**修复方案**：
- 配置类型回退为 COMMON（`AdaptiveNemesisMod.java`）：配置文件回到 `config/` 目录、游戏启动即加载、全局生效，与 1.20.1 版行为一致
- 联机客户端只读策略保留（`Config.saveToFile()`）：COMMON 配置下联机客户端本地文件虽可写但不影响服务端，为避免误导仍禁止写入并提示只读
- 新增宿敌发光开关 `enableNemesisGlow`（默认 true，`NemesisConfig`）：`NemesisSystem` 两处硬编码发光改为受该配置控制

**迁移提醒**：从 1.0.16~1.0.17 升级后，若曾在单机配置界面或存档 `serverconfig` 中修改过配置，这些值随 COMMON 回退不再被读取，需在 `config/adaptive_nemesis-common.toml` 中重新设置一次。

### 修复：KubeJS 自定义事件从未注册（联动静默失效）

**问题现象**：整合包安装 KubeJS 后，脚本中监听 `adaptive_nemesis.*` 事件（`entity_scale`、`damage_calculation`、`invasion_start` 等 8 个事件）完全无效——事件组从未注册到 KubeJS，脚本加载时无报错（脚本未监听到未注册的事件组只是调用时 console 报 "Unknown event"），模组侧事件触发链静默空转。

**根因分析**（KubeJS 2101.7.2 反编译确认）：
- KubeJS 2101.x 插件发现机制是**唯一**的：逐个模组文件读取 jar 根目录的 `kubejs.plugins.txt`，加载其中列出的类并用 `KubeJSPlugin.class::isAssignableFrom` 过滤——**不使用 Java ServiceLoader**，`META-INF/services/` 注册文件无人读取
- 本模组的 `kubejs.plugins.txt` 指向 `AdaptiveNemesisKubeJSPlugin`（纯桥接标记类，**未实现** `KubeJSPlugin` 接口）→ 被 isAssignableFrom 过滤器静默跳过，插件从未实例化
- 真正实现了接口的 `KubeJSInitializer`（`registerEvents` 注册 `adaptive_nemesis` 事件组）注册在 `META-INF/services/dev.latvian.mods.kubejs.plugin.KubeJSPlugin` 中——该文件 KubeJS 从不读取，等于从未注册
- 两者叠加 → 事件组注册、`init()` 日志、所有 KubeJS 联动静默失效

**修复方案**：
- `kubejs.plugins.txt`: 注册行改为指向 `KubeJSInitializer`（一行核心修复），并补充注释说明注册类必须直接实现 `KubeJSPlugin` 接口
- 移除无效的 `META-INF/services/dev.latvian.mods.kubejs.plugin.KubeJSPlugin`（ServiceLoader 方式在 KubeJS 2101.x 中无效，留着误导后续维护）
- `AdaptiveNemesisKubeJSPlugin.java`: 修正误导性 javadoc（原文声称"通过 ServiceLoader 机制自动发现"），如实标注其为无注册作用的遗留标记类
- `KubeJSInitializer.java`: javadoc 更正为 kubejs.plugins.txt 注册方式
- 新增 `examples/kubejs/server_scripts/adaptive_nemesis_events.js`：全部 8 个自定义事件的正确监听示例（绑定名 `adaptive_nemesis.*`，方法名逐一对照 EventJS 类源码核实，取消事件用 `cancelEvent()`）

**防复发要点**：KubeJS 插件注册的三条铁律——① 类必须直接实现 `KubeJSPlugin`；② 注册只认 `kubejs.plugins.txt`（jar 根目录）；③ `META-INF/services` 不生效。启动日志中出现 "Found plugin source <modid>" 即注册成功。

**联动验证与测试脚本**：
- 反编译 KubeJS 2101.7.2-build.363 逐项核对 API 签名：`EventGroup.of(String)`、`EventGroup.server(String, Supplier)`、`EventGroupRegistry.register(EventGroup)`、`EventHandler.post(KubeEvent)` 与本模组调用完全一致；`ScriptType.SERVER` 实现 `ScriptTypeHolder`，无参 `post()` 链路可用；`KubeJSPlugin.init()` 与 `registerEvents(EventGroupRegistry)` 均为接口 default 方法，`@Override` 正确
- 逐一核对 8 个 EventJS 类与示例脚本的方法调用，全部匹配；8 个事件的触发点均已接入模组核心逻辑（EnemyScalingHandler / TrueDamageHandler / PlayerStrengthEvaluator / WorldStageManager / NemesisMemorySystem / InvasionSystem）
- 新增 `examples/kubejs/server_scripts/adaptive_nemesis_kubejs_test.js`：8 个事件的只读观察型测试脚本，统一日志前缀 `[AN-KubeJS-TEST]`，内置每个事件的触发方式与预期输出说明；可选取消注释验证"修改返回值"链路（入侵波次改写、结算经验追加）
- 测试入口命令：`/an invasion trigger [type] [waves] [difficulty]`（覆盖入侵开始/波次/结束三个事件）、`/an test all`（模组自测）、`/an strength`（强度评估）、`/an nemesis`（召唤宿敌）、`/an memory`（记忆数据）

### 修复：/an invasion trigger 指定类型崩溃 + 省略命名空间无法触发内置入侵（2026-10-24）

**问题现象**：执行 `/an invasion trigger undead_invasion 3 1.5`（省略命名空间写法）时服务端抛 `IllegalArgumentException: TranslatableContents' arguments must be either a Component, Number, Boolean, or a String`，命令执行失败；内置亡灵入侵通过类型参数永远无法触发（不带类型参数的默认触发不受影响）。

**根因分析**：
- 省略命名空间的 `undead_invasion` 被 Brigadier 解析为 `minecraft:undead_invasion`，与内置白名单（`adaptive_nemesis:undead_invasion`）不匹配；回退逻辑虽正确换算为模组命名空间 ID，但最终校验只查数据包注册表 `InvasionDataLoader`——项目未内置任何入侵 JSON，内置亡灵入侵为纯代码定义（`InvasionSystem.InvasionType.UNDEAD`），校验必然失败
- 失败分支的 `Component.translatable("...unknown_type", id)` 将 `ResourceLocation` 对象直接作为翻译参数传入，1.21.1 的 TranslatableContents 仅接受 Component/Number/Boolean/String，抛出异常

**修复方案**（`InvasionCommand.java`）：
- 最终校验豁免内置亡灵入侵：`containsKey(id) || InvasionType.UNDEAD.getId().equals(id)`
- 报错翻译参数改为 `id.toString()`
- 全项目扫描确认无其他 translatable 传入非 Component 参数的同类隐患

**防复发要点**：1.21.1 翻译组件参数只接受 Component/Number/Boolean/String，`ResourceLocation` 等对象必须先 `toString()`；命令 ID 校验需与回退逻辑覆盖同一集合（数据包注册表 + 内置类型），否则回退换算后的合法 ID 会被误拒。

### 修复：入侵敌人生成在水面时未穿戴冰霜行者（2026-10-24）

**问题现象**：入侵敌人生成在水域上方时直接站在水面，未穿戴冰霜行者靴子，随后落水。

**根因分析**（三层叠加）：
- `findSpawnPos` 使用 `MOTION_BLOCKING_NO_LEAVES` 高度图（水方块计入阻挡），水域上方取得的表面高度即水面；向下 15 格找实心方块失败（深水底部更深）后走兜底分支直接返回水面上方位置 → 敌人生成在水面
- `applyMobInfoEnhancements` 仅在 `MobInfo.frostWalker` 为 true 时穿冰霜行者，该标志唯一来源是数据包 JSON 的 `frost_walker` 字段；内置亡灵入侵的硬编码回退配置全部传 false → 默认入侵敌人永不带冰霜行者
- 配置项 `enableFrostWalkerOnWater`（默认 true，注释"是否允许在水面生成敌人时穿戴冰霜行者"）从未被任何运行时逻辑读取，仅在配置界面出现——功能只做了配置壳，执行逻辑未接线

**修复方案**（`InvasionSystem.java`）：
- `spawnOneMob` 在应用数据包自定义效果后新增水面检测：配置允许、实体为 Mob 且 `isWaterSpawn` 判定生成点位于水面上方时，调用既有的 `applyFrostWalkerBoots`（铁靴 + 冰霜行者 I，掉落率 0）
- `isWaterSpawn`：从生成点向下扫描至多 3 格（生成点与水面间最多间隔 1 格空气），遇到 `FluidTags.WATER` 判定为水面生成，遇到实心方块判定为地面生成——不依赖固定偏移，对落点计算方式变化免疫
- 数据包 `frost_walker` 字段的既有逻辑保持不变，水面检测作为无数据包场景的兜底
- 关键修正：`Level.getHeight` 反编译确认返回"最高阻挡方块 Y + 1"（第一个空闲格），水面兜底落点为水面再高 1 格，判定若只查 `below()` 会命中空气格——初版判定因此失效，改为向下扫描后修复
- 游戏实测补充：敌人下落穿过水面后才触发结冰盘（仅作用于实体位置 -1 格），脚下水层不在冻结范围，导致"有靴子但没来得及结冰就落水"；新增 `preFreezeWaterSurface` 在生成点下方水层预铺 3x3 霜冰（会自然融化回水，不永久改地形），敌人落地即处于 onGround 状态，后续移动由冰霜行者结冰盘自持扩展

**防复发要点**：新增配置项时必须同步接线到运行时逻辑（grep 确认配置项的读取方存在），否则配置界面上的开关是"假开关"；高度图选点做兜底分支时，需考虑兜底落点的物理环境（水面/空中）是否对实体可站立。
