package com.adaptive_nemesis.adaptive_nemesismod.memory;

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

import java.util.Map;

/**
 * 宿敌配置数据包加载器
 *
 * 从数据包路径 {@code data/<namespace>/nemesis/<name>.json} 读取宿敌全局配置。
 * 当数据包重载时自动刷新，后加载的数据包会覆盖先加载的数据包。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NemesisDataLoader extends SimpleJsonResourceReloadListener {

    /**
     * 数据包资源目录名
     */
    public static final String DIRECTORY = "nemesis";

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
     * 资源重载回调
     *
     * @param object 解析后的 JSON 对象映射
     * @param resourceManager 资源管理器
     * @param profiler 性能分析器
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        NemesisConfigData merged = new NemesisConfigData();

        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                NemesisConfigData parsed = parseConfig(entry.getValue().getAsJsonObject());
                if (parsed != null) {
                    merged = parsed;
                    AdaptiveNemesisMod.LOGGER.debug("已加载宿敌配置: {}", id);
                }
            } catch (Exception e) {
                AdaptiveNemesisMod.LOGGER.error("解析宿敌配置 {} 失败: {}", id, e.getMessage());
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.error("异常堆栈:", e);
                }
            }
        }

        this.configData = merged;
        AdaptiveNemesisMod.LOGGER.info("宿敌数据包配置已加载: 近战抗性上限={}, 攻击加成上限={}, 生命加成上限={}",
            String.format("%.2f", configData.getMeleeResistanceCap()),
            String.format("%.2f", configData.getAttackBonusCap()),
            String.format("%.2f", configData.getHealthBonusCap())
        );
    }

    /**
     * 解析单个宿敌配置 JSON
     *
     * @param json JSON 对象
     * @return 解析后的宿敌配置
     */
    private NemesisConfigData parseConfig(JsonObject json) {
        double meleeResistanceCap = json.has("melee_resistance_cap")
            ? json.get("melee_resistance_cap").getAsDouble() : 0.3;
        double rangedResistanceCap = json.has("ranged_resistance_cap")
            ? json.get("ranged_resistance_cap").getAsDouble() : 0.3;
        double magicResistanceCap = json.has("magic_resistance_cap")
            ? json.get("magic_resistance_cap").getAsDouble() : 0.3;
        double attackBonusCap = json.has("attack_bonus_cap")
            ? json.get("attack_bonus_cap").getAsDouble() : 0.25;
        double speedBonusCap = json.has("speed_bonus_cap")
            ? json.get("speed_bonus_cap").getAsDouble() : 0.2;
        double healthBonusCap = json.has("health_bonus_cap")
            ? json.get("health_bonus_cap").getAsDouble() : 0.5;
        int killsPerLevel = json.has("kills_per_level")
            ? json.get("kills_per_level").getAsInt() : 10;
        int deathsPerLevel = json.has("deaths_per_level")
            ? json.get("deaths_per_level").getAsInt() : 5;
        int maxLevel = json.has("max_level")
            ? json.get("max_level").getAsInt() : 50;

        return new NemesisConfigData(
            meleeResistanceCap, rangedResistanceCap, magicResistanceCap,
            attackBonusCap, speedBonusCap, healthBonusCap,
            killsPerLevel, deathsPerLevel, maxLevel
        );
    }
}
