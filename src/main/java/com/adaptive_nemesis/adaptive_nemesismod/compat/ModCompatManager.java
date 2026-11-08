package com.adaptive_nemesis.adaptive_nemesismod.compat;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 模组兼容管理器
 *
 * 负责检测和管理与其他模组的兼容性
 * 包括：铁魔法、史诗战斗、神话等
 *
 * 缓存分离设计（F-26）：
 * - "ModList 是否加载"仅在首次探测时查询一次并缓存，探测结果运行期不会变化；
 * - "配置开关"每次调用现读，运行期切换配置可即时生效；
 * - ModList 尚未初始化（{@code ModList.get()} 返回 null）时不写入任何缓存，
 *   待其初始化完成后可重新探测。
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
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
     * 铁魔法兼容状态
     */
    private static final CompatState<IronsSpellsCompat> IRONS_SPELLS = new CompatState<>(
        IRONS_SPELLS_MODID, "铁魔法模组 (Iron's Spells 'n Spellbooks)", false,
        () -> Config.MOD_COMPAT_IRONS_SPELLS_ENABLED.get(), IronsSpellsCompat::new);

    /**
     * 史诗战斗兼容状态
     */
    private static final CompatState<EpicFightCompat> EPIC_FIGHT = new CompatState<>(
        EPIC_FIGHT_MODID, "史诗战斗模组 (Epic Fight)", true,
        () -> Config.MOD_COMPAT_EPIC_FIGHT_ENABLED.get(), EpicFightCompat::new);

    /**
     * 神话兼容状态
     */
    private static final CompatState<ApotheosisCompat> APOTHEOSIS = new CompatState<>(
        APOTHEOSIS_MODID, "神话模组 (Apotheosis)", false,
        () -> Config.MOD_COMPAT_APOTHEOSIS_ENABLED.get(), ApotheosisCompat::new);

    /**
     * 私有构造函数 - 工具类
     */
    private ModCompatManager() {}

    /**
     * 检查铁魔法模组是否已加载且兼容可用
     *
     * @return 如果兼容可用返回true
     */
    public static boolean isIronsSpellsLoaded() {
        return isCompatAvailable(IRONS_SPELLS);
    }

    /**
     * 检查史诗战斗模组是否已加载且兼容可用
     *
     * @return 如果兼容可用返回true
     */
    public static boolean isEpicFightLoaded() {
        return isCompatAvailable(EPIC_FIGHT);
    }

    /**
     * 检查神话模组是否已加载且兼容可用
     *
     * @return 如果兼容可用返回true
     */
    public static boolean isApotheosisLoaded() {
        return isCompatAvailable(APOTHEOSIS);
    }

    /**
     * 获取玩家的铁魔法强度
     *
     * @param player 目标玩家
     * @return 铁魔法强度值，如果模组未加载返回0
     */
    public static double getIronsSpellsStrength(ServerPlayer player) {
        if (!isCompatAvailable(IRONS_SPELLS)) {
            return 0.0;
        }
        return IRONS_SPELLS.compat.getPlayerSpellStrength(player);
    }

    /**
     * 获取玩家的史诗战斗强度
     *
     * @param player 目标玩家
     * @return 史诗战斗强度值，如果模组未加载返回0
     */
    public static double getEpicFightStrength(ServerPlayer player) {
        if (!isCompatAvailable(EPIC_FIGHT)) {
            return 0.0;
        }
        return EPIC_FIGHT.compat.getPlayerCombatStrength(player);
    }

    /**
     * 获取玩家的神话词条强度
     *
     * @param player 目标玩家
     * @return 神话词条强度值，如果模组未加载返回0
     */
    public static double getApotheosisStrength(ServerPlayer player) {
        if (!isCompatAvailable(APOTHEOSIS)) {
            return 0.0;
        }
        return APOTHEOSIS.compat.getPlayerGearStrength(player);
    }

    /**
     * 获取铁魔法兼容处理器
     *
     * @return IronsSpellsCompat 实例，可能为null
     */
    public static IronsSpellsCompat getIronsSpellsCompat() {
        return IRONS_SPELLS.compat;
    }

    /**
     * 获取史诗战斗兼容处理器
     *
     * @return EpicFightCompat 实例，可能为null
     */
    public static EpicFightCompat getEpicFightCompat() {
        return EPIC_FIGHT.compat;
    }

    /**
     * 获取神话兼容处理器
     *
     * @return ApotheosisCompat 实例，可能为null
     */
    public static ApotheosisCompat getApotheosisCompat() {
        return APOTHEOSIS.compat;
    }

    /**
     * 判定单个兼容模组当前是否可用
     *
     * 判定顺序：ModList 加载状态（缓存）-> 配置开关（每次现读）-> 兼容处理器就绪。
     * ModList 尚未初始化时直接返回 false 且不写入缓存；
     * 兼容处理器初始化失败时记录失败标记，后续调用不再重复尝试。
     *
     * @param state 兼容模组状态
     * @return 模组已加载、配置已开启且兼容处理器就绪时返回 true
     */
    private static boolean isCompatAvailable(CompatState<?> state) {
        if (state.modLoaded == null) {
            ModList modList = ModList.get();
            if (modList == null) {
                // ModList 未初始化时不落缓存，待初始化完成后重新探测
                AdaptiveNemesisMod.LOGGER.warn("ModList 尚未初始化，暂时禁用{}兼容检测", state.displayName);
                return false;
            }
            state.modLoaded = modList.isLoaded(state.modId);
        }
        if (!state.modLoaded || state.initFailed) {
            return false;
        }
        // 配置开关每次现读，保证运行期切换配置即时生效
        if (!state.enabledConfig.getAsBoolean()) {
            return false;
        }
        return ensureCompatInstance(state);
    }

    /**
     * 确保兼容处理器实例已创建
     *
     * 首次成功创建时输出检测日志；创建失败时记录失败标记并返回 false，
     * 避免每次调用都重复抛异常与刷日志。
     *
     * @param state 兼容模组状态
     * @return 兼容处理器就绪返回 true
     */
    private static boolean ensureCompatInstance(CompatState<?> state) {
        if (state.compat != null) {
            return true;
        }
        try {
            state.createCompat();
        } catch (Exception e) {
            state.initFailed = true;
            AdaptiveNemesisMod.LOGGER.error("{}兼容初始化失败: {}", state.displayName, e.getMessage());
            return false;
        }
        if (state.debugLog) {
            AdaptiveNemesisMod.LOGGER.debug("检测到{}，已启用兼容支持", state.displayName);
        } else {
            AdaptiveNemesisMod.LOGGER.info("检测到{}，已启用兼容支持", state.displayName);
        }
        return true;
    }

    /**
     * 单个兼容模组的运行状态
     *
     * @param <T> 兼容处理器类型
     */
    private static final class CompatState<T> {

        /** 目标模组 ID（ModList 探测用） */
        private final String modId;

        /** 模组显示名称（日志用） */
        private final String displayName;

        /** 检测日志是否降级为 debug 输出 */
        private final boolean debugLog;

        /** 配置开关读取器（每次调用现读，不缓存） */
        private final BooleanSupplier enabledConfig;

        /** 兼容处理器工厂（方法引用，仅在确认加载后才会触发目标类加载） */
        private final Supplier<T> compatFactory;

        /** ModList 加载状态缓存（null 表示尚未探测；ModList 未初始化时保持 null） */
        private Boolean modLoaded;

        /** 兼容处理器初始化失败标记（一次性，防止反复重试刷日志） */
        private boolean initFailed;

        /** 兼容处理器实例（就绪后非 null） */
        private T compat;

        /**
         * 构造兼容模组状态
         *
         * @param modId 目标模组 ID
         * @param displayName 模组显示名称（日志用）
         * @param debugLog 检测日志是否降级为 debug 输出
         * @param enabledConfig 配置开关读取器
         * @param compatFactory 兼容处理器工厂
         */
        CompatState(String modId, String displayName, boolean debugLog,
                    BooleanSupplier enabledConfig, Supplier<T> compatFactory) {
            this.modId = modId;
            this.displayName = displayName;
            this.debugLog = debugLog;
            this.enabledConfig = enabledConfig;
            this.compatFactory = compatFactory;
        }

        /**
         * 通过工厂创建兼容处理器实例
         */
        void createCompat() {
            this.compat = compatFactory.get();
        }
    }
}
