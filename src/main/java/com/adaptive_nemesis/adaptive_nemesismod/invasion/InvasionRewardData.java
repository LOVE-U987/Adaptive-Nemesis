package com.adaptive_nemesis.adaptive_nemesismod.invasion;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 入侵奖励数据类
 *
 * 保存从数据包解析出的入侵奖励信息，
 * 包括战利品表、经验值、药水效果和额外物品。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class InvasionRewardData {

    /**
     * 战利品表列表
     */
    private final List<ResourceLocation> lootTables;

    /**
     * 经验值
     */
    private int experience;

    /**
     * 药水效果列表
     */
    private final List<MobEffectInstance> effects;

    /**
     * 额外物品列表
     */
    private final List<ItemStack> extraItems;

    /**
     * 是否启用该奖励
     */
    private boolean enabled;

    /**
     * 默认空奖励
     */
    public InvasionRewardData() {
        this(new ArrayList<>(), 0, new ArrayList<>(), new ArrayList<>(), true);
    }

    /**
     * 完整构造函数
     *
     * @param lootTables 战利品表列表
     * @param experience 经验值
     * @param effects 药水效果列表
     * @param extraItems 额外物品列表
     * @param enabled 是否启用
     */
    public InvasionRewardData(List<ResourceLocation> lootTables, int experience,
                              List<MobEffectInstance> effects, List<ItemStack> extraItems,
                              boolean enabled) {
        this.lootTables = lootTables != null ? new ArrayList<>(lootTables) : new ArrayList<>();
        this.experience = Math.max(0, experience);
        this.effects = effects != null ? new ArrayList<>(effects) : new ArrayList<>();
        this.extraItems = extraItems != null ? new ArrayList<>(extraItems) : new ArrayList<>();
        this.enabled = enabled;
    }

    /**
     * 获取战利品表列表
     *
     * @return 不可修改的战利品表列表
     */
    public List<ResourceLocation> getLootTables() {
        return Collections.unmodifiableList(lootTables);
    }

    /**
     * 添加战利品表
     *
     * @param lootTable 战利品表资源位置
     */
    public void addLootTable(ResourceLocation lootTable) {
        this.lootTables.add(lootTable);
    }

    /**
     * 获取经验值
     *
     * @return 经验值
     */
    public int getExperience() {
        return experience;
    }

    /**
     * 设置经验值
     *
     * @param experience 经验值
     */
    public void setExperience(int experience) {
        this.experience = Math.max(0, experience);
    }

    /**
     * 获取药水效果列表
     *
     * @return 不可修改的药水效果列表
     */
    public List<MobEffectInstance> getEffects() {
        return Collections.unmodifiableList(effects);
    }

    /**
     * 添加药水效果
     *
     * @param effect 药水效果
     * @param duration 持续时间（刻）
     * @param amplifier 等级
     */
    public void addEffect(MobEffect effect, int duration, int amplifier) {
        this.effects.add(new MobEffectInstance(effect, duration, amplifier));
    }

    /**
     * 获取额外物品列表
     *
     * @return 额外物品列表的副本
     */
    public List<ItemStack> getExtraItems() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : extraItems) {
            copy.add(stack.copy());
        }
        return copy;
    }

    /**
     * 添加额外物品
     *
     * @param stack 物品堆叠
     */
    public void addExtraItem(ItemStack stack) {
        this.extraItems.add(stack.copy());
    }

    /**
     * 是否启用
     *
     * @return 启用返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 检查是否有任何奖励内容
     *
     * @return 有奖励返回 true
     */
    public boolean hasRewards() {
        return !lootTables.isEmpty() || experience > 0 || !effects.isEmpty() || !extraItems.isEmpty();
    }

    /**
     * 解析药水效果字符串
     *
     * @param effectId 效果 ID
     * @param duration 持续时间（秒）
     * @param amplifier 等级
     * @return 药水效果实例，解析失败返回 null
     */
    public static MobEffectInstance parseEffect(String effectId, int duration, int amplifier) {
        ResourceLocation id = ResourceLocation.tryParse(effectId);
        if (id == null) {
            return null;
        }
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
        if (effect == null) {
            return null;
        }
        return new MobEffectInstance(effect, duration * 20, amplifier);
    }
}
