package com.adaptive_nemesis.adaptive_nemesismod.data;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/**
 * 世界阶段数据包加载器
 *
 * 从数据包路径 {@code data/<namespace>/world_stages/<name>.json} 读取世界阶段配置。
 * 当数据包重载时自动刷新，支持多数据包覆盖与合并。
 * 数据包配置可以覆盖每个阶段的难度倍率、属性上限、浮动范围和入侵波次。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class WorldStageDataLoader extends SimpleJsonResourceReloadListener {

    /**
     * 数据包资源目录名
     */
    public static final String DIRECTORY = "world_stages";

    /**
     * JSON 解析器
     */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * 已加载的世界阶段配置映射
     * Key 为阶段编号，Value 为该阶段的配置
     */
    private Map<Integer, WorldStageDataConfig> stages = ImmutableMap.of();

    /**
     * 单例实例
     */
    private static WorldStageDataLoader INSTANCE;

    /**
     * 私有构造函数
     */
    private WorldStageDataLoader() {
        super(GSON, DIRECTORY);
    }

    /**
     * 获取单例实例
     *
     * @return 加载器实例
     */
    public static synchronized WorldStageDataLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new WorldStageDataLoader();
        }
        return INSTANCE;
    }

    /**
     * 根据阶段编号获取配置
     *
     * @param stage 阶段编号
     * @return 阶段配置，不存在时返回默认空配置
     */
    public WorldStageDataConfig getStageConfig(int stage) {
        return stages.getOrDefault(stage, new WorldStageDataConfig(stage));
    }

    /**
     * 获取所有已加载的世界阶段配置
     *
     * @return 不可修改的阶段配置映射
     */
    public Map<Integer, WorldStageDataConfig> getAllStages() {
        return stages;
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
        Map<Integer, WorldStageDataConfig> loaded = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                WorldStageDataConfig config = parseStageConfig(id, entry.getValue().getAsJsonObject());
                if (config != null) {
                    loaded.put(config.getStage(), config);
                    AdaptiveNemesisMod.LOGGER.debug("已加载世界阶段配置: {}", id);
                }
            } catch (Exception e) {
                AdaptiveNemesisMod.LOGGER.error("解析世界阶段配置 {} 失败: {}", id, e.getMessage());
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.error("异常堆栈:", e);
                }
            }
        }

        this.stages = ImmutableMap.copyOf(loaded);
        AdaptiveNemesisMod.LOGGER.info("已加载 {} 个世界阶段数据包配置", loaded.size());
    }

    /**
     * 解析单个世界阶段配置 JSON
     *
     * @param id 配置标识符
     * @param json JSON 对象
     * @return 解析后的世界阶段配置
     */
    private WorldStageDataConfig parseStageConfig(ResourceLocation id, JsonObject json) {
        if (!json.has("stage")) {
            throw new com.google.gson.JsonParseException("世界阶段配置缺少 stage 字段");
        }

        int stage = json.get("stage").getAsInt();
        if (stage < 0) {
            throw new com.google.gson.JsonParseException("世界阶段 stage 必须大于等于 0: " + stage);
        }

        double multiplier = json.has("multiplier") ? json.get("multiplier").getAsDouble() : -1.0;
        double maxHealthMultiplier = json.has("max_health_multiplier")
            ? json.get("max_health_multiplier").getAsDouble() : -1.0;
        double maxDamageMultiplier = json.has("max_damage_multiplier")
            ? json.get("max_damage_multiplier").getAsDouble() : -1.0;
        double maxArmorMultiplier = json.has("max_armor_multiplier")
            ? json.get("max_armor_multiplier").getAsDouble() : -1.0;
        double floatMin = json.has("float_min") ? json.get("float_min").getAsDouble() : -1.0;
        double floatMax = json.has("float_max") ? json.get("float_max").getAsDouble() : -1.0;
        int invasionMaxWaves = json.has("invasion_max_waves") ? json.get("invasion_max_waves").getAsInt() : -1;

        return new WorldStageDataConfig(
            stage, multiplier, maxHealthMultiplier, maxDamageMultiplier,
            maxArmorMultiplier, floatMin, floatMax, invasionMaxWaves
        );
    }
}
