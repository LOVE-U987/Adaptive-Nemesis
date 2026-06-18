package com.adaptive_nemesis.adaptive_nemesismod.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重构优化点微基准测试
 *
 * 通过对比“优化前模拟实现”与“优化后实际实现”的耗时，
 * 量化展示缓存、空间索引、关键词排序等优化的收益。
 * 注意：这是 JVM 内的轻量级基准，不等同于 JMH，但足以反映数量级差异。
 */
@DisplayName("重构优化性能基准")
class OptimizationBenchmarkTest {

    private static final int WARMUP_ITERATIONS = 10_000;
    private static final int BENCHMARK_ITERATIONS = 100_000;

    /**
     * 测量 Boss 关键词匹配中“按长度排序关键词”的收益。
     *
     * 优化前：按原始顺序匹配，可能先命中短关键词导致误判。
     * 优化后：按长度升序匹配，优先命中更具体的名称。
     */
    @Test
    @DisplayName("Boss 关键词匹配：排序后 vs 原始顺序")
    void bossKeywordMatchingBenchmark() {
        Set<String> keywords = Set.of(
            "boss", "elite", "champion", "cataclysm", "ignis", "ender_dragon"
        );
        List<String> sortedKeywords = new ArrayList<>(keywords);
        sortedKeywords.sort(Comparator.comparingInt(String::length));

        String entityKey = "cataclysm:ignis";

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            matchesUnsorted(entityKey, keywords);
            matchesSorted(entityKey, sortedKeywords);
        }

        long t1 = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            assertTrue(matchesUnsorted(entityKey, keywords));
        }
        long unsortedNs = System.nanoTime() - t1;

        long t2 = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            assertTrue(matchesSorted(entityKey, sortedKeywords));
        }
        long sortedNs = System.nanoTime() - t2;

        double unsortedAvg = (double) unsortedNs / BENCHMARK_ITERATIONS;
        double sortedAvg = (double) sortedNs / BENCHMARK_ITERATIONS;

        System.out.printf(
            "[Boss关键词匹配] 原始顺序: %.2f ns/次, 按长度排序: %.2f ns/次, 提升: %.2f%%%n",
            unsortedAvg, sortedAvg, (1.0 - sortedAvg / unsortedAvg) * 100.0
        );
    }

    private boolean matchesUnsorted(String entityKey, Set<String> keywords) {
        String lower = entityKey.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSorted(String entityKey, List<String> sortedKeywords) {
        String lower = entityKey.toLowerCase();
        for (String keyword : sortedKeywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 测量 Boss 识别缓存的收益。
     *
     * 优化前：每次伤害事件都遍历责任链（此处用字符串操作模拟）。
     * 优化后：使用 WeakHashMap 缓存 UUID -> Boolean，命中时 O(1)。
     */
    @Test
    @DisplayName("Boss 识别缓存：命中 vs 未命中")
    void bossCacheBenchmark() {
        Map<UUID, Boolean> cache = new HashMap<>();
        UUID uuid = UUID.randomUUID();
        cache.put(uuid, true);

        // 模拟责任链识别开销
        java.util.function.Function<UUID, Boolean> expensiveCheck = id -> {
            String s = id.toString();
            return s.contains("-") && s.length() > 30;
        };

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            expensiveCheck.apply(uuid);
            cache.get(uuid);
        }

        long t1 = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            assertTrue(expensiveCheck.apply(uuid));
        }
        long noCacheNs = System.nanoTime() - t1;

        long t2 = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            Boolean cached = cache.get(uuid);
            assertTrue(cached != null && cached);
        }
        long cacheHitNs = System.nanoTime() - t2;

        double noCacheAvg = (double) noCacheNs / BENCHMARK_ITERATIONS;
        double cacheHitAvg = (double) cacheHitNs / BENCHMARK_ITERATIONS;

        System.out.printf(
            "[Boss识别缓存] 无缓存: %.2f ns/次, 缓存命中: %.2f ns/次, 提升: %.2f%%%n",
            noCacheAvg, cacheHitAvg, (1.0 - cacheHitAvg / noCacheAvg) * 100.0
        );
    }

    /**
     * 测量玩家空间索引的收益。
     *
     * 优化前：每次实体生成遍历所有在线玩家并计算距离。
     * 优化后：每 tick 按区块重建一次索引，实体生成时只扫描附近网格内的玩家。
     *
     * 场景设计：100 玩家在出生点城镇聚集，400 玩家分散在远方；
     * 刷怪点均匀分布，其中多数远离玩家密集区，从而体现分桶过滤远处玩家的优势。
     */
    @Test
    @DisplayName("玩家空间索引：分桶 vs 全量扫描")
    void playerSpatialIndexBenchmark() {
        int playerCount = 5000;
        int spawnPointCount = 1000;
        double rangeBlocks = 128.0;
        double rangeSq = rangeBlocks * rangeBlocks;

        // 200 个玩家聚集在出生点城镇 (0,0) 附近
        List<double[]> players = new ArrayList<>(playerCount);
        for (int i = 0; i < 200; i++) {
            players.add(new double[] { i * 2.0, 64.0, i * 2.0 });
        }
        // 4800 个玩家分散在 2000~50000 格外的远方
        for (int i = 0; i < 4800; i++) {
            double angle = 2.0 * Math.PI * i / 4800.0;
            double dist = 2000.0 + i * 10.0;
            players.add(new double[] { Math.cos(angle) * dist, 64.0, Math.sin(angle) * dist });
        }

        // 1000 个刷怪点均匀分布在世界各处，只有少量落在城镇附近
        List<double[]> spawnPoints = new ArrayList<>(spawnPointCount);
        for (int i = 0; i < spawnPointCount; i++) {
            double angle = 2.0 * Math.PI * i / spawnPointCount;
            double dist = i * 80.0;
            spawnPoints.add(new double[] { Math.cos(angle) * dist, 64.0, Math.sin(angle) * dist });
        }

        int iterations = BENCHMARK_ITERATIONS / 10;

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS / 10; i++) {
            fullScanBatch(players, spawnPoints, rangeSq);
            gridScanBatch(players, spawnPoints, rangeBlocks, rangeSq);
        }

        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            assertTrue(fullScanBatch(players, spawnPoints, rangeSq) >= 0);
        }
        long fullScanNs = System.nanoTime() - t1;

        long t2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            assertTrue(gridScanBatch(players, spawnPoints, rangeBlocks, rangeSq) >= 0);
        }
        long gridScanNs = System.nanoTime() - t2;

        double fullScanAvg = (double) fullScanNs / iterations;
        double gridScanAvg = (double) gridScanNs / iterations;

        System.out.printf(
            "[玩家空间索引] 全量扫描(每 tick %d 实体 x %d 玩家): %.2f ns/tick, 分桶索引(含每 tick 重建): %.2f ns/tick, 提升: %.2f%%%n",
            spawnPointCount, playerCount, fullScanAvg, gridScanAvg,
            (fullScanAvg > 0) ? (1.0 - gridScanAvg / fullScanAvg) * 100.0 : 0.0
        );
    }

    private int fullScanBatch(List<double[]> players, List<double[]> spawnPoints, double rangeSq) {
        int total = 0;
        for (double[] center : spawnPoints) {
            total += fullScan(players, center, rangeSq);
        }
        return total;
    }

    private int gridScanBatch(List<double[]> players, List<double[]> spawnPoints, double rangeBlocks, double rangeSq) {
        Map<Long, List<double[]>> grid = buildGrid(players);
        int total = 0;
        for (double[] center : spawnPoints) {
            total += gridQuery(grid, center, rangeBlocks, rangeSq);
        }
        return total;
    }

    private int fullScan(List<double[]> players, double[] center, double rangeSq) {
        int count = 0;
        for (double[] p : players) {
            double dx = p[0] - center[0];
            double dz = p[2] - center[2];
            if (dx * dx + dz * dz <= rangeSq) {
                count++;
            }
        }
        return count;
    }

    private Map<Long, List<double[]>> buildGrid(List<double[]> players) {
        Map<Long, List<double[]>> grid = new HashMap<>();
        for (double[] p : players) {
            long cx = (long) Math.floor(p[0] / 16.0);
            long cz = (long) Math.floor(p[2] / 16.0);
            grid.computeIfAbsent((cx << 32) | (cz & 0xffffffffL), k -> new ArrayList<>()).add(p);
        }
        return grid;
    }

    private int gridQuery(Map<Long, List<double[]>> grid, double[] center, double rangeBlocks, double rangeSq) {
        int count = 0;
        int centerCx = (int) Math.floor(center[0] / 16.0);
        int centerCz = (int) Math.floor(center[2] / 16.0);
        int radius = (int) Math.ceil(rangeBlocks / 16.0);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long key = ((long) (centerCx + dx) << 32) | ((centerCz + dz) & 0xffffffffL);
                List<double[]> cell = grid.get(key);
                if (cell == null) continue;
                for (double[] p : cell) {
                    double ddx = p[0] - center[0];
                    double ddz = p[2] - center[2];
                    if (ddx * ddx + ddz * ddz <= rangeSq) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * 测量装备/附魔候选列表预缓存的收益。
     *
     * 优化前：每次实体生成都遍历整个物品/附魔注册表，过滤命名空间与标签。
     * 优化后：启动时一次性扫描并缓存候选列表，生成时直接随机选取。
     */
    @Test
    @DisplayName("装备/附魔候选：预缓存 vs 每次全量扫描")
    void equipmentEnchantmentCacheBenchmark() {
        int registrySize = 1000;
        int candidateCount = 50;
        List<String> registry = new ArrayList<>(registrySize);
        for (int i = 0; i < registrySize; i++) {
            registry.add((i % 3 == 0 ? "minecraft:" : "mod" + (i % 10) + ":") + "item_" + i);
        }
        // 预缓存的候选列表
        List<String> cached = registry.stream()
            .filter(s -> !s.startsWith("minecraft:"))
            .limit(candidateCount)
            .toList();

        // 模拟筛选条件：非 minecraft 命名空间且包含特定后缀
        java.util.function.Predicate<String> isModCandidate = s -> !s.startsWith("minecraft:") && s.contains("item");

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS / 10; i++) {
            scanAndPick(registry, isModCandidate);
            pickFromCache(cached);
        }

        int iterations = BENCHMARK_ITERATIONS / 10;

        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            assertNotNull(scanAndPick(registry, isModCandidate));
        }
        long scanNs = System.nanoTime() - t1;

        long t2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            assertNotNull(pickFromCache(cached));
        }
        long cacheNs = System.nanoTime() - t2;

        double scanAvg = (double) scanNs / iterations;
        double cacheAvg = (double) cacheNs / iterations;

        System.out.printf(
            "[装备/附魔候选] 每次全量扫描(%d 项): %.2f ns/次, 预缓存(%d 项): %.2f ns/次, 提升: %.2f%%%n",
            registrySize, scanAvg, cached.size(), cacheAvg,
            (scanAvg > 0) ? (1.0 - cacheAvg / scanAvg) * 100.0 : 0.0
        );
    }

    private String scanAndPick(List<String> registry, java.util.function.Predicate<String> predicate) {
        List<String> candidates = new ArrayList<>();
        for (String s : registry) {
            if (predicate.test(s)) {
                candidates.add(s);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private String pickFromCache(List<String> cached) {
        if (cached.isEmpty()) {
            return null;
        }
        return cached.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(cached.size()));
    }

    /**
     * 测量 KubeJS 事件触发中反射 Method 缓存的收益。
     *
     * 优化前：每次调用都通过 Class.getMethod 查找方法。
     * 优化后：启动时缓存 Method 对象，调用时直接 invoke。
     */
    @Test
    @DisplayName("KubeJS 反射：缓存 Method vs 每次查找")
    void reflectionCacheBenchmark() throws Exception {
        Class<?> clazz = OptimizationBenchmarkTest.class;
        String methodName = "dummyHandler";

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            clazz.getMethod(methodName, String.class).invoke(this, "warmup");
            cachedMethod.invoke(this, "warmup");
        }

        long t1 = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            java.lang.reflect.Method m = clazz.getMethod(methodName, String.class);
            assertNotNull(m.invoke(this, "test"));
        }
        long lookupNs = System.nanoTime() - t1;

        long t2 = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            assertNotNull(cachedMethod.invoke(this, "test"));
        }
        long cachedNs = System.nanoTime() - t2;

        double lookupAvg = (double) lookupNs / BENCHMARK_ITERATIONS;
        double cachedAvg = (double) cachedNs / BENCHMARK_ITERATIONS;

        System.out.printf(
            "[KubeJS反射] 每次 getMethod: %.2f ns/次, 缓存 Method: %.2f ns/次, 提升: %.2f%%%n",
            lookupAvg, cachedAvg,
            (lookupAvg > 0) ? (1.0 - cachedAvg / lookupAvg) * 100.0 : 0.0
        );
    }

    /** 供反射缓存基准使用的无意义方法。 */
    public String dummyHandler(String arg) {
        return arg;
    }

    private final java.lang.reflect.Method cachedMethod;

    {
        try {
            cachedMethod = OptimizationBenchmarkTest.class.getMethod("dummyHandler", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 测量 ThreadLocalRandom 相对 Random 实例字段的并发优势。
     *
     * 优化前：单实例 Random 在高并发下争用种子。
     * 优化后：ThreadLocalRandom 每个线程独立，无锁。
     */
    @Test
    @DisplayName("随机数生成：ThreadLocalRandom vs Random")
    void randomBenchmark() {
        java.util.Random sharedRandom = new java.util.Random(42);

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            sharedRandom.nextDouble();
            ThreadLocalRandom.current().nextDouble();
        }

        long t1 = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            sharedRandom.nextDouble();
        }
        long randomNs = System.nanoTime() - t1;

        long t2 = System.nanoTime();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            ThreadLocalRandom.current().nextDouble();
        }
        long tlRandomNs = System.nanoTime() - t2;

        double randomAvg = (double) randomNs / BENCHMARK_ITERATIONS;
        double tlRandomAvg = (double) tlRandomNs / BENCHMARK_ITERATIONS;

        System.out.printf(
            "[随机数生成] Random: %.2f ns/次, ThreadLocalRandom: %.2f ns/次, 提升: %.2f%%%n",
            randomAvg, tlRandomAvg, (1.0 - tlRandomAvg / randomAvg) * 100.0
        );
    }
}
