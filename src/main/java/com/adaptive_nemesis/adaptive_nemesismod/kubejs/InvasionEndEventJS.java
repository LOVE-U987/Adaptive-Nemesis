package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionRewardData;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/**
 * 入侵结束事件
 *
 * 当入侵事件结束时触发（无论胜利或失败）。
 * KubeJS 脚本可以监听此事件来：
 * - 发放自定义奖励
 * - 记录玩家战绩
 * - 根据胜负触发不同效果
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class InvasionEndEventJS implements KubeEvent {

    /**
     * 触发入侵的玩家
     */
    private final ServerPlayer player;

    /**
     * 入侵类型
     */
    private final InvasionSystem.InvasionType type;

    /**
     * 是否胜利
     */
    private final boolean victory;

    /**
     * 总波次数
     */
    private final int totalWaves;

    /**
     * 难度倍率
     */
    private final double difficultyMultiplier;

    /**
     * 完成波次数（失败时为已击败波次数）
     */
    private final int wavesCompleted;

    /**
     * 入侵奖励配置（可被 KubeJS 修改）
     */
    private final InvasionRewardData rewards;

    /**
     * 构造函数
     *
     * @param player 触发玩家
     * @param type 入侵类型
     * @param victory 是否胜利
     * @param totalWaves 总波次数
     * @param difficultyMultiplier 难度倍率
     * @param wavesCompleted 完成波次数
     * @param rewards 奖励配置
     */
    public InvasionEndEventJS(ServerPlayer player, InvasionSystem.InvasionType type, boolean victory,
                              int totalWaves, double difficultyMultiplier, int wavesCompleted,
                              InvasionRewardData rewards) {
        this.player = player;
        this.type = type;
        this.victory = victory;
        this.totalWaves = Math.max(1, totalWaves);
        this.difficultyMultiplier = Math.max(1.0, difficultyMultiplier);
        this.wavesCompleted = Math.max(0, wavesCompleted);
        this.rewards = rewards != null ? rewards : new InvasionRewardData();
    }

    /**
     * 获取触发玩家
     *
     * @return 玩家对象
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * 获取玩家名称
     *
     * @return 玩家名称
     */
    public String getPlayerName() {
        return player.getName().getString();
    }

    /**
     * 获取入侵类型
     *
     * @return 入侵类型
     */
    public InvasionSystem.InvasionType getType() {
        return type;
    }

    /**
     * 是否胜利
     *
     * @return 胜利返回 true
     */
    public boolean isVictory() {
        return victory;
    }

    /**
     * 是否失败
     *
     * @return 失败返回 true
     */
    public boolean isDefeat() {
        return !victory;
    }

    /**
     * 获取总波次数
     *
     * @return 总波次数
     */
    public int getTotalWaves() {
        return totalWaves;
    }

    /**
     * 获取难度倍率
     *
     * @return 难度倍率
     */
    public double getDifficultyMultiplier() {
        return difficultyMultiplier;
    }

    /**
     * 获取完成波次数
     *
     * @return 完成波次数
     */
    public int getWavesCompleted() {
        return wavesCompleted;
    }

    /**
     * 获取奖励配置
     *
     * @return 奖励配置
     */
    public InvasionRewardData getRewards() {
        return rewards;
    }

    /**
     * 添加战利品表
     *
     * @param lootTable 战利品表资源位置字符串
     */
    public void addLoot(String lootTable) {
        ResourceLocation id = ResourceLocation.tryParse(lootTable);
        if (id != null) {
            rewards.addLootTable(id);
        }
    }

    /**
     * 添加经验值
     *
     * @param amount 经验值
     */
    public void addExperience(int amount) {
        rewards.setExperience(rewards.getExperience() + amount);
    }

    /**
     * 设置经验值
     *
     * @param amount 经验值
     */
    public void setExperience(int amount) {
        rewards.setExperience(amount);
    }

    /**
     * 添加药水效果
     *
     * @param effect 药水效果
     * @param durationSeconds 持续时间（秒）
     * @param amplifier 等级
     */
    public void addEffect(MobEffect effect, int durationSeconds, int amplifier) {
        rewards.addEffect(effect, durationSeconds * 20, amplifier);
    }

    /**
     * 添加额外物品奖励
     *
     * @param stack 物品堆叠
     */
    public void addExtraReward(ItemStack stack) {
        rewards.addExtraItem(stack);
    }

    /**
     * 设置数据包奖励是否启用
     *
     * @param enabled 是否启用
     */
    public void setRewardsEnabled(boolean enabled) {
        rewards.setEnabled(enabled);
    }

    /**
     * 是否启用了数据包奖励
     *
     * @return 启用返回 true
     */
    public boolean isRewardsEnabled() {
        return rewards.isEnabled();
    }
}
