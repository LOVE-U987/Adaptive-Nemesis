package com.adaptive_nemesis.adaptive_nemesismod;

import org.slf4j.Logger;

import com.adaptive_nemesis.adaptive_nemesismod.boss.BossDamageCapHandler;
import com.adaptive_nemesis.adaptive_nemesismod.boss.BossIdentificationService;
import com.adaptive_nemesis.adaptive_nemesismod.command.InvasionCommand;
import com.adaptive_nemesis.adaptive_nemesismod.command.ModCommands;
import com.adaptive_nemesis.adaptive_nemesismod.damage.TrueDamageHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.AdaptiveFloatSystem;
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

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Adaptive Nemesis / 自适应宿敌 主模组类 (Forge 1.20.1)
 */
@Mod(AdaptiveNemesisMod.MODID)
public class AdaptiveNemesisMod {

    public static final String MODID = "adaptive_nemesis";
    public static final String MOD_NAME = "Adaptive Nemesis";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AdaptiveNemesisMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onModConfigEvent);

        ModNetworking.register();

        // FMLJavaModLoadingContext extends ModLoadingContext (Forge 47.4+)
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        registerEventHandlers();

        LOGGER.info("🛡️ Adaptive Nemesis (自适应宿敌) 模组已加载 [Forge 1.20.1 / 47.4.x]");
    }

    private void onModConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            Config.MOD_CONFIG = event.getConfig();
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("========================================");
        LOGGER.info("⚔️ Adaptive Nemesis 正在初始化...");
        LOGGER.info("========================================");

        LOGGER.info("📊 难度系数基准: {}", Config.DIFFICULTY_BASE_MULTIPLIER.get());
        LOGGER.info("🗡️ 真实伤害机制: {}", Config.ENABLE_TRUE_DAMAGE.get() ? "已启用" : "已禁用");
        LOGGER.info("🛡️ 新手保护机制: {}", Config.ENABLE_NEWBIE_PROTECTION.get() ? "已启用" : "已禁用");
        LOGGER.info("👑 Boss伤害上限: {}", Config.ENABLE_BOSS_DAMAGE_CAP.get() ? "已启用" : "已禁用");
        LOGGER.info("📈 敌人加成上限: {}", Config.ENABLE_ENEMY_BONUS_CAP.get() ? "已启用" : "已禁用");

        initDebugLogFile();

        event.enqueueWork(() -> {
            PlayerStrengthEvaluator.getInstance().initialize();
            NemesisMemorySystem.getInstance().initialize();
            BossIdentificationService.getInstance().initialize();

            if (Config.ENABLE_WATCHDOG.get()) {
                WatchdogService.getInstance().start();
            }

            new NemesisSystem();
            LOGGER.info("👹 宿敌日常生成系统已初始化");

            InvasionSystem invasionSystem = new InvasionSystem();
            InvasionCommand.setInvasionSystem(invasionSystem);
            new InvasionKubeJsSupport(invasionSystem);
            LOGGER.info("⚔️ 入侵事件系统已初始化");
            LOGGER.info("✅ 各子系统初始化完成！");
        });

        LOGGER.info("========================================");
        LOGGER.info("✅ Adaptive Nemesis 初始化完成！");
        LOGGER.info("========================================");
    }

    private static void initDebugLogFile() {
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
            String loggerName = LOGGER.getName();
            LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
            if (loggerConfig == null || !loggerConfig.getName().equals(loggerName)) {
                loggerConfig = new LoggerConfig(loggerName, Level.DEBUG, true);
                config.addLogger(loggerName, loggerConfig);
            }
            loggerConfig.addAppender(appender, Level.DEBUG, null);
            context.updateLoggers();
            LOGGER.info("📝 调试日志文件已初始化: {}", Config.DEBUG_LOG_FILE_PATH.get());
        } catch (Exception e) {
            LOGGER.error("初始化调试日志文件失败: {}", e.getMessage());
        }
    }

    private void registerEventHandlers() {
        IEventBus eventBus = MinecraftForge.EVENT_BUS;
        eventBus.register(PlayerStrengthEvaluator.getInstance());
        eventBus.register(EnemyScalingHandler.getInstance());
        eventBus.register(AdaptiveFloatSystem.getInstance());
        eventBus.register(TrueDamageHandler.getInstance());
        eventBus.register(BossDamageCapHandler.getInstance());
        eventBus.register(NewbieProtectionHandler.getInstance());
        eventBus.register(NemesisMemorySystem.getInstance());
        eventBus.register(DifficultyTracker.getInstance());
        eventBus.register(WorldStageManager.getInstance());
        eventBus.register(EnchantmentScalingHandler.getInstance());
        eventBus.register(ModEventHandler.getInstance());
        eventBus.register(new ModCommands());
        LOGGER.debug("📋 所有事件处理器已注册");
    }
}
