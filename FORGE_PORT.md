# Adaptive Nemesis — Forge 1.20.1 移植说明

源版本：NeoForge 1.21.1  
目标版本：Forge 1.20.1（`forge_version=47.4.10` recommended，official mappings，Java 17）  
模组版本：`1.0.12-forge`

---

## 状态总览

| 项 | 状态 |
|---|---|
| Gradle / ForgeGradle 6 脚手架 | ✅ |
| `mods.toml` / `pack.mcmeta` | ✅ |
| Loader API（NeoForge → Forge） | ✅ |
| 核心游戏 API（1.21 → 1.20.1） | ✅ |
| 世界阶段 SavedData 持久化 | ✅ |
| 配置手动保存 | ✅（尽力 + NightConfig 回退） |
| KubeJS 2001.x 插件 + 事件 | ✅（`EventJS` / `kubejs.plugins.txt`） |
| 可选模组 Maven（runtime） | ✅（`transitive = false`） |
| Iron's / Epic Fight 兼容 | ✅ 反射（无硬编译依赖） |
| Apotheosis 兼容 | ✅ NBT/`getTag()` |
| 单元测试 | ✅ `./gradlew test` 通过 |
| 产物 jar | ✅ `build/libs/adaptive_nemesis-1.0.12-forge.jar` |
| 实机 runClient 全量联调 | ✅ 主菜单 + 进世界 + 击杀/阶段 SavedData（compatRuntime=full） |

### 构建

```bash
export JAVA_HOME=/path/to/jdk-17   # 必须 Java 17（JDK 21+ 会炸 FG6）
./gradlew build
```

产物：`build/libs/adaptive_nemesis-1.0.12-forge.jar`

---

## API 对照（摘要）

| NeoForge 1.21.1 / MC 1.21 | Forge 1.20.1 |
|---|---|
| `@Mod(IEventBus, ModContainer)` | `AdaptiveNemesisMod(FMLJavaModLoadingContext)`（47.4+ 推荐；勿用已弃用的 `.get()`） |
| `NeoForge.EVENT_BUS` | `MinecraftForge.EVENT_BUS` |
| `ModConfigSpec` | `ForgeConfigSpec` |
| `LivingIncomingDamageEvent` | `LivingHurtEvent` |
| `FinalizeSpawnEvent` | `MobSpawnEvent.FinalizeSpawn` |
| `*TickEvent.Post` | `TickEvent.*` + `Phase.END` |
| Payload 网络 | `SimpleChannel` 占位 |
| `IConfigScreenFactory` | `ConfigScreenHandler.ConfigScreenFactory`（经 `ModContainer.registerExtensionPoint`） |
| `ResourceLocation.fromNamespaceAndPath` | 同名（Forge 47.4 从 1.21 回传；`new ResourceLocation` 已 forRemoval） |
| Enchantment `Holder` | `Enchantment` + `ForgeRegistries.ENCHANTMENTS` |
| `DataComponents.CUSTOM_DATA` | `ItemStack.getTag()` |
| `reloadableRegistries().getLootTable` | `getLootData().getLootTable(RL)` |
| KubeJS `KubeEvent` / `EventGroupRegistry` | `EventJS` + `EventGroup.register()` + `kubejs.plugins.txt` |

---

## 可选依赖（Maven）

`libs/` 已删除。坐标在 `gradle.properties`，`build.gradle` 使用 `fg.deobf` + **`transitive = false`**（避免 Rhino 拉 `fabric-loader` 等坏传递依赖）。

| 模组 | 坐标要点 |
|---|---|
| KubeJS | `dev.latvian.mods:kubejs-forge:2001.6.5-build.26`（compileOnly + runtimeOnly） |
| Rhino | 编译：`rhino`；**运行：`rhino-forge`**（同版本 `2001.2.3-build.10`，含 `modId=rhino`） |
| Architectury | `dev.architectury:architectury-forge:9.1.13` |
| Placebo / Apotheosis / ApothicAttributes | `dev.shadowsoffire:*:1.20.1-…` |
| Iron's Spells | `io.redspace:irons_spellbooks:1.20.1-3.16.2`（runtimeOnly） |
| **Iron's Lib** | CurseMaven `curse.maven:irons-lib-1492763:8364892`（3.16+ **强制**；Modrinth Maven 无此包） |
| PlayerAnimator | `dev.kosmx.player-anim:player-animation-lib-forge:1.0.2-rc1+1.20`（Iron's **强制**前置） |
| Curios / GeckoLib | theillusivec4 / geckolib maven |
| Epic Fight | `maven.modrinth:epic-fight:KEBfkBat`（需 Forge ≥ 47.4.0） |

仓库：Forge、Fabric（仅解 POM）、Latvian、Architectury、Shadows、Iron's、Curios、GeckoLib、**KosmX**、Modrinth、CurseMaven。

**runClient JVM（`build.gradle` runs.configureEach）：**

```properties
mixin.env.remapRefMap=true
mixin.env.refMapRemappingFile=${projectDir}/build/createSrgToMcp/output.srg
```

官方 mappings 下，第三方 `fg.deobf` jar 的 mixin refmap 常仍是 SRG 名；不 remap 会在 Architectury / Iron's 等注入阶段炸客户端。

**compatRuntime 开关（`-PcompatRuntime=…`）：**

| 值 | 内容 |
|---|---|
| `full`（默认） | KubeJS + Apotheosis + Iron's 全栈 + Epic Fight |
| `stable` | 仅 KubeJS + Apotheosis（去掉 Iron's / Epic Fight） |
| `irons` | KubeJS + Apotheosis + Iron's（无 Epic Fight） |
| `epicfight` | KubeJS + Apotheosis + Epic Fight（无 Iron's） |

**兼容策略：**

- Iron's / Epic Fight：编译期无硬依赖，**运行时反射**解析属性/能力；字段名变化时会静默跳过。
- Apotheosis：读 NBT `affix_data` / `apotheosis` 等；1.20.1 键名若不同，评分可能偏低。
- KubeJS：完整 Java 插件 + `kubejs.plugins.txt` 发现；脚本事件组名 `adaptive_nemesis`。
- **Rhino 运行时必须用 `rhino-forge`**（含 `mods.toml` / `modId=rhino`）；compileOnly 可用 common `rhino`。

---

## 本轮补全内容

1. **WorldStageSavedData** — 标准 1.20.1 `SavedData`，挂在 overworld `DimensionDataStorage`，键 `adaptive_nemesis_world_stage`。
2. **Config.saveToFile** — `ModConfig.save()` → `configData.save()`（NightConfig）回退链。
3. **KubeJS 2001.x** — `*EventJS extends EventJS`；`KubeJSInitializer extends KubeJSPlugin`；`registerEvents()` 内 `ADAPTIVE_NEMESIS_EVENTS.register()`；`EventResult` 取消检测；资源 `kubejs.plugins.txt`。
4. **附魔表懒加载** — 避免纯 JVM 单测加载 `Enchantments` 静态初始化失败。
5. **Maven 可选依赖** — 可解析坐标 + `transitive = false`。

---

## 已知限制 / 建议本地再验

1. **runClient**：已在本环境验证 **主菜单 + 创建/进入世界 + 近战击杀记录 + 世界阶段 SavedData**（`compatRuntime=full`，含 Iron's/Epic Fight/Apotheosis/KubeJS）。配置屏与重启持久化仍建议你在本机再点一次。
2. **反射兼容**：Iron's / Epic Fight 1.20.1 字段或包名若与探测列表不符，看日志 `*Compat.applyMobBuffs failed` 再补候选类名。
3. **KubeJS 脚本**：需安装 KubeJS 2001.x + **rhino-forge** + Architectury；示例见 `examples/kubejs/`、`kubejs_example/`。
4. **网络**：仍无业务数据包（与原版空占位一致）。
5. **README 徽章**：仍可能写 1.21/Neo；功能以本文件与 `mods.toml` 为准。
6. **非致命警告**：Iron's 缺部分音效/模型资源；Epic Fight waveycapes develop-only 层警告 — 不影响进游戏。

### 验证清单

1. `JAVA_HOME=…17 ./gradlew build` 全绿  
2. `JAVA_HOME=…17 ./gradlew runClient`（默认 full 兼容栈）进主菜单 / 世界  
3. 纯玩法：敌人缩放、真实伤害、Boss 限伤、入侵、宿敌、指令  
4. 重启世界后世界阶段仍在  
5. 配置屏改值后 `config/adaptive_nemesis-common.toml` 落盘  
6. 装 KubeJS 后插件加载、脚本可监听 `adaptive_nemesis.*`  
7. 日志可见 Iron's / Epic Fight / Apotheosis 加载；可选 `-PcompatRuntime=stable` 精简栈  

---

## 工程要点

- Wrapper：Gradle **8.8** + FG **6.x**
- 无 `libs/` 本地 jar
- 无 Neo `moddev` / `neoforge.mods.toml` 模板流
- `Config.java` 包路径：`com.adaptive_nemesis.adaptive_nemesismod.Config`

*移植完成后请按验证清单做一次实机 smoke test。*

---

## 并行审计后已修复 / 仍存项（2026-08-02）

### 已修复
- KubeJS 可取消事件补充 `cancel()` / `isCancelled()`（与示例脚本 `event.cancel()` 对齐，并同步 `EventResult`）
- `META-INF/services/dev.latvian.mods.kubejs.KubeJSPlugin` + `kubejs.plugins.txt` 双通道发现
- L2Hostility tank 修饰符匹配放宽（`getName()` 子串，而非仅 RL 全等）
- `ModCompatManager.applyEpicFightMobBuffs` / `applyIronsSpellsMobBuffs` 空安全封装
- **AdaptiveFloatSystem 未注册事件总线** → 已在 `AdaptiveNemesisMod.registerEventHandlers` 注册（此前浮动难度整系统静默失效）
- CI/注释中的 NeoForge 字样：仍可能残留于个别 Javadoc（不影响运行）

### 仍建议实机关注（非编译阻断）
| 项 | 说明 |
|---|---|
| Iron's / Epic Fight 反射字段 | 1.20.1 类名/字段若与候选不符则属性缩放 no-op，需看 DEBUG 日志 |
| Apotheosis 7.x NBT 键 | `affix_data`/`apotheosis` 启发式，实机神化装评分可能偏低 |
| KubeJS `player_strength_evaluation` | 仅支持改 finalStrength，不支持 cancel（与其它事件不一致，可接受） |
| 网络 SimpleChannel | 仍无业务包 |
| CI JDK | 若 `.github/workflows` 仍用 JDK 21，需改为 17 |
| README 徽章 | 可能仍写旧版本号 |
| `textture/` 目录拼写 | 资源目录 typo，未引用则无影响 |
| runClient 全量 smoke | ✅ 已过（主菜单+世界+击杀）；配置屏/重启阶段仍建议手点 |

