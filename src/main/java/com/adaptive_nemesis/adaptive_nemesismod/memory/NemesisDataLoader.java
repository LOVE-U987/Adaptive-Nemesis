package com.adaptive_nemesis.adaptive_nemesismod.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * 宿敌配置数据包加载器
 *
 * 从数据包路径 {@code data/<namespace>/nemesis/<name>.json} 读取宿敌全局配置。
 * 当数据包重载时自动刷新。
 *
 * 合并语义（F-39）：按命名空间优先级排序后依次<b>整体覆盖</b>——
 * minecraft 命名空间作为最低优先级基线最先应用，其余命名空间按名称字母序
 * 随后应用（同命名空间内按路径字母序），后应用的配置直接整体替换此前配置，
 * 不是字段级合并。
 *
 * 解析后的配置经 Builder 统一钳制：各上限/系数/阈值/半径不小于 0，
 * maxLevel、killsPerLevel、deathsPerLevel 不小于 1。
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class NemesisDataLoader extends SimpleJsonResourceReloadListener {

    /**
     * 数据包资源目录名
     */
    public static final String DIRECTORY = "nemesis";

    /**
     * 最低优先级命名空间（原版基线，最先应用）
     */
    private static final String NAMESPACE_MINECRAFT = "minecraft";

    /**
     * JSON 解析器
     */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * 当前生效的宿敌配置
     */
    private NemesisConfigData configData = new NemesisConfigData();

    /**
     * 单例实例
     */
    private static NemesisDataLoader INSTANCE;

    /**
     * 私有构造函数
     */
    private NemesisDataLoader() {
        super(GSON, DIRECTORY);
    }

    /**
     * 获取单例实例
     *
     * @return 加载器实例
     */
    public static synchronized NemesisDataLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NemesisDataLoader();
        }
        return INSTANCE;
    }

    /**
     * 获取当前宿敌全局配置
     *
     * @return 宿敌配置
     */
    public NemesisConfigData getConfig() {
        return configData;
    }

    /**
     * 资源重载回调 - 按命名空间优先级排序后依次整体覆盖（F-39）
     *
     * @param object 解析后的 JSON 对象映射
     * @param resourceManager 资源管理器
     * @param profiler 性能分析器
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        // 按命名空间优先级排序：minecraft 基线最先，其余按 ResourceLocation 自然序
        // （ResourceLocation.compareTo 先比较命名空间、再比较路径）
        List<Map.Entry<ResourceLocation, JsonElement>> sortedEntries = new ArrayList<>(object.entrySet());
        sortedEntries.sort(Comparator
            .comparingInt((Map.Entry<ResourceLocation, JsonElement> entry) ->
                NAMESPACE_MINECRAFT.equals(entry.getKey().getNamespace()) ? 0 : 1)
            .thenComparing(Map.Entry::getKey));

        NemesisConfigData selected = new NemesisConfigData();
        int appliedCount = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : sortedEntries) {
            ResourceLocation id = entry.getKey();
            try {
                NemesisConfigData parsed = parseConfig(entry.getValue().getAsJsonObject());
                if (parsed != null) {
                    // 整体覆盖：后应用的高优先级配置直接替换此前配置
                    selected = parsed;
                    appliedCount++;
                    AdaptiveNemesisMod.LOGGER.debug("已应用宿敌配置: {}", id);
                }
            } catch (Exception e) {
                AdaptiveNemesisMod.LOGGER.error("解析宿敌配置 {} 失败: {}", id, e.getMessage());
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.error("异常堆栈:", e);
                }
            }
        }

        this.configData = selected;
        AdaptiveNemesisMod.LOGGER.info(
            "宿敌数据包配置已加载: 整体覆盖应用 {}/{} 个配置文件, 近战抗性上限={}, 攻击加成上限={}, 生命加成上限={}",
            appliedCount, object.size(),
            String.format("%.2f", configData.getMeleeResistanceCap()),
            String.format("%.2f", configData.getAttackBonusCap()),
            String.format("%.2f", configData.getHealthBonusCap())
        );
    }

    /**
     * 解析单个宿敌配置 JSON（F-39）
     * 缺省键沿用 Builder 默认值，最终经 build 统一钳制
     *
     * @param json JSON 对象
     * @return 解析并钳制后的宿敌配置，JSON 非对象时返回 null
     */
    private NemesisConfigData parseConfig(JsonObject json) {
        NemesisConfigData.Builder builder = NemesisConfigData.builder();
        readResistanceCaps(json, builder);
        readLevelParams(json, builder);
        readBalanceParams(json, builder);
        return builder.build();
    }

    /**
     * 读取各战斗风格抗性与加成上限字段
     *
     * @param json JSON 对象
     * @param builder 构建器
     */
    private void readResistanceCaps(JsonObject json, NemesisConfigData.Builder builder) {
        if (json.has("melee_resistance_cap")) {
            builder.meleeResistanceCap(json.get("melee_resistance_cap").getAsDouble());
        }
        if (json.has("ranged_resistance_cap")) {
            builder.rangedResistanceCap(json.get("ranged_resistance_cap").getAsDouble());
        }
        if (json.has("magic_resistance_cap")) {
            builder.magicResistanceCap(json.get("magic_resistance_cap").getAsDouble());
        }
        if (json.has("attack_bonus_cap")) {
            builder.attackBonusCap(json.get("attack_bonus_cap").getAsDouble());
        }
        if (json.has("speed_bonus_cap")) {
            builder.speedBonusCap(json.get("speed_bonus_cap").getAsDouble());
        }
        if (json.has("health_bonus_cap")) {
            builder.healthBonusCap(json.get("health_bonus_cap").getAsDouble());
        }
    }

    /**
     * 读取等级公式参数字段
     *
     * @param json JSON 对象
     * @param builder 构建器
     */
    private void readLevelParams(JsonObject json, NemesisConfigData.Builder builder) {
        if (json.has("kills_per_level")) {
            builder.killsPerLevel(json.get("kills_per_level").getAsInt());
        }
        if (json.has("deaths_per_level")) {
            builder.deathsPerLevel(json.get("deaths_per_level").getAsInt());
        }
        if (json.has("max_level")) {
            builder.maxLevel(json.get("max_level").getAsInt());
        }
    }

    /**
     * 读取称号档位阈值、加成系数与通知半径字段（F-44 迁移项）
     *
     * @param json JSON 对象
     * @param builder 构建器
     */
    private void readBalanceParams(JsonObject json, NemesisConfigData.Builder builder) {
        if (json.has("suffix_epic_threshold")) {
            builder.suffixEpicThreshold(json.get("suffix_epic_threshold").getAsDouble());
        }
        if (json.has("suffix_legendary_threshold")) {
            builder.suffixLegendaryThreshold(json.get("suffix_legendary_threshold").getAsDouble());
        }
        if (json.has("attack_bonus_per_death")) {
            builder.attackBonusPerDeath(json.get("attack_bonus_per_death").getAsDouble());
        }
        if (json.has("speed_bonus_per_kda")) {
            builder.speedBonusPerKda(json.get("speed_bonus_per_kda").getAsDouble());
        }
        if (json.has("kda_speed_threshold")) {
            builder.kdaSpeedThreshold(json.get("kda_speed_threshold").getAsDouble());
        }
        if (json.has("health_bonus_per_kill")) {
            builder.healthBonusPerKill(json.get("health_bonus_per_kill").getAsDouble());
        }
        if (json.has("notification_radius")) {
            builder.notificationRadius(json.get("notification_radius").getAsDouble());
        }
    }
}
