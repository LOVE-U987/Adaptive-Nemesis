package com.adaptive_nemesis.adaptive_nemesismod.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;

/**
 * 神话 (Apotheosis) 兼容处理器
 *
 * 提供与神话模组的交互功能：
 * - 获取玩家装备上的神话词条
 * - 评估装备品质和等级
 * - 计算神话加成强度
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class ApotheosisCompat {

    /**
     * 默认构造函数
     */
    public ApotheosisCompat() {}

    /**
     * 获取玩家的装备强度评估值（基于神话词条）
     *
     * @param player 目标玩家
     * @return 神话词条强度值
     */
    public double getPlayerGearStrength(ServerPlayer player) {
        double strength = 0.0;

        try {
            // 检查主手武器
            strength += evaluateApotheosisItem(player.getMainHandItem());

            // 检查副手物品
            strength += evaluateApotheosisItem(player.getOffhandItem());

            // 检查护甲
            for (ItemStack armor : player.getArmorSlots()) {
                strength += evaluateApotheosisItem(armor);
            }

        } catch (Exception e) {
            return 0.0;
        }

        return strength;
    }

    /**
     * 评估单个物品的神话词条强度
     *
     * @param stack 物品堆叠
     * @return 神话词条强度贡献值
     */
    private double evaluateApotheosisItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }

        double value = 0.0;

        try {
            // 在1.21中使用components获取NBT数据
            CompoundTag tag = getItemTag(stack);
            if (tag != null) {
                // 检查Apotheosis特有的NBT标签
                // 神话装备通常有以下特征：

                // 1. 检查是否有apotheosis相关NBT
                if (tag.contains("apotheosis")) {
                    CompoundTag apothTag = tag.getCompound("apotheosis");

                    // 检查装备稀有度/品质
                    if (apothTag.contains("rarity")) {
                        String rarity = apothTag.getString("rarity");
                        value += getRarityValue(rarity);
                    }

                    // 检查装备等级
                    if (apothTag.contains("level")) {
                        int level = apothTag.getInt("level");
                        value += level * 0.5;
                    }
                }

                // 2. 检查affix标签（神话词条）
                if (tag.contains("affix_data")) {
                    CompoundTag affixTag = tag.getCompound("affix_data");

                    // 每个affix增加一定强度
                    int affixCount = affixTag.getAllKeys().size();
                    value += affixCount * 3.0;

                    // 检查特定强力affix
                    for (String key : affixTag.getAllKeys()) {
                        if (key.contains("legendary") || key.contains("mythic")) {
                            value += 5.0;
                        }
                    }
                }

                // 3. 检查gem socket（宝石镶嵌）
                if (tag.contains("gems")) {
                    CompoundTag gemsTag = tag.getCompound("gems");
                    int gemCount = gemsTag.getAllKeys().size();
                    value += gemCount * 2.0;
                }
            }

            // 4. 检查附魔等级（神话模组增强了附魔系统）
            if (stack.isEnchanted()) {
                int totalEnchantLevel = stack.getEnchantments().size();
                value += totalEnchantLevel * 0.3;
            }

        } catch (Exception e) {
            // 如果解析失败，返回已计算的值
        }

        return value;
    }

    /**
     * 获取物品的NBT标签（兼容1.21新API）
     *
     * @param stack 物品堆叠
     * @return NBT标签，如果没有则返回null
     */
    private CompoundTag getItemTag(ItemStack stack) {
        try {
            // 1.21使用DataComponents获取自定义数据
            var customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                return customData.copyTag();
            }
        } catch (Exception e) {
            // 如果获取失败，尝试其他方式
        }
        return null;
    }

    /**
     * 根据稀有度名称获取强度值
     *
     * @param rarity 稀有度名称
     * @return 对应的强度值
     */
    private double getRarityValue(String rarity) {
        return switch (rarity.toLowerCase()) {
            case "common" -> 1.0;
            case "uncommon" -> 3.0;
            case "rare" -> 6.0;
            case "epic" -> 10.0;
            case "mythic" -> 15.0;
            case "ancient" -> 20.0;
            default -> 2.0;
        };
    }

    /**
     * 获取物品上的神话词条数量
     *
     * @param stack 物品堆叠
     * @return 词条数量
     */
    public int getAffixCount(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        try {
            CompoundTag tag = getItemTag(stack);
            if (tag != null && tag.contains("affix_data")) {
                return tag.getCompound("affix_data").getAllKeys().size();
            }
        } catch (Exception e) {
            return 0;
        }

        return 0;
    }

    /**
     * 获取物品的神话等级
     *
     * @param stack 物品堆叠
     * @return 等级值
     */
    public int getItemLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        try {
            CompoundTag tag = getItemTag(stack);
            if (tag != null && tag.contains("apotheosis")) {
                CompoundTag apothTag = tag.getCompound("apotheosis");
                if (apothTag.contains("level")) {
                    return apothTag.getInt("level");
                }
            }
        } catch (Exception e) {
            return 0;
        }

        return 0;
    }
}
