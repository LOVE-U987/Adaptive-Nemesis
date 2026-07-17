package com.adaptive_nemesis.adaptive_nemesismod.invasion;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem.InvasionType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 入侵事件 KubeJS 支持类
 * 
 * 提供 KubeJS API 用于自定义入侵事件
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class InvasionKubeJsSupport {

    /**
     * 入侵系统实例
     */
    private InvasionSystem invasionSystem;

    /**
     * 构造函数
     * 
     * @param invasionSystem 入侵系统实例
     */
    public InvasionKubeJsSupport(InvasionSystem invasionSystem) {
        this.invasionSystem = invasionSystem;
    }

    /**
     * KubeJS API 类
     */
    public class AdaptiveNemesisKubeJsAPI {

        /**
         * 手动触发亡灵入侵事件
         * 
         * @param player 目标玩家
         */
        public void triggerUndeadInvasion(Player player) {
            if (player != null) {
                invasionSystem.triggerInvasion(player.level(), player, InvasionType.UNDEAD);
            }
        }

        /**
         * 手动触发亡灵入侵事件（带自定义参数）
         * 
         * @param player 目标玩家
         * @param waveCount 波次数量
         * @param difficultyMultiplier 难度倍率
         */
        public void triggerUndeadInvasion(Player player, int waveCount, double difficultyMultiplier) {
            if (player != null) {
                invasionSystem.triggerInvasionManual(player, InvasionType.UNDEAD, waveCount, difficultyMultiplier);
            }
        }

        /**
         * 检查玩家是否正在经历入侵事件
         * 
         * @param player 玩家
         * @return 是否在入侵中
         */
        public boolean isInInvasion(Player player) {
            if (player == null) {
                return false;
            }
            return invasionSystem.getActiveInvasion(player) != null;
        }

        /**
         * 获取玩家当前的入侵事件进度
         * 
         * @param player 玩家
         * @return 进度信息，格式为 "当前波次/总波次"，或 null
         */
        public String getInvasionProgress(Player player) {
            if (player == null) {
                return null;
            }
            ActiveInvasion invasion = invasionSystem.getActiveInvasion(player);
            if (invasion == null) {
                return null;
            }
            return String.format("%d/%d", invasion.getCurrentWave(), invasion.getTotalWaves());
        }
    }
}
