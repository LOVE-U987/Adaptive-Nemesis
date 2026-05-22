# Adaptive Nemesis 更新日志

## \[v1.0.3] - 2026-05-23

### ✨ 新功能

#### 🎯 怪物装备系统全面升级

- **装备生成概率大幅提升**：基础概率 5% → 15%，每单位难度增量 3% → 10%
- **装备品质阈值降低**：下界合金从 ×10 倍率降至 ×6 倍率，钻石从 ×6 降至 ×4
- **品质跳级机制**：15% 概率获得高一档品质的装备，运气好低难度也能见钻石怪
- **武器多样化**：Tier 0 新增金剑/金斧选择，告别清一色石剑

#### 🤝 跨模组兼容系统

- **附魔全兼容**：`tryApplyModCompatibleEnchantments()` 自动扫描附魔注册表，应用其他模组添加的兼容附魔
- **装备全兼容**：`tryGetModEquipment()` 通过 `ItemTags` 标签系统发现并穿戴其他模组的武器和盔甲
- **副手智能选择**：优先寻找模组盾牌（`#minecraft:shields`），找不到再退而求其次找单手剑
- **智能识别**：通过命名空间自动区分原版（`minecraft:`）和模组物品

#### 📦 附魔池扩充

- **武器附魔**：新增 耐久、效率（原 7 种 → 9 种）
- **防具附魔**：新增 水下呼吸、水下速掘、轻盈、深海探索者（原 6 种 → 10 种）
- **弓附魔**：新增 耐久（原 4 种 → 5 种）

#### ⚙️ 全新配置项

新增 **6 个可配置参数** + 2 个配置屏幕分类：

| 配置分类 | 配置项                            |  默认值 | 说明       |
| ---- | ------------------------------ | :--: | :------- |
| 装备生成 | `equipmentBaseChance`          | 0.15 | 装备生成基础概率 |
| 装备生成 | `equipmentChancePerDifficulty` | 0.10 | 每倍率概率增量  |
| 装备生成 | `equipmentTierUpgradeChance`   | 0.15 | 品质跳级概率   |
| 装备生成 | `equipmentModCompatChance`     | 0.30 | 模组装备替换概率 |
| 史诗战斗 | `weightMinBonus`               | 15.0 | 重量最小加值   |
| 史诗战斗 | `weightPerMultiplier`          | 20.0 | 每倍率重量增量  |

- 所有新配置支持游戏内可视化配置屏幕调节

### 🐛 Bug 修复

#### 🦴 史诗战斗击退修复（EpicFightCompat）

- **重量公式**：`new = old + (effectiveMultiplier - 1) × 20`，保底 `old + 15`
- **全面修复 3 处漏网公式**：最大连击、重量、耐力恢复公式改用 `effectiveMultiplier` 替代 `multiplier`
- **所有 8 处属性公式增加** **`Math.max()`** **保护**：防止随机因子导致属性降为负数
- 修复前：骷髅重量 5.47（裸装水平）→ 修复后：骷髅重量 21.15（铁套级抗性）

#### 🛡️ Iron's Spells 属性保护（IronsSpellsCompat）

- **23 处公式全部添加** **`Math.max()`** **保底**：法术强度、法力值、法力恢复等属性不再因低随机因子而变负

#### 🎯 装备系统修复

- **修复** **`createEquipmentForSlot`** **空壳问题**：怪物不再裸奔
- **修复装备生成后不附魔问题**：移除 `continue` 语句，新建装备也能获得附魔
- **延迟加载修复**：装备数据改为 instance-level lazy init，避免单元测试触发 Minecraft 类加载

#### ⚙️ 配置文件修复

- **修复配置重置 Bug**：`saveToFile()` 改为 `MOD_CONFIG.getLoadedConfig().save()`

### 🧪 测试

- 新增 `EnchantmentScalingHandlerTest`：34 个单元测试用例
- 覆盖附魔概率、附魔等级、装备生成概率的边界情况
- JUnit 5 + ParameterizedTest + `@CsvSource`

### 🏗️ 重构 & 优化

- **硬编码 → 配置化**：重量公式、装备概率、品质跳级概率全面迁移至 Config
- **兼容层分离**：`tryGetModEquipment` / `isVanillaItem` / `tryApplyModCompatibleEnchantments` 独立方法
- **附魔冲突智能检测**：`tryApplyModCompatibleEnchantments` 增加 `Set<Enchantment>` 去重 + `candidates.remove(index)` 防重复选中，try-catch 仅作为兜底
- **日志增强**：关键缩放节点添加详细 DEBUG 日志，方便排查属性计算问题
- **禁用 DEBUG 刷屏**：EpicFightCompat 和 IronsSpellsCompat 日志调整为条件输出

<br />

