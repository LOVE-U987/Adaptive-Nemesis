package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 看门狗服务配置
 */
public class WatchdogConfig {

    /**
     * 是否启用看门狗服务 - 监控服务端线程卡死/死锁
     */
    public final ModConfigSpec.BooleanValue ENABLE_WATCHDOG;

    /**
     * 看门狗检查间隔（秒）
     */
    public final ModConfigSpec.IntValue WATCHDOG_CHECK_INTERVAL;

    /**
     * 看门狗警告阈值（秒）- 超过此时间无响应输出警告
     */
    public final ModConfigSpec.IntValue WATCHDOG_WARN_THRESHOLD;

    /**
     * 看门狗严重阈值（秒）- 超过此时间输出线程堆栈
     */
    public final ModConfigSpec.IntValue WATCHDOG_CRITICAL_THRESHOLD;

    public WatchdogConfig(ModConfigSpec.Builder builder) {
        builder.push("watchdog");
        ENABLE_WATCHDOG = builder
            .comment("是否启用看门狗服务 - 监控服务端线程是否卡死/死锁")
            .comment("Enable watchdog service - monitors server thread for hangs/deadlocks")
            .define("enableWatchdog", false);
        WATCHDOG_CHECK_INTERVAL = builder
            .comment("看门狗检查间隔（秒）- 每多少秒检查一次服务端活性")
            .comment("Watchdog check interval (seconds) - how often to check server activity")
            .defineInRange("watchdogCheckInterval", 5, 1, 30);
        WATCHDOG_WARN_THRESHOLD = builder
            .comment("看门狗警告阈值（秒）- 服务端超过此时间无响应则输出警告")
            .comment("Watchdog warning threshold (seconds) - warn if server unresponsive for this long")
            .defineInRange("watchdogWarnThreshold", 30, 10, 120);
        WATCHDOG_CRITICAL_THRESHOLD = builder
            .comment("看门狗严重阈值（秒）- 超过此时间输出线程堆栈用于死锁分析")
            .comment("Watchdog critical threshold (seconds) - dump thread stack for deadlock analysis")
            .defineInRange("watchdogCriticalThreshold", 60, 30, 300);
        builder.pop();
    }
}
