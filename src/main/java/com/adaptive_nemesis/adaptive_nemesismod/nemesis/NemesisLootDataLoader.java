package com.adaptive_nemesis.adaptive_nemesismod.nemesis;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 宿敌额外掉落战利品表数据包加载器
 *
 * 从数据包路径 {@code data/<namespace>/nemesis_loot/<name>.json} 读取宿敌额外掉落配置。
 * 当数据包重载时自动刷新（F3+T），支持多数据包文件合并。
 *
 * JSON 格式：
 * <pre>
 * {
 *   "loot_tables": ["adaptive_nemesis:nemesis_example_loot", "minecraft:chests/simple_dungeon"]
 * }
 * </pre>
 *
 * 所有文件的 loot_tables 数组会合并去重为全局列表，
 * 与 toml 配置 {@code nemesisLootTables} 合并后，宿敌死亡时逐表 roll 掉落。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NemesisLootDataLoader extends SimpleJsonResourceReloadListener {

    /**
     * 数据包资源目录名
     */
    public static final String DIRECTORY = "nemesis_loot";

    /**
     * JSON 解析器
     */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * 已合并的战利品表 ID 列表（不可修改，重载时整体替换）
     */
    private List<ResourceLocation> lootTables = ImmutableList.of();

    /**
     * 单例实例
     */
    private static NemesisLootDataLoader INSTANCE;

    /**
     * 私有构造函数 - 单例模式
     */
    private NemesisLootDataLoader() {
        super(GSON, DIRECTORY);
    }

    /**
     * 获取单例实例
     *
     * @return 加载器实例
     */
    public static synchronized NemesisLootDataLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NemesisLootDataLoader();
        }
        return INSTANCE;
    }

    /**
     * 获取当前生效的全部宿敌额外掉落战利品表
     *
     * @return 不可修改的战利品表列表
     */
    public List<ResourceLocation> getLootTables() {
        return lootTables;
    }

    /**
     * 资源重载回调 - 合并所有数据包文件中的战利品表定义
     *
     * @param object           解析后的 JSON 对象映射
     * @param resourceManager  资源管理器
     * @param profiler         性能分析器
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<ResourceLocation> loaded = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                parseLootTables(id, entry.getValue().getAsJsonObject(), loaded);
                AdaptiveNemesisMod.LOGGER.debug("已加载宿敌额外掉落配置: {}", id);
            } catch (Exception e) {
                AdaptiveNemesisMod.LOGGER.error("解析宿敌额外掉落配置 {} 失败: {}", id, e.getMessage());
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.error("异常堆栈:", e);
                }
            }
        }

        this.lootTables = ImmutableList.copyOf(loaded);
        AdaptiveNemesisMod.LOGGER.info("已加载 {} 个宿敌额外掉落战利品表（来自 {} 个数据包配置）",
            loaded.size(), object.size());
    }

    /**
     * 解析单个配置 JSON 中的 loot_tables 字段并合并入列表
     *
     * @param id      配置文件标识符（用于日志）
     * @param json    JSON 对象
     * @param target  合并目标列表（方法内去重）
     */
    private void parseLootTables(ResourceLocation id, JsonObject json, List<ResourceLocation> target) {
        if (!json.has("loot_tables") || !json.get("loot_tables").isJsonArray()) {
            AdaptiveNemesisMod.LOGGER.warn("宿敌额外掉落配置 {} 缺少 loot_tables 数组字段，跳过", id);
            return;
        }

        for (JsonElement element : json.getAsJsonArray("loot_tables")) {
            String rawId = element.getAsString().trim();
            ResourceLocation tableId = ResourceLocation.tryParse(rawId);
            if (tableId == null) {
                AdaptiveNemesisMod.LOGGER.warn("宿敌额外掉落配置 {} 中的战利品表 ID 无效: {}", id, rawId);
                continue;
            }
            if (!target.contains(tableId)) {
                target.add(tableId);
            }
        }
    }
}
