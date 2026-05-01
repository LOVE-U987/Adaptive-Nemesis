package com.adaptive_nemesis.adaptive_nemesismod.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.compat.ModCompatManager;
import com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSEventTrigger;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 玩家强度评估系统
 * 
 * 基于多维度数据评估玩家综合强度，包括：
 * - 防御能力（护甲值、血量上限）
 * - 输出能力（伤害数值）
 * - 神话词条（品质与等级）
 * - 铁魔法（法力值、法术强度）
 * - 史诗战斗（耐力值）
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class PlayerStrengthEvaluator {
    
    /**
     * 单例实例
     */
    private static PlayerStrengthEvaluator INSTANCE;
    
    /**
     * 玩家强度缓存 - 避免频繁计算
     * Key: 玩家UUID, Value: 强度数据
     */
    private final Map<UUID, PlayerStrengthData> strengthCache = new HashMap<>();
    
    /**
     * 缓存刷新间隔（tick）
     */
    private static final int CACHE_REFRESH_INTERVAL = 100; // 5秒
    
    /**
     * 私有构造函数 - 单例模式
     */
    private PlayerStrengthEvaluator() {}
    
    /**
     * 获取单例实例
     * 
     * @return PlayerStrengthEvaluator 实例
     */
    public static synchronized PlayerStrengthEvaluator getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PlayerStrengthEvaluator();
        }
        return INSTANCE;
    }
    
    /**
     * 初始化评估系统
     */
    public void initialize() {
        AdaptiveNemesisMod.LOGGER.info("🧠 玩家强度评估系统已初始化");
    }
    
    /**
     * 玩家Tick事件 - 定期更新玩家强度缓存
     * 
     * @param event 玩家Tick事件
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        
        // 只在服务端执行
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        
        // 每5秒更新一次
        if (serverPlayer.tickCount % CACHE_REFRESH_INTERVAL == 0) {
            updatePlayerStrength(serverPlayer);
        }
    }
    
    /**
     * 更新指定玩家的强度数据
     * 
     * @param player 目标玩家
     * @return 更新后的强度数据
     */
    public PlayerStrengthData updatePlayerStrength(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        // 计算各维度强度
        double defenseStrength = calculateDefenseStrength(player);
        double damageStrength = calculateDamageStrength(player);
        double apotheosisStrength = calculateApotheosisStrength(player);
        double ironsSpellsStrength = calculateIronsSpellsStrength(player);
        double epicFightStrength = calculateEpicFightStrength(player);
        
        // 加权计算综合强度
        double totalStrength = 
            defenseStrength * Config.DEFENSE_WEIGHT.get() +
            damageStrength * Config.DAMAGE_WEIGHT.get() +
            apotheosisStrength * Config.APOTHEOSIS_WEIGHT.get() +
            ironsSpellsStrength * Config.IRONS_SPELLS_WEIGHT.get() +
            epicFightStrength * Config.EPIC_FIGHT_WEIGHT.get();
        
        // 触发 KubeJS 玩家强度评估事件
        double modifiedStrength = KubeJSEventTrigger.triggerPlayerStrengthEvaluation(
            player, totalStrength, defenseStrength, damageStrength, 
            ironsSpellsStrength, epicFightStrength
        );
        
        // 使用 KubeJS 修改后的强度
        totalStrength = modifiedStrength;
        
        PlayerStrengthData data = new PlayerStrengthData(
            totalStrength,
            defenseStrength,
            damageStrength,
            apotheosisStrength,
            ironsSpellsStrength,
            epicFightStrength
        );
        
        strengthCache.put(playerId, data);
        
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 强度更新: 综合={}, 防御={}, 输出={}, 神话={}, 铁魔法={}, 史诗战斗={}",
                player.getName().getString(),
                String.format("%.2f", totalStrength),
                String.format("%.2f", defenseStrength),
                String.format("%.2f", damageStrength),
                String.format("%.2f", apotheosisStrength),
                String.format("%.2f", ironsSpellsStrength),
                String.format("%.2f", epicFightStrength)
            );
        }
        
        return data;
    }
    
    /**
     * 获取指定玩家的强度数据（从缓存）
     * 
     * @param player 目标玩家
     * @return 强度数据，如果不存在则重新计算
     */
    public PlayerStrengthData getPlayerStrength(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerStrengthData data = strengthCache.get(playerId);
        
        if (data == null) {
            data = updatePlayerStrength(player);
        }
        
        return data;
    }
    
    /**
     * 获取指定玩家的强度数据（从缓存）
     * 
     * @param playerId 玩家UUID
     * @return 强度数据，可能为null
     */
    public PlayerStrengthData getPlayerStrength(UUID playerId) {
        return strengthCache.get(playerId);
    }
    
    /**
     * 计算玩家防御能力强度
     * 
     * @param player 目标玩家
     * @return 防御强度值
     */
    private double calculateDefenseStrength(ServerPlayer player) {
        double armor = player.getAttributeValue(Attributes.ARMOR);
        double maxHealth = player.getMaxHealth();
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        
        // 防御强度 = 护甲值 * 2 + 血量上限 + 韧性 * 1.5
        return armor * 2.0 + maxHealth + toughness * 1.5;
    }
    
    /**
     * 计算玩家输出能力强度
     * 
     * @param player 目标玩家
     * @return 输出强度值
     */
    private double calculateDamageStrength(ServerPlayer player) {
        double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        
        // 获取主手武器伤害
        ItemStack mainHand = player.getMainHandItem();
        double weaponDamage = attackDamage;
        
        // 如果有附魔，增加额外评分
        double enchantBonus = 0;
        if (!mainHand.isEmpty() && mainHand.isEnchanted()) {
            enchantBonus = mainHand.getEnchantments().size() * 2.0;
        }
        
        // 输出强度 = 攻击力 * 3 + 攻击速度 * 2 + 附魔加成
        return weaponDamage * 3.0 + attackSpeed * 2.0 + enchantBonus;
    }
    
    /**
     * 计算玩家神话词条强度（通过兼容层）
     * 
     * @param player 目标玩家
     * @return 神话词条强度值
     */
    private double calculateApotheosisStrength(ServerPlayer player) {
        if (!ModCompatManager.isApotheosisLoaded()) {
            return 0.0;
        }
        
        return ModCompatManager.getApotheosisStrength(player);
    }
    
    /**
     * 计算玩家铁魔法强度（通过兼容层）
     * 
     * @param player 目标玩家
     * @return 铁魔法强度值
     */
    private double calculateIronsSpellsStrength(ServerPlayer player) {
        if (!ModCompatManager.isIronsSpellsLoaded()) {
            return 0.0;
        }
        
        return ModCompatManager.getIronsSpellsStrength(player);
    }
    
    /**
     * 计算玩家史诗战斗强度（通过兼容层）
     * 
     * @param player 目标玩家
     * @return 史诗战斗强度值
     */
    private double calculateEpicFightStrength(ServerPlayer player) {
        if (!ModCompatManager.isEpicFightLoaded()) {
            return 0.0;
        }
        
        return ModCompatManager.getEpicFightStrength(player);
    }
    
    /**
     * 玩家登出事件 - 清理缓存避免内存泄漏
     *
     * @param event 玩家登出事件
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        strengthCache.remove(playerId);
        
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 登出，已清理强度缓存",
                event.getEntity().getName().getString()
            );
        }
    }

    /**
     * 清除指定玩家的缓存数据
     *
     * @param playerId 玩家UUID
     */
    public void clearCache(UUID playerId) {
        strengthCache.remove(playerId);
    }

    /**
     * 清除所有缓存数据
     */
    public void clearAllCache() {
        strengthCache.clear();
    }
}
