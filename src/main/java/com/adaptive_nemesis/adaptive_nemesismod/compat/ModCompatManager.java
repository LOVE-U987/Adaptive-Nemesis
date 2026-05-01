package com.adaptive_nemesis.adaptive_nemesismod.compat;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * 模组兼容管理器
 * 
 * 负责检测和管理与其他模组的兼容性
 * 包括：铁魔法、史诗战斗、神话等
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class ModCompatManager {
    
    /**
     * 铁魔法模组ID
     */
    public static final String IRONS_SPELLS_MODID = "irons_spellbooks";
    
    /**
     * 史诗战斗模组ID
     */
    public static final String EPIC_FIGHT_MODID = "epicfight";
    
    /**
     * 神话模组ID
     */
    public static final String APOTHEOSIS_MODID = "apotheosis";
    
    /**
     * 铁魔法是否已加载
     */
    private static Boolean ironsSpellsLoaded = null;
    
    /**
     * 史诗战斗是否已加载
     */
    private static Boolean epicFightLoaded = null;
    
    /**
     * 神话是否已加载
     */
    private static Boolean apotheosisLoaded = null;
    
    /**
     * 铁魔法兼容处理器
     */
    private static IronsSpellsCompat ironsSpellsCompat;
    
    /**
     * 史诗战斗兼容处理器
     */
    private static EpicFightCompat epicFightCompat;
    
    /**
     * 神话兼容处理器
     */
    private static ApotheosisCompat apotheosisCompat;

    /**
     * 私有构造函数 - 工具类
     */
    private ModCompatManager() {}

    /**
     * 检查铁魔法模组是否已加载
     * 
     * @return 如果已加载返回true
     */
    public static boolean isIronsSpellsLoaded() {
        if (ironsSpellsLoaded == null) {
            ironsSpellsLoaded = ModList.get().isLoaded(IRONS_SPELLS_MODID);
            if (ironsSpellsLoaded) {
                AdaptiveNemesisMod.LOGGER.info("✅ 检测到铁魔法模组 (Iron's Spells 'n Spellbooks)，已启用兼容支持");
                try {
                    ironsSpellsCompat = new IronsSpellsCompat();
                } catch (Exception e) {
                    AdaptiveNemesisMod.LOGGER.error("❌ 铁魔法兼容初始化失败: {}", e.getMessage());
                    ironsSpellsLoaded = false;
                }
            }
        }
        return ironsSpellsLoaded;
    }

    /**
     * 检查史诗战斗模组是否已加载
     * 
     * @return 如果已加载返回true
     */
    public static boolean isEpicFightLoaded() {
        if (epicFightLoaded == null) {
            epicFightLoaded = ModList.get().isLoaded(EPIC_FIGHT_MODID);
            if (epicFightLoaded) {
                AdaptiveNemesisMod.LOGGER.info("✅ 检测到史诗战斗模组 (Epic Fight)，已启用兼容支持");
                try {
                    epicFightCompat = new EpicFightCompat();
                } catch (Exception e) {
                    AdaptiveNemesisMod.LOGGER.error("❌ 史诗战斗兼容初始化失败: {}", e.getMessage());
                    epicFightLoaded = false;
                }
            }
        }
        return epicFightLoaded;
    }

    /**
     * 检查神话模组是否已加载
     * 
     * @return 如果已加载返回true
     */
    public static boolean isApotheosisLoaded() {
        if (apotheosisLoaded == null) {
            apotheosisLoaded = ModList.get().isLoaded(APOTHEOSIS_MODID);
            if (apotheosisLoaded) {
                AdaptiveNemesisMod.LOGGER.info("✅ 检测到神话模组 (Apotheosis)，已启用兼容支持");
                try {
                    apotheosisCompat = new ApotheosisCompat();
                } catch (Exception e) {
                    AdaptiveNemesisMod.LOGGER.error("❌ 神话兼容初始化失败: {}", e.getMessage());
                    apotheosisLoaded = false;
                }
            }
        }
        return apotheosisLoaded;
    }

    /**
     * 获取玩家的铁魔法强度
     * 
     * @param player 目标玩家
     * @return 铁魔法强度值，如果模组未加载返回0
     */
    public static double getIronsSpellsStrength(ServerPlayer player) {
        if (!isIronsSpellsLoaded() || ironsSpellsCompat == null) {
            return 0.0;
        }
        return ironsSpellsCompat.getPlayerSpellStrength(player);
    }

    /**
     * 获取玩家的史诗战斗强度
     * 
     * @param player 目标玩家
     * @return 史诗战斗强度值，如果模组未加载返回0
     */
    public static double getEpicFightStrength(ServerPlayer player) {
        if (!isEpicFightLoaded() || epicFightCompat == null) {
            return 0.0;
        }
        return epicFightCompat.getPlayerCombatStrength(player);
    }

    /**
     * 获取玩家的神话词条强度
     * 
     * @param player 目标玩家
     * @return 神话词条强度值，如果模组未加载返回0
     */
    public static double getApotheosisStrength(ServerPlayer player) {
        if (!isApotheosisLoaded() || apotheosisCompat == null) {
            return 0.0;
        }
        return apotheosisCompat.getPlayerGearStrength(player);
    }

    /**
     * 获取铁魔法兼容处理器
     * 
     * @return IronsSpellsCompat 实例，可能为null
     */
    public static IronsSpellsCompat getIronsSpellsCompat() {
        return ironsSpellsCompat;
    }

    /**
     * 获取史诗战斗兼容处理器
     * 
     * @return EpicFightCompat 实例，可能为null
     */
    public static EpicFightCompat getEpicFightCompat() {
        return epicFightCompat;
    }

    /**
     * 获取神话兼容处理器
     * 
     * @return ApotheosisCompat 实例，可能为null
     */
    public static ApotheosisCompat getApotheosisCompat() {
        return apotheosisCompat;
    }
}
