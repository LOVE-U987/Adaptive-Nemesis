package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 其他模组兼容性配置
 */
public class ModCompatConfig {

    /**
    * 是否启用 L2Hostility (莱特兰恶意) 兼容模式
    * 启用后，当 L2Hostility 加载时，自适应模组会跳过血量与速度缩放，
    * 交由 L2Hostility 管理，防止 ADD_MULTIPLIED_TOTAL 导致血量爆炸
    */
    public final ForgeConfigSpec.BooleanValue MOD_COMPAT_L2HOSTILITY_ENABLED;

    /**
    * 是否启用史诗战斗 (Epic Fight) 兼容模式
    * 启用后，会应用专用权重计算逻辑，防止怪物被击飞过远
    */
    public final ForgeConfigSpec.BooleanValue MOD_COMPAT_EPIC_FIGHT_ENABLED;

    /**
    * 是否启用铁魔法 (Irons Spells) 兼容模式
    * 启用后，会抑制刷屏式 DEBUG 日志输出
    */
    public final ForgeConfigSpec.BooleanValue MOD_COMPAT_IRONS_SPELLS_ENABLED;

    /**
    * 是否启用神化 (Apotheosis) 兼容模式
    * 启用后会在玩家强度评估中考虑神话词条加成
    */
    public final ForgeConfigSpec.BooleanValue MOD_COMPAT_APOTHEOSIS_ENABLED;

    public ModCompatConfig(ForgeConfigSpec.Builder builder) {
        builder.push("modCompatibility");
        MOD_COMPAT_L2HOSTILITY_ENABLED = builder.comment("是否启用 L2Hostility (莱特兰恶意) 兼容模式").comment("启用后跳过血量与速度缩放，防止 ADD_MULTIPLIED_TOTAL 导致血量爆炸").comment("Enable L2Hostility compat mode - skips health/speed scaling to prevent HP explosion").define("modCompatL2HostilityEnabled", true);
        MOD_COMPAT_EPIC_FIGHT_ENABLED = builder.comment("是否启用史诗战斗 (Epic Fight) 兼容模式").comment("启用后使用专用权重计算，防止怪物被击飞过远").comment("Enable Epic Fight compat mode - uses specialized weight calculation").define("modCompatEpicFightEnabled", true);
        MOD_COMPAT_IRONS_SPELLS_ENABLED = builder.comment("是否启用铁魔法 (Irons Spells) 兼容模式").comment("启用后抑制刷屏式 DEBUG 日志输出").comment("Enable Irons Spells compat mode - suppresses spamming DEBUG logs").define("modCompatIronsSpellsEnabled", true);
        MOD_COMPAT_APOTHEOSIS_ENABLED = builder.comment("是否启用神化 (Apotheosis) 兼容模式").comment("启用后会在玩家强度评估中考虑神话词条加成").comment("Enable Apotheosis compat mode - includes mythic affix bonuses in player strength evaluation").define("modCompatApotheosisEnabled", true);
        builder.pop();
    }
}