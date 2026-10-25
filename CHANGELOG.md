## v1.0.18 (2026-10-02)

### 修复：KubeJS 命令示例脚本 adaptive_nemesis_invasion.js 出厂语法错误（2026-10-25）

**问题现象**：该示例脚本自分支创建起从未成功加载，每次 `/reload` 报 `missing ) after argument list`。

**根因**：脚本建立在从未实现的 API 上——调用的 `adaptiveNemesis` 全局绑定从未被模组注册（实际只有 `adaptive_nemesis.*` 事件组），`ServerEvents.commandRegistry` 使用了虚构的 `event.register('名称', command => {...})` 签名（KubeJS 6 真实签名为 `event.register(Commands.literal(...))` Brigadier 风格），行 60 报错点只是错误嵌套的解析落点。

**修复**：整体重写为 KubeJS 6 正确语法——`Java.loadClass` 显式加载参数类型、`Commands.literal/argument` 注册、`performPrefixedCommand` 转发模组原生命令 `/an invasion trigger`；提供 `/trigger_invasion`（默认参数）与 `/trigger_invasion_custom <波次> <难度倍率>` 两个示例命令（权限等级 2）。

### 功能：入侵敌人涉水自持（冰霜行者路径援助）（2026-10-25）

**背景**：冰霜行者援助此前只在生成瞬间生效——生成在水面上的敌人穿靴 + 预铺霜冰；但陆地生成、移动路径上遇到水的敌人没有任何运行时援助，直接落水（1.21.1 与 1.20.1 均存在该缺口）。

**改动**（`InvasionSystem.java`）：
- 入侵 tick 每 20 tick 扫描一次所有活动入侵的已生成敌人，复用 `enableFrostWalkerOnWater` 配置开关
- 判定"身体在水中或脚下为水"的敌人：脚下无冰霜行者附魔时补穿靴子（已有附魔则跳过，避免覆盖装备缩放结果），随后冻结脚下一层 3x3 水面为霜冰
- 只冻结脚下层、不冻结身体所在格，防止实体被卡入实心方块；深水场景下敌人逐层下沉、逐层冻结，最终站上冰柱恢复 onGround，此后由冰霜行者结冰盘自持扩展
- 霜冰自然融化回水，不永久改变地形

### 功能：宿敌发光新增配置开关（2026-10-25）

**背景**：宿敌生成时的发光 buff 为硬编码，不受任何配置项控制（与入侵怪的 `enableGlowingEffect` 无关），无法关闭。

**改动**：
- `NemesisConfig.java`: 新增 `enableNemesisGlow` 配置项（默认 true，注释"是否让宿敌自带发光buff"），与 1.21.1 版保持一致
- `Config.java`: 新增兼容代理字段 `ENABLE_NEMESIS_GLOW`
- `NemesisSystem.java`: `convertToNemesis` 与 `convertToNemesisManual` 两处硬编码发光改为受该配置控制，关闭后宿敌生成时不再附加发光效果
