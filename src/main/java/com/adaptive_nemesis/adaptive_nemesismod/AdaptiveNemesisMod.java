package com.adaptive_nemesis.adaptive_nemesismod;

import org.slf4j.Logger;

import com.adaptive_nemesis.adaptive_nemesismod.boss.BossDamageCapHandler;
import com.adaptive_nemesis.adaptive_nemesismod.command.ModCommands;
import com.adaptive_nemesis.adaptive_nemesismod.damage.TrueDamageHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnemyScalingHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.DifficultyTracker;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.WorldStageManager;
import com.adaptive_nemesis.adaptive_nemesismod.event.ModEventHandler;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.network.ModNetworking;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.adaptive_nemesis.adaptive_nemesismod.protection.NewbieProtectionHandler;
import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Adaptive Nemesis / 自适应宿敌 主模组类
 * 
 * 动态难度平衡模组 - 专为整合包设计
 * 解决"前期刮痧、后期秒天秒地"的难度失衡问题
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
@Mod(AdaptiveNemesisMod.MODID)
public class AdaptiveNemesisMod {
    
    /**
     * 模组唯一标识符
     */
    public static final String MODID = "adaptive_nemesis";
    
    /**
     * 模组显示名称
     */
    public static final String MOD_NAME = "Adaptive Nemesis";
    
    /**
     * SLF4J 日志记录器
     */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 模组构造函数 - NeoForge 加载时自动调用
     * 
     * @param modEventBus 模组事件总线
     * @param modContainer 模组容器
     */
    public AdaptiveNemesisMod(IEventBus modEventBus, ModContainer modContainer) {
        // 注册通用设置监听器
        modEventBus.addListener(this::commonSetup);
        
        // 注册网络系统
        ModNetworking.register(modEventBus);
        
        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        // 注册游戏事件处理器
        registerEventHandlers();
        
        LOGGER.info("🛡️ Adaptive Nemesis (自适应宿敌) 模组已加载");
    }

    /**
     * 通用设置初始化
     * 在模组加载完成后调用，用于初始化各子系统
     * 
     * @param event 通用设置事件
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("========================================");
        LOGGER.info("⚔️ Adaptive Nemesis 正在初始化...");
        LOGGER.info("========================================");
        
        // 输出当前配置信息
        LOGGER.info("📊 难度系数基准: {}", Config.DIFFICULTY_BASE_MULTIPLIER.get());
        LOGGER.info("🗡️ 真实伤害机制: {}", Config.ENABLE_TRUE_DAMAGE.get() ? "已启用" : "已禁用");
        LOGGER.info("🛡️ 新手保护机制: {}", Config.ENABLE_NEWBIE_PROTECTION.get() ? "已启用" : "已禁用");
        LOGGER.info("👑 Boss伤害上限: {}", Config.ENABLE_BOSS_DAMAGE_CAP.get() ? "已启用" : "已禁用");
        LOGGER.info("📈 敌人加成上限: {}", Config.ENABLE_ENEMY_BONUS_CAP.get() ? "已启用" : "已禁用");
        
        // 初始化各子系统
        event.enqueueWork(() -> {
            PlayerStrengthEvaluator.getInstance().initialize();
            NemesisMemorySystem.getInstance().initialize();
            LOGGER.info("✅ 各子系统初始化完成！");
        });
        
        LOGGER.info("========================================");
        LOGGER.info("✅ Adaptive Nemesis 初始化完成！");
        LOGGER.info("========================================");
    }

    /**
     * 注册所有游戏事件处理器
     */
    private void registerEventHandlers() {
        IEventBus eventBus = NeoForge.EVENT_BUS;
        
        // 注册玩家强度评估器
        eventBus.register(PlayerStrengthEvaluator.getInstance());
        
        // 注册敌人强化处理器
        eventBus.register(EnemyScalingHandler.getInstance());
        
        // 注册真实伤害处理器
        eventBus.register(TrueDamageHandler.getInstance());
        
        // 注册Boss伤害上限处理器
        eventBus.register(BossDamageCapHandler.getInstance());
        
        // 注册新手保护处理器
        eventBus.register(NewbieProtectionHandler.getInstance());
        
        // 注册宿敌记忆系统
        eventBus.register(NemesisMemorySystem.getInstance());

        // 注册难度缓动跟踪器
        eventBus.register(DifficultyTracker.getInstance());

        // 注册世界阶段管理器
        eventBus.register(WorldStageManager.getInstance());

        // 注册通用事件处理器
        eventBus.register(ModEventHandler.getInstance());
        
        // 注册命令系统
        eventBus.register(new ModCommands());
        
        LOGGER.debug("📋 所有事件处理器已注册");
    }
}
