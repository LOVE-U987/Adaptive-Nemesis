# Adaptive Nemesis Changelog

---

## v1.0.18 (2026-10-18)

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
