package com.adaptive_nemesis.adaptive_nemesismod;

import org.slf4j.Logger;

import com.adaptive_nemesis.adaptive_nemesismod.boss.BossDamageCapHandler;
import com.adaptive_nemesis.adaptive_nemesismod.boss.BossIdentificationService;
import com.adaptive_nemesis.adaptive_nemesismod.command.InvasionCommand;
import com.adaptive_nemesis.adaptive_nemesismod.command.ModCommands;
import com.adaptive_nemesis.adaptive_nemesismod.damage.TrueDamageHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnemyScalingHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.DifficultyTracker;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnchantmentScalingHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.WorldStageManager;
import com.adaptive_nemesis.adaptive_nemesismod.event.ModEventHandler;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionKubeJsSupport;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.network.ModNetworking;
import com.adaptive_nemesis.adaptive_nemesismod.nemesis.NemesisSystem;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.adaptive_nemesis.adaptive_nemesismod.protection.NewbieProtectionHandler;
import com.adaptive_nemesis.adaptive_nemesismod.watchdog.WatchdogService;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

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
     * 配置是否已加载完成
     */
    private static boolean configLoaded = false;

    /**
     * 模组构造函数 - NeoForge 加载时自动调用
     * 
     * @param modEventBus 模组事件总线
     * @param modContainer 模组容器
     */
    public AdaptiveNemesisMod(IEventBus modEventBus, ModContainer modContainer) {
        // 注册通用设置监听器
        modEventBus.addListener(this::commonSetup);
        
        // 注册配置事件监听器（用于保存引用和输出配置信息）
        modEventBus.addListener(this::onModConfigEvent);
        
        // 注册网络系统
        ModNetworking.register(modEventBus);
        
        // 注册配置
        // 使用 SERVER 类型：配置以服务端为权威，NeoForge 会自动把服务端配置同步给所有连接的客户端，
        // 解决此前 COMMON 类型下服务端与客户端各自加载各自配置文件、联机时服务端配置不生效的问题。
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        // 注册游戏事件处理器
        registerEventHandlers();
        
        LOGGER.info("🛡️ Adaptive Nemesis (自适应宿敌) 模组已加载");
    }

    /**
     * 配置加载/重载事件处理
     * 保存 ModConfig 引用用于后续保存操作，并在配置加载完成后输出配置信息
     */
    private void onModConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            Config.MOD_CONFIG = event.getConfig();
            
            // 仅在配置首次加载时输出配置信息（避免每次重载都输出日志）
            if (event instanceof ModConfigEvent.Loading) {
                // 先设置 configLoaded 标志，确保后续方法可以安全读取配置
                configLoaded = true;
                LOGGER.info("========================================");
                LOGGER.info("📜 Adaptive Nemesis 配置已加载");
                LOGGER.info("========================================");
                LOGGER.info("📊 难度系数基准：{}", Config.DIFFICULTY_BASE_MULTIPLIER.get());
                LOGGER.info("🗡️ 真实伤害机制：{}", Config.ENABLE_TRUE_DAMAGE.get() ? "已启用" : "已禁用");
                LOGGER.info("🛡️ 新手保护机制：{}", Config.ENABLE_NEWBIE_PROTECTION.get() ? "已启用" : "已禁用");
                LOGGER.info("👑 Boss 伤害上限：{}", Config.ENABLE_BOSS_DAMAGE_CAP.get() ? "已启用" : "已禁用");
                LOGGER.info("📈 敌人加成上限：{}", Config.ENABLE_ENEMY_BONUS_CAP.get() ? "已启用" : "已禁用");
                LOGGER.info("🔍 看门狗服务：{}", Config.ENABLE_WATCHDOG.get() ? "已启用" : "已禁用");
                LOGGER.info("========================================");
                
                // 配置加载完成后，初始化调试日志文件（确保配置已加载）
                initDebugLogFile();
                
                // 配置加载完成后，初始化 Boss 识别服务（确保配置已加载）
                BossIdentificationService.initialize();
                
                // 配置加载完成后，启动看门狗服务（配置已加载完毕，可以安全读取 Config 值）
                if (Config.ENABLE_WATCHDOG.get()) {
                    WatchdogService.getInstance().start();
                }
            }
        }
    }

    /**
     * 通用设置初始化
     * 在模组加载完成后调用，用于初始化各子系统
     * 注意：不要在 commonSetup 中直接读取配置值，应该等待配置加载完成后再读取
     * 
     * @param event 通用设置事件
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("========================================");
        LOGGER.info("⚔️ Adaptive Nemesis 正在初始化...");
        LOGGER.info("========================================");
        
        // 初始化各子系统（不读取配置值）
        event.enqueueWork(() -> {
            PlayerStrengthEvaluator.getInstance().initialize();
            NemesisMemorySystem.getInstance().initialize();

            // Boss 识别服务已在配置加载完成后初始化
            // 如果配置还未加载完成，BossIdentificationService 会懒加载

            LOGGER.info("👹 宿敌日常生成系统已初始化");

            // 初始化入侵事件系统
            InvasionSystem invasionSystem = new InvasionSystem();
            InvasionCommand.setInvasionSystem(invasionSystem);
            
            // 初始化 KubeJS 支持
            new InvasionKubeJsSupport(invasionSystem);
            LOGGER.info("⚔️ 入侵事件系统已初始化");

            LOGGER.info("✅ 各子系统初始化完成！");
        });
        
        LOGGER.info("========================================");
        LOGGER.info("✅ Adaptive Nemesis 初始化完成！");
        LOGGER.info("========================================");
    }

    /**
     * 初始化调试日志文件输出
     * 当配置中启用 debugLogToFile 时，自动创建单独的日志文件
     * 使用 Log4j2 FileAppender 捕获模组所有 DEBUG 级别以上的日志
     * 注意：此方法应在配置加载完成后调用
     */
    private void initDebugLogFile() {
        if (!Config.DEBUG_LOG_TO_FILE.get()) return;

        try {
            LoggerContext context = LoggerContext.getContext(false);
            Configuration config = context.getConfiguration();

            PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("[%d{HH:mm:ss.SSS}][%level] %msg%n")
                .withConfiguration(config)
                .build();

            FileAppender appender = FileAppender.newBuilder()
                .withFileName(Config.DEBUG_LOG_FILE_PATH.get())
                .withName("AdaptiveNemesisDebugFile")
                .withAppend(true)
                .withLayout(layout)
                .withConfiguration(config)
                .build();
            appender.start();

            config.addAppender(appender);

            // 获取我们日志器的 Log4j2 LoggerConfig 并附加文件输出
            String loggerName = LOGGER.getName();
            LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);

            if (loggerConfig == null || !loggerConfig.getName().equals(loggerName)) {
                loggerConfig = new LoggerConfig(loggerName, Level.DEBUG, true);
                config.addLogger(loggerName, loggerConfig);
            }

            loggerConfig.addAppender(appender, Level.DEBUG, null);
            context.updateLoggers();

            LOGGER.info("📝 调试日志文件已初始化：{}", Config.DEBUG_LOG_FILE_PATH.get());
        } catch (Exception e) {
            LOGGER.error("初始化调试日志文件失败：{}", e.getMessage());
        }
    }

    /**
     * 注册所有游戏事件处理器
     */
    private void registerEventHandlers() {
        IEventBus eventBus = NeoForge.EVENT_BUS;

        // 注册宿敌日常生成系统
        // ⚠️ 必须先于敌人强化处理器注册：
        // 同一个 EntityJoinLevelEvent 中，宿敌转化（打宿敌标记）需先于自适应缩放执行，
        // 铁魔法兼容层才能识别宿敌并应用法术抗性/强度加成；
        // 若顺序颠倒，宿敌会被当作普通怪处理而拿不到法术类加成。
        new NemesisSystem();

        // 注册玩家强度评估器
        eventBus.register(PlayerStrengthEvaluator.getInstance());
        
        // 注册敌人强化处理器
        eventBus.register(EnemyScalingHandler.getInstance());
        
        // 注册真实伤害处理器
        eventBus.register(TrueDamageHandler.getInstance());
        
        // 注册 Boss 伤害上限处理器
        eventBus.register(BossDamageCapHandler.getInstance());
        
        // 注册新手保护处理器
        eventBus.register(NewbieProtectionHandler.getInstance());
        
        // 注册宿敌记忆系统
        eventBus.register(NemesisMemorySystem.getInstance());

        // 注册难度缓动跟踪器
        eventBus.register(DifficultyTracker.getInstance());

        // 注册世界阶段管理器
        eventBus.register(WorldStageManager.getInstance());

        // 注册装备附魔强化处理器
        eventBus.register(EnchantmentScalingHandler.getInstance());

        // 注册通用事件处理器
        eventBus.register(ModEventHandler.getInstance());
        
        // 注册命令系统
        eventBus.register(new ModCommands());
        
        LOGGER.debug("📋 所有事件处理器已注册");
    }
}
