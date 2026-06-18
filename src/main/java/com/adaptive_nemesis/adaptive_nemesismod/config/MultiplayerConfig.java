package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 多人联机配置
 */
public class MultiplayerConfig {

    /**
    * 区域同步范围（区块）
    */
    public final ModConfigSpec.IntValue AREA_SYNC_RANGE;

    public MultiplayerConfig(ModConfigSpec.Builder builder) {
        builder.push("multiplayer");
        AREA_SYNC_RANGE = builder.comment("区域同步范围（区块）- 同一区域内取在线玩家平均难度").defineInRange("areaSyncRange", 8, 1, 32);
        builder.pop();
    }
}