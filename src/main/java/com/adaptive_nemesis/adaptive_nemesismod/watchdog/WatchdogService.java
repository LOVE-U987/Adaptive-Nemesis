package com.adaptive_nemesis.adaptive_nemesismod.watchdog;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
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
 * - EnemyScalingHandler 在缩放实体前后注入处理状态
 * - ModEventHandler 在 ServerTickEvent 中更新 tick 时间戳
 * - BossDamageCapHandler 在处理 Boss buff 时注入状态
 * 
 * 看门狗本身不会影响游戏性能，其守护线程处于 WAITING 状态的大部分时间，
 * 仅在每 CHECK_INTERVAL_MS 毫秒唤醒一次做简单的时间戳比较。
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class WatchdogService {

    /** 单例实例 */
    private static WatchdogService INSTANCE;

    /** 看门狗守护线程 */
    private Thread watchdogThread;

    /** 服务是否正在运行 */
    private volatile boolean running = false;

    /** 服务端主线程 ID - 由首次 tick 事件捕获 */
    private volatile long serverThreadId = -1;

    /** 上次活跃时间戳（纳秒）- 由 tick 事件或实体处理事件更新 */
    private final AtomicLong lastActivityNanos = new AtomicLong(System.nanoTime());

    /** 上次被处理的实体名称 */
    private final AtomicReference<String> currentEntityName = new AtomicReference<>("N/A");

    /** 上次被处理的实体类型 ID */
    private final AtomicReference<String> currentEntityType = new AtomicReference<>("N/A");

    /** 当前处理阶段 */
    private final AtomicReference<String> currentStage = new AtomicReference<>("idle");

    /** 上次被处理的实体坐标 */
    private final AtomicReference<String> currentEntityPos = new AtomicReference<>("N/A");

    /** 上次被处理的实体维度 */
    private final AtomicReference<String> currentEntityDim = new AtomicReference<>("N/A");

    /** 看门狗检查间隔（毫秒） */
    private static final long CHECK_INTERVAL_MS = 5_000;

    /** 警告阈值（毫秒）- 超过此时间无活动则记录警告日志 */
    private static final long WARN_THRESHOLD_MS = 30_000;

    /** 严重阈值（毫秒）- 超过此时间无活动则记录完整线程堆栈 */
    private static final long CRITICAL_THRESHOLD_MS = 60_000;

    /** 线程转储最小间隔（毫秒）- 避免同一卡死事件刷屏 */
    private static final long DUMP_COOLDOWN_MS = 120_000;

    /** 上次输出线程转储的时间戳（纳秒） */
    private final AtomicLong lastDumpNanos = new AtomicLong(0);

    /** 是否曾被报告为卡死状态（用于恢复正常时输出恢复日志） */
    private volatile boolean wasStuck = false;

    /** 
     * 卡死期间的峰值无响应时长（毫秒） 
     * 用于修复「恢复运行」消息显示的时长错误 — 
     * 原来的代码在恢复后读取当前 elapsedMs，那只是恢复后的瞬间间隔，
     * 而不是实际卡死时长。这个字段保存卡死期间的峰值。
     */
    private final AtomicLong peakStuckMs = new AtomicLong(0);

    /**
     * 问题实体 UUID 集 — 在卡死期间被标记为"可能引发卡死"的实体。
     * EnemyScalingHandler 在处理实体前会检查此集合，
     * 如果发现当前实体的 UUID 在其中，则跳过缩放处理，
     * 避免同一实体反复触发区块加载死锁。
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
     * 创建并启动守护线程，开始周期性检查服务端活动状态
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;

        watchdogThread = new Thread(this::watchdogLoop, "AN-Watchdog");
        watchdogThread.setDaemon(true);
        watchdogThread.setPriority(Thread.MIN_PRIORITY);
        watchdogThread.start();

        AdaptiveNemesisMod.LOGGER.info("🐕 看门狗服务已启动（检查间隔={}ms, 警告阈值={}ms）",
            CHECK_INTERVAL_MS, WARN_THRESHOLD_MS);
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
        AdaptiveNemesisMod.LOGGER.info("🐕 看门狗服务已停止");
    }

    /**
     * 看门狗主循环 - 在守护线程中运行
     * 
     * 周期性检查服务端活跃时间戳，判断是否发生卡死。
     * 卡死状态分为两级：
     * 1. WARN（30s）：记录实体处理信息，提示可能卡死
     * 2. CRITICAL（60s）：记录完整线程堆栈，用于死锁分析
     * 
     * 当服务端恢复正常运行时，输出恢复日志。
     */
    private void watchdogLoop() {
        while (running) {
            try {
                Thread.sleep(CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (!running) break;

            long now = System.nanoTime();
            long elapsedMs = (now - lastActivityNanos.get()) / 1_000_000;

            if (elapsedMs < WARN_THRESHOLD_MS) {
                // 正常状态：如果之前卡死过，输出恢复日志
                if (wasStuck) {
                    wasStuck = false;
                    long stuckDuration = peakStuckMs.getAndSet(0);
                    // 清空问题实体集，允许下一轮正常处理
                    problematicEntities.clear();
                    AdaptiveNemesisMod.LOGGER.info(
                        "🐕 服务端已恢复运行，上次卡死持续约 {}ms",
                        stuckDuration > 0 ? stuckDuration : elapsedMs
                    );
                }
                continue;
            }

            // ===== 检测到服务端长时间无活动 =====
            wasStuck = true;
            // 更新峰值记录（用于恢复消息显示真实卡死时长）
            if (elapsedMs > peakStuckMs.get()) {
                peakStuckMs.set(elapsedMs);
            }

            String entityName = currentEntityName.get();
            String entityType = currentEntityType.get();
            String stage = currentStage.get();
            String entityPos = currentEntityPos.get();
            String entityDim = currentEntityDim.get();

            if (elapsedMs < CRITICAL_THRESHOLD_MS) {
                // 一级警告（30s-60s）：记录实体处理信息
                AdaptiveNemesisMod.LOGGER.warn(
                    "🐕 [看门狗-警告] 服务端已 {}ms 无响应！" +
                    "当前处理: 实体={}({}), 阶段={}, 位置={}, 维度={}",
                    elapsedMs, entityName, entityType, stage, entityPos, entityDim
                );
            } else {
                // 二级严重（60s+）：输出线程堆栈 + 锁信息
                long lastDump = lastDumpNanos.get();
                long sinceLastDumpMs = (now - lastDump) / 1_000_000;

                // 记录严重警告（无论是否输出线程转储都记录）
                AdaptiveNemesisMod.LOGGER.error(
                    "🐕 [看门狗-严重] 服务端已 {}ms 无响应（> {}ms 严重阈值）！" +
                    "当前处理: 实体={}({}), 阶段={}, 位置={}, 维度={}",
                    elapsedMs, CRITICAL_THRESHOLD_MS,
                    entityName, entityType, stage, entityPos, entityDim
                );

                // 标记当前实体为问题实体 — EnemyScalingHandler 会据此跳过该实体的缩放
                // 统一使用 makeEntityKey 生成实体键，确保与 EnemyScalingHandler 的匹配逻辑一致
                String entityKey;
                try {
                    // entityPos 格式为 "[x, y, z]"，解析坐标值后调用 makeEntityKey
                    String posContent = entityPos.substring(1, entityPos.length() - 1);
                    String[] coords = posContent.split(",\\s*");
                    entityKey = makeEntityKey(entityName,
                        Double.parseDouble(coords[0]),
                        Double.parseDouble(coords[1]),
                        Double.parseDouble(coords[2]));
                } catch (Exception e) {
                    // 解析失败时回退到原始拼接方式
                    entityKey = entityName + "@" + entityPos;
                }
                problematicEntities.add(entityKey);

                // 线程转储冷却：避免同一卡死事件刷屏日志
                if (sinceLastDumpMs >= DUMP_COOLDOWN_MS) {
                    lastDumpNanos.set(now);
                    dumpServerThread();
                }
            }
        }
    }

    /**
     * 输出所有关键线程的完整堆栈信息
     * 
     * 使用 ThreadMXBean 获取包含锁信息的详细线程信息，
     * 按重要性分类输出：
     * 1. 🔒 死锁线程
     * 2. 🎯 服务端主线程
     * 3. 🔴 BLOCKED 线程（可能被阻塞在锁上）
     * 4. 🟡 RUNNABLE 线程（可能正在做耗时工作）
     * 5. 🏗️ WorldGen/Chunk 加载线程（Cataclysm 死锁关键线索）
     * 6. 📋 其余线程摘要
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

            AdaptiveNemesisMod.LOGGER.error("===== 🐕 [看门狗-线程转储] 开始 =====");
            AdaptiveNemesisMod.LOGGER.error(" 总线程数={}", threadInfos.length);

            // ===== 1. 死锁检测 =====
            long[] deadlockedIds = threadMXBean.findDeadlockedThreads();
            if (deadlockedIds != null && deadlockedIds.length > 0) {
                AdaptiveNemesisMod.LOGGER.error("⚠️ [死锁检测] 检测到 {} 个线程处于死锁状态！",
                    deadlockedIds.length);
                for (long id : deadlockedIds) {
                    for (ThreadInfo info : threadInfos) {
                        if (info != null && info.getThreadId() == id) {
                            AdaptiveNemesisMod.LOGGER.error(
                                "  💀 [死锁线程] {} (ID={}), 状态={}",
                                info.getThreadName(), id, info.getThreadState()
                            );
                            printThreadInfo(info);
                        }
                    }
                }
            } else {
                AdaptiveNemesisMod.LOGGER.error("  ✅ 未检测到 Java 级别死锁");
            }

            // ===== 按类别收集线程 =====
            List<ThreadInfo> blockedThreads = new java.util.ArrayList<>();
            List<ThreadInfo> runnableThreads = new java.util.ArrayList<>();
            List<ThreadInfo> worldgenThreads = new java.util.ArrayList<>();
            List<ThreadInfo> chunkLoadThreads = new java.util.ArrayList<>();
            List<ThreadInfo> serverThreadInfo = new java.util.ArrayList<>();
            List<ThreadInfo> otherKeyThreads = new java.util.ArrayList<>();

            for (ThreadInfo info : threadInfos) {
                if (info == null) continue;
                String name = info.getThreadName().toLowerCase();

                // 服务端线程
                if (info.getThreadId() == serverThreadId) {
                    serverThreadInfo.add(info);
                }
                // worldgen 线程（Cataclysm 死锁关键线索）
                else if (name.contains("worldgen") || name.contains("world-gen")) {
                    worldgenThreads.add(info);
                }
                // 区块加载线程
                else if (name.contains("chunk") || name.contains("worker")
                    || name.contains("pool") || (info.getThreadState() == Thread.State.RUNNABLE
                    && stackContainsAny(info.getStackTrace(), "Chunk", "chunk", "WorldGen", "Structure"))) {
                    chunkLoadThreads.add(info);
                }
                // BLOCKED 线程（可能在等锁）
                else if (info.getThreadState() == Thread.State.BLOCKED) {
                    blockedThreads.add(info);
                }
                // RUNNABLE 线程（可能在干活）
                else if (info.getThreadState() == Thread.State.RUNNABLE) {
                    runnableThreads.add(info);
                }
            }

            // ===== 2. 服务端主线程 =====
            if (!serverThreadInfo.isEmpty()) {
                ThreadInfo st = serverThreadInfo.get(0);
                long cpuTime = 0;
                try {
                    cpuTime = threadMXBean.getThreadCpuTime(serverThreadId);
                } catch (Exception ignored) {}
                AdaptiveNemesisMod.LOGGER.error(
                    "  🎯 [服务端线程] {} (ID={}), 状态={}, CPU时间={}ns",
                    st.getThreadName(), st.getThreadId(),
                    st.getThreadState(), cpuTime
                );
                printThreadInfo(st);
            } else if (serverThreadId == -1) {
                AdaptiveNemesisMod.LOGGER.error("  ⚠️ 尚未捕获到服务端线程ID");
            }

            // ===== 3. BLOCKED 线程 =====
            if (!blockedThreads.isEmpty()) {
                AdaptiveNemesisMod.LOGGER.error("  🔴 [BLOCKED线程] {} 个线程被阻塞:", blockedThreads.size());
                for (ThreadInfo info : blockedThreads) {
                    AdaptiveNemesisMod.LOGGER.error(
                        "    🔴 {} (ID={}), 状态={}",
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

            // ===== 4. RUNNABLE 线程 =====
            if (!runnableThreads.isEmpty()) {
                AdaptiveNemesisMod.LOGGER.error("  🟡 [RUNNABLE线程] {} 个线程:", runnableThreads.size());
                for (ThreadInfo info : runnableThreads) {
                    AdaptiveNemesisMod.LOGGER.error(
                        "    🟡 {} (ID={})",
                        info.getThreadName(), info.getThreadId()
                    );
                    printStackTraceBrief(info.getStackTrace(), 3);
                }
            }

            // ===== 5. WorldGen 线程 =====
            if (!worldgenThreads.isEmpty()) {
                AdaptiveNemesisMod.LOGGER.error("  🏗️ [WorldGen线程] {} 个线程:", worldgenThreads.size());
                for (ThreadInfo info : worldgenThreads) {
                    AdaptiveNemesisMod.LOGGER.error(
                        "    🏗️ {} (ID={}), 状态={}",
                        info.getThreadName(), info.getThreadId(), info.getThreadState()
                    );
                    printThreadInfo(info);
                }
            }

            // ===== 6. 区块加载相关线程 =====
            if (!chunkLoadThreads.isEmpty()) {
                AdaptiveNemesisMod.LOGGER.error("  📦 [区块加载线程] {} 个线程:", chunkLoadThreads.size());
                for (ThreadInfo info : chunkLoadThreads) {
                    AdaptiveNemesisMod.LOGGER.error(
                        "    📦 {} (ID={}), 状态={}",
                        info.getThreadName(), info.getThreadId(), info.getThreadState()
                    );
                    printThreadInfo(info);
                }
            }

            AdaptiveNemesisMod.LOGGER.error("===== 🐕 [看门狗-线程转储] 结束 =====");

        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("🐕 获取线程转储时发生异常: {}", e.getMessage());
            // 回退方案：使用 Thread.getAllStackTraces()
            AdaptiveNemesisMod.LOGGER.error("===== 🐕 [看门狗-线程转储-回退] 开始 =====");
            for (var entry : Thread.getAllStackTraces().entrySet()) {
                Thread thread = entry.getKey();
                StackTraceElement[] stack = entry.getValue();
                AdaptiveNemesisMod.LOGGER.error("线程: {} (ID={}, 状态={})",
                    thread.getName(), thread.getId(), thread.getState());
                for (StackTraceElement element : stack) {
                    AdaptiveNemesisMod.LOGGER.error("  at {}", element);
                }
            }
            AdaptiveNemesisMod.LOGGER.error("===== 🐕 [看门狗-线程转储-回退] 结束 =====");
        }
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
     * 用于看门狗判断服务端是否仍然在进行正常 tick
     */
    public void updateServerTick() {
        if (serverThreadId == -1) {
            serverThreadId = Thread.currentThread().getId();
        }
        lastActivityNanos.set(System.nanoTime());
    }

    /**
     * 注入当前实体处理状态
     * 由 EnemyScalingHandler 在缩放实体前/后调用
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
        currentEntityName.set(entityName);
        currentEntityType.set(entityType);
        currentStage.set(stage);
        currentEntityPos.set(pos);
        currentEntityDim.set(dimension);
        lastActivityNanos.set(System.nanoTime());
    }

    /**
     * 注入 Boss buff 处理状态
     * 由 BossDamageCapHandler.applyBossBuffs() 调用
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
     * @param entityKey 实体标识键（格式：实体名称@[x, y, z]）
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
            "🐕 看门狗将实体 {} 标记为问题实体，下次将跳过缩放处理",
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
     * @return 标准化实体标识键
     */
    public static String makeEntityKey(String entityName, double x, double y, double z) {
        return entityName + "@[" + Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z) + "]";
    }

    /**
     * 获取看门狗记录的峰值卡死时长
     *
     * @return 峰值卡死时长（毫秒）
     */
    public long getPeakStuckMs() {
        return peakStuckMs.get();
    }
}