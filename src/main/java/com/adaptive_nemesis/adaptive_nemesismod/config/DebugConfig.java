package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 调试配置
 */
public class DebugConfig {

    /**
     * 是否启用调试日志
     */
    public final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOG;

    /**
     * 是否启用详细调试模式（输出更多信息）
     */
    public final ModConfigSpec.BooleanValue ENABLE_VERBOSE_DEBUG;

    /**
     * 是否将调试日志输出到文件
     */
    public final ModConfigSpec.BooleanValue DEBUG_LOG_TO_FILE;

    /**
     * 调试日志文件路径
     */
    public final ModConfigSpec.ConfigValue<String> DEBUG_LOG_FILE_PATH;

    /**
     * 日志输出级别 (OFF, ERROR, WARN, INFO, DEBUG)
     */
    public final ModConfigSpec.ConfigValue<String> LOG_OUTPUT_LEVEL;

    public DebugConfig(ModConfigSpec.Builder builder) {
        builder.push("debug");
        ENABLE_DEBUG_LOG = builder
            .comment("是否启用调试日志输出")
            .define("enableDebugLog", false);
        ENABLE_VERBOSE_DEBUG = builder
            .comment("是否启用详细调试模式 - 输出更多详细信息")
            .comment("Enable verbose debug mode - outputs more detailed information")
            .define("enableVerboseDebug", false);
        DEBUG_LOG_TO_FILE = builder
            .comment("是否将调试日志输出到文件")
            .comment("Enable debug log output to file")
            .define("debugLogToFile", false);
        DEBUG_LOG_FILE_PATH = builder
            .comment("调试日志文件路径（相对于游戏目录）")
            .comment("Debug log file path (relative to game directory)")
            .define("debugLogFilePath", "logs/adaptive_nemesis_debug.log");
        LOG_OUTPUT_LEVEL = builder
            .comment("日志输出级别: OFF(关闭), ERROR(错误), WARN(警告), INFO(信息), DEBUG(调试)")
            .comment("Log output level: OFF, ERROR, WARN, INFO, DEBUG")
            .define("logOutputLevel", "INFO");
        builder.pop();
    }
}
