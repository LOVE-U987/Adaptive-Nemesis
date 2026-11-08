package com.adaptive_nemesis.adaptive_nemesismod.compat;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.affix.ItemAffixes;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * 神话 (Apotheosis) 兼容处理器
 *
 * 提供与神话模组的交互功能：
 * - 评估玩家装备上的神话词条、宝石槽位与已镶嵌宝石强度
 * - 计算神话加成强度
 *
 * 数据来源说明（F-32，基于 Apotheosis 1.21.1-8.5.2 实测解包确认）：
 * 1.21.1 的词条/宝石全部存储在物品 data components 中，旧的 NBT 键
 * （"apotheosis"、"affix_data"、"gems"）已不存在。实际使用的组件：
 * - apotheosis:affixes（{@link ItemAffixes}，词条集合，每条词条等级 0.0-2.0）
 * - apotheosis:sockets（Integer，空宝石槽数量）
 * - apotheosis:socketed_gems（{@link ItemContainerContents}，已镶嵌宝石物品堆叠）
 * - apotheosis:purity（{@link Purity}，宝石纯度档位，挂在宝石物品自身上）
 * 另有 apotheosis:rarity 组件（DynamicHolder<LootRarity>），但其取值类型的
 * 方法签名依赖 Placebo（当前不在编译类路径上），且词条数量与稀有度强相关，
 * 故本类以词条数量作为稀有度的强度代理，不直接读取稀有度组件。
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class ApotheosisCompat {

    /** 单条词条的强度贡献（词条数量是 1.21.1 格式下最稳定的品质信号） */
    private static final double AFFIX_STRENGTH = 3.0;

    /** 单个空宝石槽的强度贡献（槽位本身代表升级潜力） */
    private static final double EMPTY_SOCKET_STRENGTH = 1.0;

    /** 单颗已镶嵌宝石的基础强度贡献（与纯度无关的保底值） */
    private static final double GEM_BASE_STRENGTH = 2.0;

    /** 宝石纯度每提升一档（ordinal +1）追加的强度贡献 */
    private static final double GEM_PURITY_STEP = 1.5;

    /** "解析结果全 0"警告是否已输出（仅告警一次，避免日志刷屏） */
    private static boolean zeroResultWarned = false;

    /**
     * 默认构造函数（非空壳）
     * 工具类，方法由外部直接调用（如 PlayerStrengthEvaluator），不持有实例状态，故无需初始化。
     * 保留 public 构造以允许外部实例化。
     */
    public ApotheosisCompat() {}

    /**
     * 获取玩家的装备强度评估值（基于神话词条与宝石）
     *
     * 评估主手、副手与全部护甲槽位；所有槽位解析结果均为 0 时
     * 输出一次警告（仅一次），提示玩家可能穿着的是未经过神话
     * 生成的普通装备或神话数据包异常。
     *
     * @param player 目标玩家
     * @return 神话词条强度值
     */
    public double getPlayerGearStrength(ServerPlayer player) {
        double strength = 0.0;

        try {
            // 检查主手武器
            strength += evaluateApotheosisItem(player.getMainHandItem());

            // 检查副手物品
            strength += evaluateApotheosisItem(player.getOffhandItem());

            // 检查护甲
            for (ItemStack armor : player.getArmorSlots()) {
                strength += evaluateApotheosisItem(armor);
            }

        } catch (Exception e) {
            return 0.0;
        }

        if (strength <= 0.0) {
            warnOnceOnZeroResult();
        }

        return strength;
    }

    /**
     * 评估单个物品的神话词条强度
     *
     * 强度来源（均为 1.21.1 data components，见类注释）：
     * 1. 词条数量（apotheosis:affixes）；
     * 2. 空宝石槽数量（apotheosis:sockets）；
     * 3. 已镶嵌宝石数量与其纯度档位（apotheosis:socketed_gems + apotheosis:purity）。
     *
     * @param stack 物品堆叠
     * @return 神话词条强度贡献值
     */
    private double evaluateApotheosisItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }

        double value = 0.0;

        try {
            // 1. 词条（apotheosis:affixes 数据组件）
            ItemAffixes affixes = stack.get(Apoth.Components.AFFIXES);
            if (affixes != null) {
                value += affixes.size() * AFFIX_STRENGTH;
            }

            // 2. 空宝石槽位（apotheosis:sockets 数据组件）
            Integer sockets = stack.get(Apoth.Components.SOCKETS);
            if (sockets != null) {
                value += sockets * EMPTY_SOCKET_STRENGTH;
            }

            // 3. 已镶嵌宝石（apotheosis:socketed_gems 数据组件）
            value += evaluateSocketedGems(stack);

        } catch (Exception e) {
            // 组件解析失败时返回已计算的部分值
        }

        return value;
    }

    /**
     * 评估物品上已镶嵌宝石的强度
     *
     * 每颗宝石按基础值 + 纯度档位加成计分；宝石物品自身的
     * apotheosis:purity 组件缺失时按最低纯度（CRACKED，ordinal 0）处理。
     *
     * @param stack 携带宝石槽的物品堆叠
     * @return 已镶嵌宝石的强度贡献值
     */
    private double evaluateSocketedGems(ItemStack stack) {
        ItemContainerContents gems = stack.get(Apoth.Components.SOCKETED_GEMS);
        if (gems == null) {
            return 0.0;
        }

        double value = 0.0;
        for (int i = 0; i < gems.getSlots(); i++) {
            ItemStack gem = gems.getStackInSlot(i);
            if (gem.isEmpty()) {
                continue;
            }
            Purity purity = gem.get(Apoth.Components.PURITY);
            int purityIndex = purity != null ? purity.ordinal() : 0;
            value += GEM_BASE_STRENGTH + purityIndex * GEM_PURITY_STEP;
        }
        return value;
    }

    /**
     * 词条强度全为 0 时输出一次警告
     *
     * 仅在神话模组已加载（本类只会被 ModCompatManager 在
     * ModList 确认加载后实例化）且全部槽位解析为 0 时触发，
     * 用于暴露"兼容静默失效"类问题；只警告一次避免刷屏。
     */
    private static void warnOnceOnZeroResult() {
        if (zeroResultWarned) {
            return;
        }
        zeroResultWarned = true;
        AdaptiveNemesisMod.LOGGER.warn(
            "神话 (Apotheosis) 兼容已启用，但玩家装备的词条/宝石解析结果全部为 0；"
            + "若玩家确实持有神话装备，请检查神话数据包是否正常加载");
    }

    /**
     * 获取物品上的神话词条数量
     *
     * @param stack 物品堆叠
     * @return 词条数量
     */
    public int getAffixCount(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        try {
            ItemAffixes affixes = stack.get(Apoth.Components.AFFIXES);
            if (affixes != null) {
                return affixes.size();
            }
        } catch (Exception e) {
            return 0;
        }

        return 0;
    }

    /**
     * 获取物品的神话等级
     *
     * 1.21.1 数据组件格式中不存在"物品等级"组件：等级信息
     * 内嵌在每条词条上（0.0-2.0，需 Placebo 的 DynamicHolder 才能遍历，
     * 见类注释），因此本方法恒返回 0，仅为保持既有 API 兼容而保留。
     *
     * @param stack 物品堆叠
     * @return 恒为 0（1.21.1 格式无物品等级组件）
     */
    public int getItemLevel(ItemStack stack) {
        return 0;
    }
}
