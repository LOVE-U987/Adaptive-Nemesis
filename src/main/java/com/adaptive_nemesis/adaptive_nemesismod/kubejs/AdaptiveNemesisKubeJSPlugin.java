package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.ScriptType;

/**
 * KubeJS 插件主类
 *
 * 为自适应宿敌模组提供 KubeJS 集成支持：
 * - 注册自定义事件（实体强化、伤害计算等）
 * - 暴露模组 API 给 KubeJS 脚本使用
 * - 提供配置和热重载支持
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class AdaptiveNemesisKubeJSPlugin implements KubeJSPlugin {

    /**
     * 事件组 - 自适应宿敌模组的所有 KubeJS 事件
     */
    public static final EventGroup ADAPTIVE_NEMESIS_EVENTS = EventGroup.of("adaptive_nemesis");

    /**
     * 实体强化事件 - 当实体被自适应宿敌模组强化时触发
     * 允许 KubeJS 脚本修改或覆盖强化逻辑
     */
    public static final EventHandler ENTITY_SCALE = ADAPTIVE_NEMESIS_EVENTS.server("entity_scale", () -> EntityScaleEventJS.class);

    /**
     * 伤害计算事件 - 当计算真实伤害时触发
     * 允许 KubeJS 脚本修改伤害数值
     */
    public static final EventHandler DAMAGE_CALCULATION = ADAPTIVE_NEMESIS_EVENTS.server("damage_calculation", () -> DamageCalculationEventJS.class);

    /**
     * 玩家强度评估事件 - 当评估玩家强度时触发
     * 允许 KubeJS 脚本添加自定义的强度评估逻辑
     */
    public static final EventHandler PLAYER_STRENGTH_EVALUATION = ADAPTIVE_NEMESIS_EVENTS.server("player_strength_evaluation", () -> PlayerStrengthEvaluationEventJS.class);

    /**
     * 宿敌记忆更新事件 - 当宿敌记忆数据更新时触发
     */
    public static final EventHandler NEMESIS_MEMORY_UPDATE = ADAPTIVE_NEMESIS_EVENTS.server("nemesis_memory_update", () -> NemesisMemoryUpdateEventJS.class);

    /**
     * 初始化插件
     */
    @Override
    public void init() {
        AdaptiveNemesisMod.LOGGER.info("Adaptive Nemesis KubeJS 插件已加载！");
    }

    /**
     * 注册事件组
     */
    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(ADAPTIVE_NEMESIS_EVENTS);
    }

    /**
     * 检查 KubeJS 是否已加载
     *
     * @return 如果 KubeJS 模组已加载返回 true
     */
    public static boolean isKubeJSLoaded() {
        try {
            Class.forName("dev.latvian.mods.kubejs.plugin.KubeJSPlugin");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
