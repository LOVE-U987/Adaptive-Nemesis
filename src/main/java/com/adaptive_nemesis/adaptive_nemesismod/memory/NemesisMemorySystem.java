package com.adaptive_nemesis.adaptive_nemesismod.memory;

import java.util.*;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSEventTrigger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * 宿敌记忆系统
 *
 * 每个玩家拥有独立的宿敌档案，敌人会记住玩家的战斗方式并针对性进化：
 * - 击杀偏好：玩家常用的击杀手段 → 敌人获得对应抗性
 * - 死亡记录：玩家死亡时的伤害来源 → 敌人学会对应攻击方式
 * - 装备历史：玩家曾使用过的装备组合 → 敌人针对性进化反制策略
 * - 行为模式：玩家的战斗习惯（近战/远程/法术） → 敌人调整AI行为
 *
 * 持久化（F-05）：档案权威数据挂在 overworld 的
 * {@link NemesisProfileSavedData}（SavedData）上，随世界自动保存；
 * 本类的 playerProfiles 仅是在线玩家的运行缓存。
 * 档案对象在创建时即写入 SavedData（共享引用），每次变更 setDirty；
 * 玩家登出时确保档案落入 SavedData 后从内存缓存移除，
 * 玩家再次登入需要档案时按需从 SavedData 读回。
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class NemesisMemorySystem {

    /**
     * 单例实例
     */
    private static NemesisMemorySystem INSTANCE;

    /**
     * 在线玩家宿敌档案运行缓存
     * Key: 玩家UUID, Value: 宿敌档案（与 SavedData 持久层共享对象引用）
     * 仅保留在线玩家的条目，登出即移除，避免长期运行内存无界增长
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
        AdaptiveNemesisMod.LOGGER.debug("宿敌记忆系统已初始化");
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
     * 处理玩家登出事件 - 档案持久化并释放内存（F-05）
     *
     * 将该玩家的宿敌档案写入 SavedData（幂等）并标记落盘，
     * 然后从内存缓存移除条目；玩家再次登入需要档案时按需读回。
     *
     * @param event 玩家登出事件
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        NemesisProfile profile = playerProfiles.remove(playerId);
        if (profile == null) {
            return;
        }

        ServerLevel overworld = overworldOf(player);
        if (overworld != null) {
            NemesisProfileSavedData.get(overworld).putProfile(playerId, profile);
        }

        AdaptiveNemesisMod.LOGGER.debug(
            "玩家 {} 登出，宿敌档案已写入世界存档并从内存缓存移除",
            player.getName().getString()
        );
    }

    /**
     * 处理服务器停止事件 - 停服前兜底落盘（F-05）
     *
     * 在线玩家的档案与 SavedData 共享同一对象引用且变更时已 setDirty，
     * 此处再次幂等写入仅为防御性兜底，实际写盘由 vanilla 停服保存流程完成。
     *
     * @param event 服务器停止事件
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (playerProfiles.isEmpty()) {
            return;
        }

        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null) {
            return;
        }

        NemesisProfileSavedData savedData = NemesisProfileSavedData.get(overworld);
        for (Map.Entry<UUID, NemesisProfile> entry : playerProfiles.entrySet()) {
            savedData.putProfile(entry.getKey(), entry.getValue());
        }

        AdaptiveNemesisMod.LOGGER.debug(
            "停服前已将 {} 份在线玩家宿敌档案同步至世界存档",
            playerProfiles.size()
        );
    }

    /**
     * 记录玩家击杀偏好
     *
     * @param player 击杀者
     * @param source 伤害来源
     * @param victim 被击杀者
     */
    private void recordKillPreference(ServerPlayer player, DamageSource source, LivingEntity victim) {
        NemesisProfile profile = getOrCreateProfile(player.getUUID(), overworldOf(player));

        // 判断击杀方式
        CombatStyle style = determineCombatStyle(player, source);
        profile.recordKillStyle(style);

        // 记录使用的武器类型（F-50：使用注册 ID 而非 Item.toString()）
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.isEmpty()) {
            String weaponType = getRegistryIdString(weapon.getItem());
            if (weaponType != null) {
                profile.recordWeaponUsed(weaponType);
            }
        }

        // 记录击杀的敌人类型（F-50：使用注册 ID 而非 EntityType.toString()）
        String victimType = getEntityTypeRegistryId(victim.getType());
        if (victimType != null) {
            profile.recordKilledEntity(victimType);
        }

        // 档案变更后标记 SavedData 落盘
        markProfileDirty(player);

        // 触发 KubeJS 宿敌记忆更新事件
        KubeJSEventTrigger.triggerNemesisMemoryUpdate(player.getUUID(), player.getName().getString(), profile);

        if (AdaptiveNemesisMod.LOGGER.isDebugEnabled()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 击杀记录: 方式={}, 武器={}, 目标={}",
                player.getName().getString(),
                style,
                weapon.getItem(),
                victimType
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
        NemesisProfile profile = getOrCreateProfile(player.getUUID(), overworldOf(player));

        // 记录死亡来源类型
        String deathSource = source.getMsgId();
        profile.recordDeathSource(deathSource);

        // 记录击杀者类型（F-50：使用注册 ID 而非 EntityType.toString()）
        String killerType = null;
        if (source.getEntity() != null) {
            killerType = getEntityTypeRegistryId(source.getEntity().getType());
            if (killerType != null) {
                profile.recordKillerType(killerType);
            }
        }

        // 档案变更后标记 SavedData 落盘
        markProfileDirty(player);

        if (AdaptiveNemesisMod.LOGGER.isDebugEnabled()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 死亡记录: 来源={}, 击杀者={}",
                player.getName().getString(),
                deathSource,
                killerType
            );
        }
    }

    /**
     * 判断玩家的战斗风格
     *
     * 伤害来源按 msgId 关键词判定；武器风格保留子串匹配，
     * 但基于注册 ID（如 "minecraft:bow"）而非 Item.toString()，
     * 避免依赖未定义的 toString 行为（F-50）。
     *
     * @param player 目标玩家
     * @param source 伤害来源
     * @return 战斗风格枚举
     */
    private CombatStyle determineCombatStyle(ServerPlayer player, DamageSource source) {
        // 检查是否是远程攻击
        String msgId = source.getMsgId();
        if (msgId.contains("arrow") || msgId.contains("projectile") || msgId.contains("trident")) {
            return CombatStyle.RANGED;
        }

        // 检查是否是魔法攻击
        if (msgId.contains("magic") || msgId.contains("spell") || msgId.contains("indirect_magic")) {
            return CombatStyle.MAGIC;
        }

        // 检查主手武器（基于注册 ID 的子串匹配，兼容命名空间前缀）
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty()) {
            String itemName = getRegistryIdString(mainHand.getItem());
            if (itemName != null) {
                String lowered = itemName.toLowerCase(Locale.ROOT);
                if (lowered.contains("bow") || lowered.contains("crossbow")) {
                    return CombatStyle.RANGED;
                }
                if (lowered.contains("staff") || lowered.contains("wand") || lowered.contains("spell")) {
                    return CombatStyle.MAGIC;
                }
            }
        }

        // 默认近战
        return CombatStyle.MELEE;
    }

    /**
     * 获取或创建玩家宿敌档案（F-05：按需从 SavedData 读回）
     *
     * 缓存命中时直接返回；未命中（如重新登入、服务器重启后首次记录）
     * 时从 SavedData 读取；SavedData 也无档案时创建新档案并写入 SavedData。
     * 返回的档案与 SavedData 持久层共享对象引用。
     *
     * @param playerId 玩家UUID
     * @param overworld 主世界（SavedData 挂载维度），null 时退化为纯内存档案
     * @return 宿敌档案
     */
    private NemesisProfile getOrCreateProfile(UUID playerId, ServerLevel overworld) {
        NemesisProfile cached = playerProfiles.get(playerId);
        if (cached != null) {
            return cached;
        }

        if (overworld == null) {
            // 无法访问世界存档时退化为纯内存档案（本次会话内不持久化）
            AdaptiveNemesisMod.LOGGER.warn(
                "宿敌档案无法访问主世界存档，玩家 {} 的档案仅在内存中生效",
                playerId
            );
            return playerProfiles.computeIfAbsent(playerId, k -> new NemesisProfile());
        }

        NemesisProfileSavedData savedData = NemesisProfileSavedData.get(overworld);
        NemesisProfile profile = savedData.getProfile(playerId);
        if (profile == null) {
            profile = new NemesisProfile();
            savedData.putProfile(playerId, profile);
        }
        playerProfiles.put(playerId, profile);
        return profile;
    }

    /**
     * 获取玩家宿敌档案（仅查询在线玩家缓存）
     *
     * 离线玩家的档案已随登出移出内存缓存，此方法返回 null；
     * 离线档案持久保存在 SavedData 中，玩家重新登入并产生记录时自动读回。
     *
     * @param playerId 玩家UUID
     * @return 宿敌档案，玩家离线或无档案时为 null
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
     * 清除指定玩家的档案（仅内存缓存）
     *
     * 注意：该方法只移除内存缓存条目，不影响 SavedData 中的持久化档案；
     * 登出持久化由 {@link #onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent)} 负责。
     *
     * @param playerId 玩家UUID
     */
    public void clearProfile(UUID playerId) {
        playerProfiles.remove(playerId);
    }

    /**
     * 获取玩家所在服务器的主世界
     *
     * @param player 目标玩家
     * @return 主世界，不可得时返回 null
     */
    private ServerLevel overworldOf(ServerPlayer player) {
        return player.serverLevel().getServer().overworld();
    }

    /**
     * 玩家档案发生变更后标记 SavedData 落盘
     *
     * @param player 档案所属玩家
     */
    private void markProfileDirty(ServerPlayer player) {
        ServerLevel overworld = overworldOf(player);
        if (overworld != null) {
            NemesisProfileSavedData.get(overworld).setDirty();
        }
    }

    /**
     * 获取物品的注册 ID 字符串（F-50）
     *
     * @param item 目标物品
     * @return 注册 ID（如 "minecraft:bow"），未注册时返回 null
     */
    private String getRegistryIdString(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null ? id.toString() : null;
    }

    /**
     * 获取实体类型的注册 ID 字符串（F-50）
     *
     * @param type 目标实体类型
     * @return 注册 ID（如 "minecraft:zombie"），未注册时返回 null
     */
    private String getEntityTypeRegistryId(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null ? id.toString() : null;
    }

    /**
     * 战斗风格枚举
     */
    public enum CombatStyle {
        MELEE("adaptive_nemesis.combat_style.melee"),
        RANGED("adaptive_nemesis.combat_style.ranged"),
        MAGIC("adaptive_nemesis.combat_style.magic");

        private final String translationKey;

        CombatStyle(String translationKey) {
            this.translationKey = translationKey;
        }

        /**
         * 获取战斗风格的显示名称组件
         *
         * @return 已本地化的显示名称组件
         */
        public Component getDisplayName() {
            return Component.translatable(translationKey);
        }

        /**
         * 获取战斗风格的翻译键
         *
         * @return 翻译键
         */
        public String getTranslationKey() {
            return translationKey;
        }
    }
}
