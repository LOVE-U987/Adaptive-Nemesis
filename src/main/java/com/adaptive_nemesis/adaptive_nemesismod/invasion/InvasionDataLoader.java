package com.adaptive_nemesis.adaptive_nemesismod.invasion;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 入侵配置数据包加载器
 *
 * 从数据包路径 {@code data/<namespace>/invasions/<name>.json} 读取入侵策略配置。
 * 当数据包重载时自动刷新，支持多数据包覆盖与合并。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class InvasionDataLoader extends SimpleJsonResourceReloadListener {

    /**
     * 数据包资源目录名
     */
    public static final String DIRECTORY = "invasions";

    /**
     * JSON 解析器
     */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * 已加载的入侵配置映射
     */
    private Map<ResourceLocation, InvasionData> invasions = ImmutableMap.of();

    /**
     * 单例实例
     */
    private static InvasionDataLoader INSTANCE;

    /**
     * 私有构造函数
     */
    private InvasionDataLoader() {
        super(GSON, DIRECTORY);
    }

    /**
     * 获取单例实例
     *
     * @return 加载器实例
     */
    public static synchronized InvasionDataLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new InvasionDataLoader();
        }
        return INSTANCE;
    }

    /**
     * 根据标识符获取入侵配置
     *
     * @param id 入侵配置标识符
     * @return 入侵配置，不存在时返回 null
     */
    public InvasionData getInvasion(ResourceLocation id) {
        return invasions.get(id);
    }

    /**
     * 获取所有已加载的入侵配置
     *
     * @return 不可修改的入侵配置映射
     */
    public Map<ResourceLocation, InvasionData> getAllInvasions() {
        return invasions;
    }

    /**
     * 资源重载回调
     *
     * @param object 解析后的 JSON 对象映射
     * @param resourceManager 资源管理器
     * @param profiler 性能分析器
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, InvasionData> loaded = parseEachSafely(object, this::parseInvasion);

        this.invasions = ImmutableMap.copyOf(loaded);
        AdaptiveNemesisMod.LOGGER.info("已加载 {} 个入侵数据包配置", loaded.size());
    }

    /**
     * 逐条解析配置条目，单条失败仅告警跳过，不影响其余条目
     * 坏 JSON 单文件失败不拖垮整份配置（T-04 覆盖点）
     *
     * @param object 待解析的 JSON 条目映射
     * @param parser 单条解析函数（入参：条目标识、JSON 对象）
     * @param <K> 条目标识类型
     * @param <V> 解析结果类型
     * @return 解析成功的条目映射
     */
    static <K, V> Map<K, V> parseEachSafely(Map<K, JsonElement> object,
                                            java.util.function.BiFunction<K, JsonObject, V> parser) {
        Map<K, V> loaded = new HashMap<>();

        for (Map.Entry<K, JsonElement> entry : object.entrySet()) {
            K id = entry.getKey();
            try {
                V parsed = parser.apply(id, entry.getValue().getAsJsonObject());
                if (parsed != null) {
                    loaded.put(id, parsed);
                    AdaptiveNemesisMod.LOGGER.debug("已加载入侵配置: {}", id);
                }
            } catch (Exception e) {
                AdaptiveNemesisMod.LOGGER.error("解析入侵配置 {} 失败: {}", id, e.getMessage());
                logParseStackTrace(id, e);
            }
        }

        return loaded;
    }

    /**
     * 按调试开关输出单条解析失败的完整堆栈
     *
     * 配置未加载时（数据包早期重载、纯 JVM 单元测试环境）读取调试开关会抛出
     * IllegalStateException——本方法将其隔离在内部并降级为直接输出堆栈，
     * 保证 parseEachSafely 的单条隔离语义不被 catch 块自身的异常破坏
     *
     * @param id 失败条目的标识符
     * @param e 解析异常
     */
    private static void logParseStackTrace(Object id, Exception e) {
        try {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.error("解析入侵配置 {} 的异常堆栈:", id, e);
            }
        } catch (RuntimeException configError) {
            AdaptiveNemesisMod.LOGGER.error("解析入侵配置 {} 的异常堆栈（调试开关未就绪，跳过检查）:", id, e);
        }
    }

    /**
     * 解析单个入侵配置 JSON
     * 顶层 name 字段暂不支持（F-12：加载时 warn 不再静默忽略）
     *
     * @param id 配置标识符
     * @param json JSON 对象
     * @return 解析后的入侵配置
     */
    private InvasionData parseInvasion(ResourceLocation id, JsonObject json) {
        String nameKey = json.has("name") ? json.get("name").getAsString() : null;
        if (nameKey != null) {
            AdaptiveNemesisMod.LOGGER.warn("入侵配置 {} 的 name 字段暂不支持，已忽略", id);
        }
        int maxWaves = json.has("max_waves") ? json.get("max_waves").getAsInt() : 6;
        int spawnDistance = json.has("spawn_distance") ? json.get("spawn_distance").getAsInt() : 70;

        List<InvasionData.WaveData> waves = new ArrayList<>();
        if (json.has("waves") && json.get("waves").isJsonArray()) {
            JsonArray waveArray = json.getAsJsonArray("waves");
            for (JsonElement element : waveArray) {
                if (element.isJsonObject()) {
                    InvasionData.WaveData wave = parseWave(element.getAsJsonObject());
                    if (wave != null) {
                        waves.add(wave);
                    }
                }
            }
        }

        if (waves.isEmpty()) {
            AdaptiveNemesisMod.LOGGER.warn("入侵配置 {} 没有定义任何波次，将使用硬编码配置", id);
        }

        InvasionRewardData rewards = parseRewards(json);

        return new InvasionData(id, nameKey, maxWaves, spawnDistance, waves, rewards);
    }

    /**
     * 解析奖励配置
     *
     * @param json JSON 对象
     * @return 奖励配置
     */
    private InvasionRewardData parseRewards(JsonObject json) {
        InvasionRewardData rewards = new InvasionRewardData();

        if (!json.has("rewards") || !json.get("rewards").isJsonObject()) {
            return rewards;
        }

        JsonObject rewardsJson = json.getAsJsonObject("rewards");

        // 战利品表
        if (rewardsJson.has("loot_tables") && rewardsJson.get("loot_tables").isJsonArray()) {
            JsonArray lootArray = rewardsJson.getAsJsonArray("loot_tables");
            for (JsonElement element : lootArray) {
                ResourceLocation lootTable = ResourceLocation.tryParse(element.getAsString());
                if (lootTable != null) {
                    rewards.addLootTable(lootTable);
                }
            }
        }

        // 单一战利品表（兼容简写）
        if (rewardsJson.has("loot_table")) {
            ResourceLocation lootTable = ResourceLocation.tryParse(rewardsJson.get("loot_table").getAsString());
            if (lootTable != null) {
                rewards.addLootTable(lootTable);
            }
        }

        // 经验值
        if (rewardsJson.has("experience")) {
            rewards.setExperience(rewardsJson.get("experience").getAsInt());
        }

        // 药水效果（F-38：缺 id 或未知效果跳过并 warn，不再 NPE）
        if (rewardsJson.has("effects") && rewardsJson.get("effects").isJsonArray()) {
            JsonArray effectArray = rewardsJson.getAsJsonArray("effects");
            for (JsonElement element : effectArray) {
                if (element.isJsonObject()) {
                    JsonObject effectObj = element.getAsJsonObject();
                    String effectId = effectObj.has("id") ? effectObj.get("id").getAsString() : null;
                    int duration = effectObj.has("duration") ? effectObj.get("duration").getAsInt() : 30;
                    int amplifier = effectObj.has("amplifier") ? effectObj.get("amplifier").getAsInt() : 0;
                    MobEffectInstance effect = InvasionRewardData.parseEffect(effectId, duration, amplifier);
                    if (effect != null) {
                        rewards.addEffect(effect.getEffect().value(), effect.getDuration(), effect.getAmplifier());
                    } else {
                        AdaptiveNemesisMod.LOGGER.warn("入侵配置的奖励药水效果缺少有效 id 或未知，已跳过");
                    }
                }
            }
        }

        return rewards;
    }

    /**
     * 解析单个波次配置
     *
     * @param json JSON 对象
     * @return 解析后的波次配置
     */
    private InvasionData.WaveData parseWave(JsonObject json) {
        int waveNumber = json.has("wave") ? json.get("wave").getAsInt() : 1;
        double difficultyMultiplier = json.has("difficulty_multiplier")
            ? json.get("difficulty_multiplier").getAsDouble() : 1.0;

        List<InvasionData.EnemyData> enemies = new ArrayList<>();
        if (json.has("enemies") && json.get("enemies").isJsonArray()) {
            JsonArray enemyArray = json.getAsJsonArray("enemies");
            for (JsonElement element : enemyArray) {
                if (element.isJsonObject()) {
                    InvasionData.EnemyData enemy = parseEnemy(element.getAsJsonObject());
                    if (enemy != null) {
                        enemies.add(enemy);
                    }
                }
            }
        }

        if (enemies.isEmpty()) {
            throw new JsonParseException("波次 " + waveNumber + " 没有定义任何敌人");
        }

        return new InvasionData.WaveData(waveNumber, difficultyMultiplier, enemies);
    }

    /**
     * 解析单个敌人配置
     * spawn_direction 字段暂不支持（F-12：加载时 warn 不再静默忽略）；
     * 药水效果缺 id 时跳过并 warn（F-38，不再 NPE）
     *
     * @param json JSON 对象
     * @return 解析后的敌人配置
     */
    private InvasionData.EnemyData parseEnemy(JsonObject json) {
        if (!json.has("entity_type")) {
            throw new JsonParseException("敌人配置缺少 entity_type 字段");
        }

        ResourceLocation entityId = ResourceLocation.tryParse(json.get("entity_type").getAsString());
        if (entityId == null) {
            throw new JsonParseException("无效的 entity_type: " + json.get("entity_type").getAsString());
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityId);
        if (entityType == null || entityType == EntityType.PIG) {
            // 未注册的实体类型默认返回 PIG，需要额外判断
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
                throw new JsonParseException("未知的实体类型: " + entityId);
            }
        }

        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
        boolean isBoss = json.has("is_boss") && json.get("is_boss").getAsBoolean();
        boolean glowing = json.has("glowing") && json.get("glowing").getAsBoolean();
        boolean frostWalker = json.has("frost_walker") && json.get("frost_walker").getAsBoolean();
        String spawnDirection = json.has("spawn_direction") ? json.get("spawn_direction").getAsString() : null;
        if (spawnDirection != null) {
            AdaptiveNemesisMod.LOGGER.warn("入侵配置中敌人 {} 的 spawn_direction 字段暂不支持，已忽略", entityId);
        }
        String customNameKey = json.has("custom_name") ? json.get("custom_name").getAsString() : null;
        double healthMultiplier = json.has("health_multiplier") ? json.get("health_multiplier").getAsDouble() : 1.0;
        double damageMultiplier = json.has("damage_multiplier") ? json.get("damage_multiplier").getAsDouble() : 1.0;

        ResourceLocation equipmentLootTable = null;
        if (json.has("equipment_loot_table")) {
            equipmentLootTable = ResourceLocation.tryParse(json.get("equipment_loot_table").getAsString());
        }

        List<MobEffectInstance> effects = new ArrayList<>();
        if (json.has("effects") && json.get("effects").isJsonArray()) {
            JsonArray effectArray = json.getAsJsonArray("effects");
            for (JsonElement element : effectArray) {
                if (element.isJsonObject()) {
                    JsonObject effectObj = element.getAsJsonObject();
                    String effectId = effectObj.has("id") ? effectObj.get("id").getAsString() : null;
                    int duration = effectObj.has("duration") ? effectObj.get("duration").getAsInt() : 30;
                    int amplifier = effectObj.has("amplifier") ? effectObj.get("amplifier").getAsInt() : 0;
                    MobEffectInstance effect = InvasionRewardData.parseEffect(effectId, duration, amplifier);
                    if (effect != null) {
                        effects.add(effect);
                    } else {
                        AdaptiveNemesisMod.LOGGER.warn("入侵配置中敌人 {} 的药水效果缺少有效 id 或未知，已跳过", entityId);
                    }
                }
            }
        }

        return new InvasionData.EnemyData(entityType, count, weight, isBoss, equipmentLootTable,
            effects, glowing, frostWalker, spawnDirection, customNameKey,
            healthMultiplier, damageMultiplier);
    }
}
