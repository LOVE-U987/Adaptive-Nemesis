package com.adaptive_nemesis.adaptive_nemesismod.watchdog;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 看门狗服务 - 监控服务端线程卡死/死锁
 *
 * 在独立守护线程中定期检测服务端主线程是否长时间无响应。
 * 当检测到服务端卡死时，记录当前正在处理的实体信息和完整线程堆栈，
 * 帮助排查形如 Integrated Cataclysm 结构生成导致的死锁问题。
 *
 * 使用方式：
 * - 主类在 ServerStartedEvent 中按配置开关与侧别（仅专用服务器）启动本服务
 * - ModEventHandler.onServerStopping 负责停止，专用服务器重启后可再次启动
 * - EnemyScalingHandler 在缩放实体前后注入处理状态
 * - ModEventHandler 在 ServerTickEvent 中更新 tick 时间戳
 * - BossDamageCapHandler 在处理 Boss buff 时注入状态
 *
 * 武装机制（armed）：服务启动到服务端首个 tick 之间没有任何活动时间戳参考，
 * 守护循环在该阶段只休眠不判定，避免启动加载期误报。
 *
 * 看门狗本身不会影响游戏性能，其守护线程处于 WAITING 状态的大部分时间，
 * 仅在每 CHECK_INTERVAL_MS 毫秒唤醒一次做简单的时间戳比较。
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class WatchdogService {

    /** 实体 UUID 状态未知的占位值 */
    private static final String UUID_UNKNOWN = "N/A";

    /** 单例实例 */
    private static WatchdogService INSTANCE;

    /** 看门狗守护线程 */
    private Thread watchdogThread;

    /** 服务是否正在运行 */
    private volatile boolean running = false;

    /**
     * 武装标志：服务端首个 updateServerTick 到达后置为 true，
     * 之前守护循环只休眠不判定（启动加载期无时间戳参考，判定必误报）
     */
    private volatile boolean armed = false;

    /** 服务端主线程 ID - 由首次 tick 事件捕获 */
    private volatile long serverThreadId = -1;

    /** 上次活跃时间戳（纳秒）- 由 tick 事件或实体处理事件更新 */
    private final AtomicLong lastActivityNanos = new AtomicLong(System.nanoTime());

    /** 上次被处理的实体 UUID（字符串形式）- 问题实体键的稳定依据 */
    private final AtomicReference<String> currentEntityUuid = new AtomicReference<>(UUID_UNKNOWN);

    /** 上次被处理的实体名称（仅用于日志展示） */
    private final AtomicReference<String> currentEntityName = new AtomicReference<>("N/A");

    /** 上次被处理的实体类型 ID */
    private final AtomicReference<String> currentEntityType = new AtomicReference<>("N/A");

    /** 当前处理阶段 */
    private final AtomicReference<String> currentStage = new AtomicReference<>("idle");

    /** 上次被处理的实体坐标（仅用于日志展示） */
    private final AtomicReference<String> currentEntityPos = new AtomicReference<>("N/A");

    /** 上次被处理的实体维度 */
    private final AtomicReference<String> currentEntityDim = new AtomicReference<>("N/A");

    /** 看门狗检查间隔（毫秒）- 从配置读取 */
    private volatile long checkIntervalMs = 5_000;

    /** 警告阈值（毫秒）- 从配置读取 */
    private volatile long warnThresholdMs = 30_000;

    /** 严重阈值（毫秒）- 从配置读取 */
    private volatile long criticalThresholdMs = 60_000;

    /** 线程转储最小间隔（毫秒）- 避免同一卡死事件刷屏 */
    private static final long DUMP_COOLDOWN_MS = 120_000;

    /** 上次输出线程转储的时间戳（纳秒） */
    private final AtomicLong lastDumpNanos = new AtomicLong(0);

    /** 是否曾被报告为卡死状态（用于恢复正常时输出恢复日志） */
    private volatile boolean wasStuck = false;

    /**
     * 卡死期间的峰值无响应时长（毫秒）
     * 用于恢复消息显示真实的卡死时长：恢复瞬间读取的当前间隔
     * 不是实际卡死时长，该字段保存卡死期间的峰值。
     */
    private final AtomicLong peakStuckMs = new AtomicLong(0);

    /**
     * 问题实体键集 — 在卡死期间被标记为"可能引发卡死"的实体。
     * 键以实体 UUID 字符串为主（身份稳定，不受坐标快照漂移影响），
     * 同时保留"名称@坐标"兼容键，与 EnemyScalingHandler 现行查询键匹配。
     * EnemyScalingHandler 在处理实体前会检查此集合，
     * 如果当前实体的键在集合中，则跳过缩放处理，避免同一实体反复触发区块加载死锁。
     * 集合会在服务端恢复后清空，避免永久跳过。
     */
    private final Set<String> problematicEntities = Collections.synchronizedSet(new HashSet<>());

    /**
     * 私有构造函数 - 单例模式
     */
    private WatchdogService() {}

    /**
     * 获取单例实例
     *
     * @return WatchdogService 实例
     */
    public static synchronized WatchdogService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new WatchdogService();
        }
        return INSTANCE;
    }

    /**
     * 启动看门狗服务
     * 创建并启动守护线程，开始周期性检查服务端活动状态。
     * 由主类在 ServerStartedEvent 中按配置开关与侧别（仅专用服务器）调用，
     * 服务器重启后可重复调用以恢复工作。
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        // 重置上一轮服务端的运行状态：等待武装、重新捕获线程 ID、清空问题实体标记
        armed = false;
        serverThreadId = -1;
        problematicEntities.clear();
        lastActivityNanos.set(System.nanoTime());
        lastDumpNanos.set(0);
        wasStuck = false;
        peakStuckMs.set(0);

        // 从配置读取阈值并转换为毫秒（配置文件中单位为秒）
        checkIntervalMs = Config.WATCHDOG_CHECK_INTERVAL.get() * 1_000L;
        warnThresholdMs = Config.WATCHDOG_WARN_THRESHOLD.get() * 1_000L;
        criticalThresholdMs = Config.WATCHDOG_CRITICAL_THRESHOLD.get() * 1_000L;

        // 校验阈值关系，防止配置错误导致看门狗失效
        if (warnThresholdMs >= criticalThresholdMs) {
            AdaptiveNemesisMod.LOGGER.warn(
                "看门狗配置异常：警告阈值({}ms) >= 严重阈值({}ms)，已自动将严重阈值调整为警告阈值+30秒",
                warnThresholdMs, criticalThresholdMs
            );
            criticalThresholdMs = warnThresholdMs + 30_000L;
        }
        if (checkIntervalMs > warnThresholdMs) {
            AdaptiveNemesisMod.LOGGER.warn(
                "看门狗配置异常：检查间隔({}ms) > 警告阈值({}ms)，可能导致无法及时检测，已自动调整检查间隔",
                checkIntervalMs, warnThresholdMs
            );
            checkIntervalMs = Math.max(1_000L, warnThresholdMs / 5);
        }

        watchdogThread = new Thread(this::watchdogLoop, "AN-Watchdog");
        watchdogThread.setDaemon(true);
        watchdogThread.setPriority(Thread.MIN_PRIORITY);
        watchdogThread.start();

        AdaptiveNemesisMod.LOGGER.info("看门狗服务已启动（检查间隔={}ms, 警告阈值={}ms, 严重阈值={}ms）",
            checkIntervalMs, warnThresholdMs, criticalThresholdMs);
    }

    /**
     * 停止看门狗服务
     */
    public synchronized void stop() {
        running = false;
        if (watchdogThread != null) {
            watchdogThread.interrupt();
            watchdogThread = null;
        }
        AdaptiveNemesisMod.LOGGER.info("看门狗服务已停止");
    }

    /**
     * 看门狗主循环 - 在守护线程中运行
     *
     * 周期性检查服务端活跃时间戳，判断是否发生卡死。
     * 未武装（服务端尚未产生首个 tick）时只休眠不判定；
     * 卡死状态分为两级：WARN（记录实体处理信息）与 CRITICAL（输出线程堆栈）。
     */
    private void watchdogLoop() {
        while (running) {
            try {
                Thread.sleep(checkIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (!running) {
                break;
            }

            // 未武装阶段：服务端尚未产生首个 tick，无有效参考时间戳，只休眠不判定
            if (!armed) {
                continue;
            }

            long now = System.nanoTime();
            long elapsedMs = (now - lastActivityNanos.get()) / 1_000_000;

            if (elapsedMs < warnThresholdMs) {
                handleRecovered(elapsedMs);
            } else {
                handleStuck(now, elapsedMs);
            }
        }
    }

    /**
     * 处理服务端恢复正常的状态
     * 输出恢复日志（含卡死峰值时长）并清空问题实体标记，
     * 允许下一轮正常处理曾被标记的实体
     *
     * @param elapsedMs 当前无响应时长（毫秒），卡死峰值未记录时的兜底值
     */
    private void handleRecovered(long elapsedMs) {
        if (!wasStuck) {
            return;
        }
        wasStuck = false;
        long stuckDuration = peakStuckMs.getAndSet(0);
        // 清空问题实体集，允许下一轮正常处理
        problematicEntities.clear();
        AdaptiveNemesisMod.LOGGER.info(
            "看门狗：服务端已恢复运行，上次卡死持续约 {}ms",
            stuckDuration > 0 ? stuckDuration : elapsedMs
        );
    }

    /**
     * 处理检测到服务端长时间无活动的状态
     * 更新峰值记录，按严重程度分级输出警告或线程转储
     *
     * @param now       当前纳秒时间戳
     * @param elapsedMs 无响应时长（毫秒）
     */
    private void handleStuck(long now, long elapsedMs) {
        wasStuck = true;
        if (elapsedMs > peakStuckMs.get()) {
            peakStuckMs.set(elapsedMs);
        }

        if (elapsedMs < criticalThresholdMs) {
            // 一级警告：记录实体处理信息
            AdaptiveNemesisMod.LOGGER.warn(
                "[看门狗-警告] 服务端已 {}ms 无响应！当前处理: 实体={}({}), UUID={}, 阶段={}, 位置={}, 维度={}",
                elapsedMs, currentEntityName.get(), currentEntityType.get(),
                currentEntityUuid.get(), currentStage.get(),
                currentEntityPos.get(), currentEntityDim.get()
            );
            return;
        }

        // 二级严重：记录错误并输出线程堆栈 + 锁信息
        AdaptiveNemesisMod.LOGGER.error(
            "[看门狗-严重] 服务端已 {}ms 无响应（> {}ms 严重阈值）！当前处理: 实体={}({}), UUID={}, 阶段={}, 位置={}, 维度={}",
            elapsedMs, criticalThresholdMs,
            currentEntityName.get(), currentEntityType.get(), currentEntityUuid.get(),
            currentStage.get(), currentEntityPos.get(), currentEntityDim.get()
        );

        markCurrentEntityProblematic();
        dumpWithCooldown(now);
    }

    /**
     * 将当前处理实体标记为问题实体
     *
     * 以实体 UUID 字符串为主键（身份稳定，不受坐标快照漂移影响）；
     * 同时写入"名称@坐标"兼容键，保持与 EnemyScalingHandler 现行查询键一致；
     * 名称与坐标本身仅用于日志展示。
     */
    private void markCurrentEntityProblematic() {
        // UUID 键：上游注入实体 UUID 后即可精确匹配
        String uuidKey = currentEntityUuid.get();
        if (!UUID_UNKNOWN.equals(uuidKey)) {
            problematicEntities.add(uuidKey);
        }
        // 兼容键：与 EnemyScalingHandler.makeEntityKey 的"名称@坐标"格式对齐
        problematicEntities.add(currentEntityName.get() + "@" + currentEntityPos.get());
    }

    /**
     * 在冷却时间允许时输出线程转储
     * 避免同一卡死事件刷屏日志
     *
     * @param now 当前纳秒时间戳
     */
    private void dumpWithCooldown(long now) {
        long sinceLastDumpMs = (now - lastDumpNanos.get()) / 1_000_000;
        if (sinceLastDumpMs >= DUMP_COOLDOWN_MS) {
            lastDumpNanos.set(now);
            dumpServerThread();
        }
    }

    /**
     * 输出所有关键线程的完整堆栈信息
     *
     * 使用 ThreadMXBean 获取包含锁信息的详细线程信息，
     * 按重要性分类输出：死锁线程、服务端主线程、BLOCKED 线程、
     * RUNNABLE 线程、WorldGen/Chunk 加载线程。
     */
    private void dumpServerThread() {
        try {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

            // 获取包含锁信息的详细线程信息
            ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(
                threadMXBean.getAllThreadIds(),
                true,  // 包含锁监视器信息
                true   // 包含同步器信息
            );

            AdaptiveNemesisMod.LOGGER.error("===== [看门狗-线程转储] 开始 =====");
            AdaptiveNemesisMod.LOGGER.error(" 总线程数={}", threadInfos.length);

            logDeadlockedThreads(threadMXBean, threadInfos);

            ThreadCategories categories = classifyThreads(threadInfos);
            logServerThreadInfo(threadMXBean, threadInfos);
            logBlockedThreads(categories.blockedThreads);
            logRunnableThreads(categories.runnableThreads);
            logWorldGenThreads(categories.worldgenThreads);
            logChunkLoadThreads(categories.chunkLoadThreads);

            AdaptiveNemesisMod.LOGGER.error("===== [看门狗-线程转储] 结束 =====");
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("获取线程转储时发生异常: {}", e.getMessage());
            dumpAllThreadsFallback();
        }
    }

    /**
     * 输出处于 Java 级死锁状态的线程信息
     *
     * @param threadMXBean 线程管理 Bean
     * @param threadInfos  全量线程信息
     */
    private void logDeadlockedThreads(ThreadMXBean threadMXBean, ThreadInfo[] threadInfos) {
        long[] deadlockedIds = threadMXBean.findDeadlockedThreads();
        if (deadlockedIds == null || deadlockedIds.length == 0) {
            AdaptiveNemesisMod.LOGGER.error("  [死锁检测] 未检测到 Java 级别死锁");
            return;
        }

        AdaptiveNemesisMod.LOGGER.error("  [死锁检测] 检测到 {} 个线程处于死锁状态！",
            deadlockedIds.length);
        for (long id : deadlockedIds) {
            for (ThreadInfo info : threadInfos) {
                if (info != null && info.getThreadId() == id) {
                    AdaptiveNemesisMod.LOGGER.error(
                        "  [死锁线程] {} (ID={}), 状态={}",
                        info.getThreadName(), id, info.getThreadState()
                    );
                    printThreadInfo(info);
                }
            }
        }
    }

    /**
     * 线程分类结果容器
     * 供转储流程按类别输出使用
     */
    private static class ThreadCategories {
        /** 服务端主线程信息（最多一条） */
        final List<ThreadInfo> serverThreads = new ArrayList<>();
        /** BLOCKED 线程（可能在等锁） */
        final List<ThreadInfo> blockedThreads = new ArrayList<>();
        /** RUNNABLE 线程（可能在执行耗时工作） */
        final List<ThreadInfo> runnableThreads = new ArrayList<>();
        /** worldgen 线程（结构生成死锁的关键线索） */
        final List<ThreadInfo> worldgenThreads = new ArrayList<>();
        /** 区块加载相关线程 */
        final List<ThreadInfo> chunkLoadThreads = new ArrayList<>();
    }

    /**
     * 将全量线程信息按类别分组
     *
     * @param threadInfos 全量线程信息
     * @return 分类结果
     */
    private ThreadCategories classifyThreads(ThreadInfo[] threadInfos) {
        ThreadCategories categories = new ThreadCategories();
        for (ThreadInfo info : threadInfos) {
            if (info == null) {
                continue;
            }
            if (info.getThreadId() == serverThreadId) {
                categories.serverThreads.add(info);
            } else if (isWorldGenThread(info)) {
                categories.worldgenThreads.add(info);
            } else if (isChunkLoadThread(info)) {
                categories.chunkLoadThreads.add(info);
            } else if (info.getThreadState() == Thread.State.BLOCKED) {
                categories.blockedThreads.add(info);
            } else if (info.getThreadState() == Thread.State.RUNNABLE) {
                categories.runnableThreads.add(info);
            }
        }
        return categories;
    }

    /**
     * 判断是否为 worldgen 线程（结构生成死锁的关键线索）
     *
     * @param info 线程信息
     * @return 如果是 worldgen 线程返回 true
     */
    private boolean isWorldGenThread(ThreadInfo info) {
        String name = info.getThreadName().toLowerCase();
        return name.contains("worldgen") || name.contains("world-gen");
    }

    /**
     * 判断是否为区块加载相关线程
     *
     * @param info 线程信息
     * @return 如果是区块加载相关线程返回 true
     */
    private boolean isChunkLoadThread(ThreadInfo info) {
        String name = info.getThreadName().toLowerCase();
        if (name.contains("chunk") || name.contains("worker") || name.contains("pool")) {
            return true;
        }
        return info.getThreadState() == Thread.State.RUNNABLE
            && stackContainsAny(info.getStackTrace(), "Chunk", "chunk", "WorldGen", "Structure");
    }

    /**
     * 输出服务端主线程的转储信息
     *
     * @param threadMXBean 线程管理 Bean
     * @param threadInfos  全量线程信息
     */
    private void logServerThreadInfo(ThreadMXBean threadMXBean, ThreadInfo[] threadInfos) {
        if (serverThreadId == -1) {
            AdaptiveNemesisMod.LOGGER.error("  [服务端线程] 尚未捕获到服务端线程ID");
            return;
        }
        for (ThreadInfo info : threadInfos) {
            if (info == null || info.getThreadId() != serverThreadId) {
                continue;
            }
            long cpuTime;
            try {
                cpuTime = threadMXBean.getThreadCpuTime(serverThreadId);
            } catch (Exception e) {
                cpuTime = 0;
            }
            AdaptiveNemesisMod.LOGGER.error(
                "  [服务端线程] {} (ID={}), 状态={}, CPU时间={}ns",
                info.getThreadName(), info.getThreadId(),
                info.getThreadState(), cpuTime
            );
            printThreadInfo(info);
            return;
        }
    }

    /**
     * 输出 BLOCKED 线程组（可能被阻塞在锁上）
     *
     * @param blockedThreads BLOCKED 线程列表
     */
    private void logBlockedThreads(List<ThreadInfo> blockedThreads) {
        if (blockedThreads.isEmpty()) {
            return;
        }
        AdaptiveNemesisMod.LOGGER.error("  [BLOCKED线程] {} 个线程被阻塞:", blockedThreads.size());
        for (ThreadInfo info : blockedThreads) {
            AdaptiveNemesisMod.LOGGER.error(
                "  {} (ID={}), 状态={}",
                info.getThreadName(), info.getThreadId(), info.getThreadState()
            );
            if (info.getLockInfo() != null) {
                AdaptiveNemesisMod.LOGGER.error("      等待锁: {}", info.getLockInfo());
            }
            if (info.getLockOwnerName() != null) {
                AdaptiveNemesisMod.LOGGER.error("      锁拥有者: {} (ID={})",
                    info.getLockOwnerName(), info.getLockOwnerId());
            }
            printStackTraceBrief(info.getStackTrace(), 5);
        }
    }

    /**
     * 输出 RUNNABLE 线程组（可能正在做耗时工作）
     *
     * @param runnableThreads RUNNABLE 线程列表
     */
    private void logRunnableThreads(List<ThreadInfo> runnableThreads) {
        if (runnableThreads.isEmpty()) {
            return;
        }
        AdaptiveNemesisMod.LOGGER.error("  [RUNNABLE线程] {} 个线程:", runnableThreads.size());
        for (ThreadInfo info : runnableThreads) {
            AdaptiveNemesisMod.LOGGER.error(
                "  {} (ID={})",
                info.getThreadName(), info.getThreadId()
            );
            printStackTraceBrief(info.getStackTrace(), 3);
        }
    }

    /**
     * 输出 WorldGen 线程组
     *
     * @param worldgenThreads worldgen 线程列表
     */
    private void logWorldGenThreads(List<ThreadInfo> worldgenThreads) {
        if (worldgenThreads.isEmpty()) {
            return;
        }
        AdaptiveNemesisMod.LOGGER.error("  [WorldGen线程] {} 个线程:", worldgenThreads.size());
        for (ThreadInfo info : worldgenThreads) {
            AdaptiveNemesisMod.LOGGER.error(
                "  {} (ID={}), 状态={}",
                info.getThreadName(), info.getThreadId(), info.getThreadState()
            );
            printThreadInfo(info);
        }
    }

    /**
     * 输出区块加载相关线程组
     *
     * @param chunkLoadThreads 区块加载线程列表
     */
    private void logChunkLoadThreads(List<ThreadInfo> chunkLoadThreads) {
        if (chunkLoadThreads.isEmpty()) {
            return;
        }
        AdaptiveNemesisMod.LOGGER.error("  [区块加载线程] {} 个线程:", chunkLoadThreads.size());
        for (ThreadInfo info : chunkLoadThreads) {
            AdaptiveNemesisMod.LOGGER.error(
                "  {} (ID={}), 状态={}",
                info.getThreadName(), info.getThreadId(), info.getThreadState()
            );
            printThreadInfo(info);
        }
    }

    /**
     * 转储失败时的回退方案：使用 Thread.getAllStackTraces() 输出全量堆栈
     */
    private void dumpAllThreadsFallback() {
        AdaptiveNemesisMod.LOGGER.error("===== [看门狗-线程转储-回退] 开始 =====");
        for (var entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            StackTraceElement[] stack = entry.getValue();
            AdaptiveNemesisMod.LOGGER.error("线程: {} (ID={}, 状态={})",
                thread.getName(), thread.getId(), thread.getState());
            for (StackTraceElement element : stack) {
                AdaptiveNemesisMod.LOGGER.error("  at {}", element);
            }
        }
        AdaptiveNemesisMod.LOGGER.error("===== [看门狗-线程转储-回退] 结束 =====");
    }

    /**
     * 检查堆栈中是否包含任一关键字
     *
     * @param stack  堆栈跟踪
     * @param keywords 关键字列表
     * @return 如果包含任一关键字返回 true
     */
    private boolean stackContainsAny(StackTraceElement[] stack, String... keywords) {
        if (stack == null) return false;
        for (StackTraceElement element : stack) {
            String line = element.toString();
            for (String kw : keywords) {
                if (line.contains(kw)) return true;
            }
        }
        return false;
    }

    /**
     * 打印堆栈跟踪摘要（限制行数）
     *
     * @param stack 堆栈跟踪
     * @param maxLines 最大行数
     */
    private void printStackTraceBrief(StackTraceElement[] stack, int maxLines) {
        if (stack == null || stack.length == 0) return;
        int lines = Math.min(stack.length, maxLines);
        for (int i = 0; i < lines; i++) {
            AdaptiveNemesisMod.LOGGER.error("      at {}", stack[i]);
        }
        if (stack.length > maxLines) {
            AdaptiveNemesisMod.LOGGER.error("      ... (共 {} 行)", stack.length);
        }
    }

    /**
     * 打印单条线程的详细信息
     * 包括堆栈、持有的锁、等待的锁等
     *
     * @param info 线程信息
     */
    private void printThreadInfo(ThreadInfo info) {
        // 堆栈跟踪
        StackTraceElement[] stack = info.getStackTrace();
        if (stack != null && stack.length > 0) {
            int maxLines = Math.min(stack.length, 30); // 最多输出30行堆栈
            for (int i = 0; i < maxLines; i++) {
                AdaptiveNemesisMod.LOGGER.error("    at {}", stack[i]);
            }
            if (stack.length > maxLines) {
                AdaptiveNemesisMod.LOGGER.error("    ... (省略 {} 行)", stack.length - maxLines);
            }
        }

        // 持有的锁
        if (info.getLockInfo() != null) {
            AdaptiveNemesisMod.LOGGER.error("    锁信息: {}", info.getLockInfo());
        }
        if (info.getLockedMonitors() != null && info.getLockedMonitors().length > 0) {
            AdaptiveNemesisMod.LOGGER.error("    持有的锁监视器 ({}个):", info.getLockedMonitors().length);
            for (var monitor : info.getLockedMonitors()) {
                AdaptiveNemesisMod.LOGGER.error("      {} (堆栈深度: {})", monitor, monitor.getLockedStackDepth());
            }
        }
        if (info.getLockedSynchronizers() != null && info.getLockedSynchronizers().length > 0) {
            AdaptiveNemesisMod.LOGGER.error("    持有的同步器 ({}个):", info.getLockedSynchronizers().length);
            for (var sync : info.getLockedSynchronizers()) {
                AdaptiveNemesisMod.LOGGER.error("      {}", sync);
            }
        }
    }

    // ==================== 状态注入方法 ====================

    /**
     * 更新服务端 tick 时间戳
     * 由 ModEventHandler.onServerTick() 在每个 tick 结束时调用，
     * 用于看门狗判断服务端是否仍然在进行正常 tick。
     * 首次调用完成武装（armed 置 true），之后守护循环才开始卡死判定。
     */
    public void updateServerTick() {
        if (serverThreadId == -1) {
            serverThreadId = Thread.currentThread().getId();
        }
        armed = true;
        lastActivityNanos.set(System.nanoTime());
    }

    /**
     * 注入当前实体处理状态（不带 UUID 的兼容重载）
     * UUID 状态保持为未知占位，问题实体标记将退化为"名称@坐标"兼容键
     *
     * @param entityName 实体名称
     * @param entityType 实体类型 ID
     * @param stage      当前处理阶段（如 "applyScaling", "applyHealthBonus" 等）
     * @param pos        实体坐标（格式如 "[x, y, z]"）
     * @param dimension  实体所在维度
     */
    public void updateEntityProcessing(
            String entityName, String entityType, String stage,
            String pos, String dimension) {
        updateEntityProcessing(UUID_UNKNOWN, entityName, entityType, stage, pos, dimension);
    }

    /**
     * 注入当前实体处理状态（带 UUID，问题实体标记使用稳定键）
     *
     * @param entityUuid 实体 UUID 字符串
     * @param entityName 实体名称
     * @param entityType 实体类型 ID
     * @param stage      当前处理阶段（如 "applyScaling", "applyHealthBonus" 等）
     * @param pos        实体坐标（格式如 "[x, y, z]"）
     * @param dimension  实体所在维度
     */
    public void updateEntityProcessing(
            String entityUuid, String entityName, String entityType, String stage,
            String pos, String dimension) {
        currentEntityUuid.set(entityUuid);
        currentEntityName.set(entityName);
        currentEntityType.set(entityType);
        currentStage.set(stage);
        currentEntityPos.set(pos);
        currentEntityDim.set(dimension);
        lastActivityNanos.set(System.nanoTime());
    }

    /**
     * 注入 Boss buff 处理状态（不带 UUID 的兼容重载）
     * UUID 状态保持为未知占位，问题实体标记将退化为"名称@坐标"兼容键
     *
     * @param bossName  Boss 名称
     * @param bossType  Boss 类型 ID
     * @param pos       Boss 坐标
     * @param dimension Boss 所在维度
     * @param stage     buff 处理阶段（如 "applyHealthBuff", "applyDamageBuff"）
     */
    public void updateBossBuffProcessing(
            String bossName, String bossType, String pos,
            String dimension, String stage) {
        updateBossBuffProcessing(UUID_UNKNOWN, bossName, bossType, pos, dimension, stage);
    }

    /**
     * 注入 Boss buff 处理状态（带 UUID，问题实体标记使用稳定键）
     *
     * @param bossUuid  Boss 实体 UUID 字符串
     * @param bossName  Boss 名称
     * @param bossType  Boss 类型 ID
     * @param pos       Boss 坐标
     * @param dimension Boss 所在维度
     * @param stage     buff 处理阶段（如 "applyHealthBuff", "applyDamageBuff"）
     */
    public void updateBossBuffProcessing(
            String bossUuid, String bossName, String bossType, String pos,
            String dimension, String stage) {
        currentEntityUuid.set(bossUuid);
        currentEntityName.set(bossName);
        currentEntityType.set(bossType);
        currentStage.set("boss_buff:" + stage);
        currentEntityPos.set(pos);
        currentEntityDim.set(dimension);
        lastActivityNanos.set(System.nanoTime());
    }

    /**
     * 更新当前处理阶段（不更新实体信息）
     * 用于不需要更新实体但需要标记当前阶段的场景
     *
     * @param stage 当前阶段名称
     */
    public void updateStage(String stage) {
        currentStage.set(stage);
        lastActivityNanos.set(System.nanoTime());
    }

    // ==================== 问题实体管理 ====================

    /**
     * 检查指定实体是否被看门狗标记为"问题实体"
     * 被标记的实体在前一轮处理中可能导致服务端卡死，
     * EnemyScalingHandler 应跳过其缩放处理。
     *
     * @param entityKey 实体标识键（UUID 字符串，或"名称@[x, y, z]"兼容键）
     * @return 如果该实体被标记为问题实体返回 true
     */
    public boolean isEntityProblematic(String entityKey) {
        return problematicEntities.contains(entityKey);
    }

    /**
     * 手动将某个实体标记为问题实体
     * 当 EnemyScalingHandler 检测到实体处理超时或异常时调用
     *
     * @param entityKey 实体标识键
     */
    public void markEntityProblematic(String entityKey) {
        problematicEntities.add(entityKey);
        AdaptiveNemesisMod.LOGGER.warn(
            "看门狗将实体 {} 标记为问题实体，下次将跳过缩放处理",
            entityKey
        );
    }

    /**
     * 获取当前实体的去重标识键
     * 用于在问题实体集中查找和比较
     *
     * @param entityName 实体名称
     * @param x          X 坐标
     * @param y          Y 坐标
     * @param z          Z 坐标
     * @return 标准化实体标识键（"名称@[x, y, z]"兼容格式）
     */
    public static String makeEntityKey(String entityName, double x, double y, double z) {
        return entityName + "@[" + Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z) + "]";
    }
}
