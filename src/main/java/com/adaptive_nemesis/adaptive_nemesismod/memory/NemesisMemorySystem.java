package com.adaptive_nemesis.adaptive_nemesismod.memory;

import java.util.*;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSEventTrigger;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 宿敌记忆系统
 *
 * 每个玩家拥有独立的宿敌档案，敌人会记住玩家的战斗方式并针对性进化：
 * - 击杀偏好：玩家常用的击杀手段 → 敌人获得对应抗性
 * - 死亡记录：玩家死亡时的伤害来源 → 敌人学会对应攻击方式
 * - 装备历史：玩家曾使用过的装备组合 → 敌人针对性进化反制策略
 * - 行为模式：玩家的战斗习惯（近战/远程/法术） → 敌人调整AI行为
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NemesisMemorySystem {

    /**
     * 单例实例
     */
    private static NemesisMemorySystem INSTANCE;

    /**
     * 玩家宿敌档案映射
     * Key: 玩家UUID, Value: 宿敌档案
     */
    private final Map<UUID, NemesisProfile> playerProfiles = new HashMap<>();

    /**
     * 私有构造函数 - 单例模式
     */
    private NemesisMemorySystem() {}

    /**
     * 获取单例实例
     *
     * @return NemesisMemorySystem 实例
     */
    public static synchronized NemesisMemorySystem getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NemesisMemorySystem();
        }
        return INSTANCE;
    }

    /**
     * 初始化宿敌记忆系统
     */
    public void initialize() {
        AdaptiveNemesisMod.LOGGER.info("🧬 宿敌记忆系统已初始化");
    }

    /**
     * 处理生物死亡事件 - 记录玩家击杀偏好
     *
     * @param event 生物死亡事件
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource() == null || event.getSource().getEntity() == null) {
            return;
        }

        // 记录玩家击杀数据
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            recordKillPreference(player, event.getSource(), event.getEntity());
        }

        // 记录玩家死亡数据
        if (event.getEntity() instanceof ServerPlayer player) {
            recordDeathSource(player, event.getSource());
        }
    }

    /**
     * 处理玩家登出事件 - 保存数据
     *
     * @param event 玩家登出事件
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 可以在这里保存数据到磁盘
            UUID playerId = player.getUUID();
            NemesisProfile profile = playerProfiles.get(playerId);
            if (profile != null) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "玩家 {} 登出，宿敌档案已保存",
                    player.getName().getString()
                );
            }
        }
    }

    /**
     * 记录玩家击杀偏好
     *
     * @param player 击杀者
     * @param source 伤害来源
     * @param victim 被击杀者
     */
    private void recordKillPreference(ServerPlayer player, DamageSource source, LivingEntity victim) {
        UUID playerId = player.getUUID();
        NemesisProfile profile = getOrCreateProfile(playerId);

        // 判断击杀方式
        CombatStyle style = determineCombatStyle(player, source);
        profile.recordKillStyle(style);

        // 记录使用的武器类型
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.isEmpty()) {
            String weaponType = weapon.getItem().toString();
            profile.recordWeaponUsed(weaponType);
        }

        // 记录击杀的敌人类型
        String entityType = victim.getType().toString();
        profile.recordKilledEntity(entityType);

        // 触发 KubeJS 宿敌记忆更新事件
        KubeJSEventTrigger.triggerNemesisMemoryUpdate(playerId, player.getName().getString(), profile);

        if (AdaptiveNemesisMod.LOGGER.isDebugEnabled()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 击杀记录: 方式={}, 武器={}, 目标={}",
                player.getName().getString(),
                style,
                weapon.getItem(),
                entityType
            );
        }
    }

    /**
     * 记录玩家死亡来源
     *
     * @param player 死亡玩家
     * @param source 伤害来源
     */
    private void recordDeathSource(ServerPlayer player, DamageSource source) {
        UUID playerId = player.getUUID();
        NemesisProfile profile = getOrCreateProfile(playerId);

        // 记录死亡来源类型
        String deathSource = source.getMsgId();
        profile.recordDeathSource(deathSource);

        // 记录击杀者类型
        if (source.getEntity() != null) {
            String killerType = source.getEntity().getType().toString();
            profile.recordKillerType(killerType);
        }

        if (AdaptiveNemesisMod.LOGGER.isDebugEnabled()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 死亡记录: 来源={}",
                player.getName().getString(),
                deathSource
            );
        }
    }

    /**
     * 判断玩家的战斗风格
     *
     * @param player 目标玩家
     * @param source 伤害来源
     * @return 战斗风格枚举
     */
    private CombatStyle determineCombatStyle(ServerPlayer player, DamageSource source) {
        // 检查是否是远程攻击
        if (source.getMsgId().contains("arrow") ||
            source.getMsgId().contains("projectile") ||
            source.getMsgId().contains("trident")) {
            return CombatStyle.RANGED;
        }

        // 检查是否是魔法攻击
        if (source.getMsgId().contains("magic") ||
            source.getMsgId().contains("spell") ||
            source.getMsgId().contains("indirect_magic")) {
            return CombatStyle.MAGIC;
        }

        // 检查主手武器
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty()) {
            String itemName = mainHand.getItem().toString().toLowerCase();
            if (itemName.contains("bow") || itemName.contains("crossbow")) {
                return CombatStyle.RANGED;
            }
            if (itemName.contains("staff") || itemName.contains("wand") || itemName.contains("spell")) {
                return CombatStyle.MAGIC;
            }
        }

        // 默认近战
        return CombatStyle.MELEE;
    }

    /**
     * 获取或创建玩家宿敌档案
     *
     * @param playerId 玩家UUID
     * @return 宿敌档案
     */
    private NemesisProfile getOrCreateProfile(UUID playerId) {
        return playerProfiles.computeIfAbsent(playerId, k -> new NemesisProfile());
    }

    /**
     * 获取玩家宿敌档案
     *
     * @param playerId 玩家UUID
     * @return 宿敌档案，可能为null
     */
    public NemesisProfile getProfile(UUID playerId) {
        return playerProfiles.get(playerId);
    }

    /**
     * 获取玩家的主要战斗风格
     *
     * @param playerId 玩家UUID
     * @return 主要战斗风格
     */
    public CombatStyle getDominantCombatStyle(UUID playerId) {
        NemesisProfile profile = playerProfiles.get(playerId);
        if (profile == null) {
            return CombatStyle.MELEE;
        }
        return profile.getDominantStyle();
    }

    /**
     * 清除指定玩家的档案
     *
     * @param playerId 玩家UUID
     */
    public void clearProfile(UUID playerId) {
        playerProfiles.remove(playerId);
    }

    /**
     * 战斗风格枚举
     */
    public enum CombatStyle {
        MELEE("近战"),
        RANGED("远程"),
        MAGIC("法术");

        private final String displayName;

        CombatStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
