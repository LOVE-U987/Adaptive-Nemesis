package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import net.minecraft.world.entity.player.Player;

/**
 * KubeJS 统一绑定对象（F-31）
 *
 * 由 {@link KubeJSInitializer#registerBindings} 以名称 {@code AdaptiveNemesis}
 * 注入脚本全局作用域，封装入侵触发与查询 API，脚本用法示例：
 * <pre>{@code
 * // 服务器脚本 (server_scripts)
 * PlayerEvents.loggedIn(event => {
 *     AdaptiveNemesis.triggerUndeadInvasion(event.player)
 * })
 * }</pre>
 *
 * 本类不实现 {@code dev.latvian.mods.kubejs.plugin.KubeJSPlugin}，
 * 仅作为注入作用域的普通对象，不应写入 kubejs.plugins.txt。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class AdaptiveNemesisBinding {

    /**
     * 包私有构造函数 - 仅允许 KubeJSInitializer 在同包内实例化
     */
    AdaptiveNemesisBinding() {}

    /**
     * 手动触发亡灵入侵事件
     *
     * @param player 目标玩家（null 时静默忽略）
     */
    public void triggerUndeadInvasion(Player player) {
        KubeJSInitializer.triggerUndeadInvasion(player);
    }

    /**
     * 手动触发亡灵入侵事件（带自定义参数）
     *
     * @param player 目标玩家（null 时静默忽略）
     * @param waveCount 波次数量
     * @param difficultyMultiplier 难度倍率
     */
    public void triggerUndeadInvasion(Player player, int waveCount, double difficultyMultiplier) {
        KubeJSInitializer.triggerUndeadInvasion(player, waveCount, difficultyMultiplier);
    }

    /**
     * 检查玩家是否正在经历入侵事件
     *
     * @param player 玩家
     * @return 是否在入侵中
     */
    public boolean isInInvasion(Player player) {
        return KubeJSInitializer.isInInvasion(player);
    }

    /**
     * 获取玩家当前的入侵事件进度
     *
     * @param player 玩家
     * @return 进度信息，格式为 "当前波次/总波次"，不在入侵中时返回 null
     */
    public String getInvasionProgress(Player player) {
        return KubeJSInitializer.getInvasionProgress(player);
    }
}
