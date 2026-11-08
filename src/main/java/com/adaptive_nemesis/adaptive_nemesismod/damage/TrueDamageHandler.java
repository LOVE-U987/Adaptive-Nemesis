package com.adaptive_nemesis.adaptive_nemesismod.damage;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSEventTrigger;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.lang.reflect.Field;

/**
 * 真实伤害转化处理器
 *
 * 铁乌龟终结者机制：
 * 针对高护甲玩家，将部分伤害强制转化为真实伤害（无视护甲）
 *
 * 真实伤害比例基于护甲值相对于基准护甲的倍率计算：
 * - 护甲值 <= 基准护甲 → 基础比例
 * - 护甲值 > 基准护甲 → 按比例增加真实伤害
 *
 * 伤害源与归因策略（P-13）：
 * 使用模组注册的 true_damage 伤害类型构建 DamageSource，归因指向攻击方：
 * - direct = 直接施加方（近战场景为原始攻击者实体；投射物场景为投射物本体）
 * - causing = 归因实体（原始攻击者或投射物拥有者）
 * 保证死亡消息、仇恨（lastHurtByMob 取 direct）与兼容模组归因指向攻击方而非受击玩家。
 * 无攻击者的伤害源不进入转化逻辑（入口过滤），因此不会产生无归因的真实伤害源。
 *
 * 无敌帧处理：
 * 1.21.1 的 hurt 结算在 invulnerableTime > 10 时只结算超出 lastHurt 的差值，
 * 且没有 bypasses_cooldown 标签消费点（该标签自 1.21.2 引入），
 * 延迟一 tick 施加的真实伤害会被无敌帧吞掉；延迟任务中反射清零无敌帧兜底。
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class TrueDamageHandler {

    /**
     * 单例实例
     */
    private static TrueDamageHandler INSTANCE;

    /**
     * 初始化状态标记
     */
    private static boolean initialized = false;

    /**
     * invulnerableTime 字段反射缓存（1.21.1 无公开访问器）
     */
    private static volatile Field invulnerableTimeField;

    /**
     * 字段解析是否已尝试过（含失败；失败后不再重试，行为回退为原版无敌帧结算）
     */
    private static volatile boolean invulnerableFieldResolved = false;

    /**
     * 标记为已初始化（在配置加载后调用）
     */
    public static void markInitialized() {
        initialized = true;
    }

    /**
     * 检查是否已初始化
     *
     * @return 如果已初始化返回 true，否则返回 false
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 私有构造函数 - 单例模式
     */
    private TrueDamageHandler() {}

    /**
     * 获取单例实例
     *
     * @return TrueDamageHandler 实例
     */
    public static synchronized TrueDamageHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TrueDamageHandler();
        }
        return INSTANCE;
    }

    /**
     * 处理实体受到伤害事件
     *
     * 守卫链依次过滤：再入排除（true_damage 源）、初始化门槛、配置开关、
     * 玩家目标、生物攻击者、已无视护甲的伤害源，通过后进入转化计算。
     *
     * @param event 实体受到伤害事件
     */
    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        // 再入排除：延迟任务施加的真实伤害会再次触发本事件，直接跳过防止二次转化
        if (isTrueDamageSource(event.getSource())) {
            return;
        }

        // 检查是否已初始化 (配置可能还未加载完成)
        if (!isInitialized()) {
            return;
        }

        // 检查是否启用真实伤害
        if (!Config.ENABLE_TRUE_DAMAGE.get()) {
            return;
        }

        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        // 只处理玩家受到伤害的情况
        if (!(target instanceof ServerPlayer player)) {
            return;
        }

        // 检查伤害来源是否是生物攻击
        if (source.getEntity() == null || !(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        // 跳过已无视护甲的伤害源
        // 这些伤害已经不受护甲减免，无需再转化，也可避免递归或重复计算
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }

        // 获取玩家护甲值并计算真实伤害比例
        double armorValue = player.getAttributeValue(Attributes.ARMOR);
        double trueDamagePercent = getTrueDamagePercent(armorValue);
        if (trueDamagePercent <= 0) {
            return;
        }

        applyTrueDamageConversion(event, player, attacker, source, armorValue, trueDamagePercent);
    }

    /**
     * 判断伤害源是否为本模组的真实伤害类型
     * 提取为静态方法以便再入排除逻辑复用与单元测试
     *
     * @param source 伤害源
     * @return 如果是真实伤害类型返回 true
     */
    static boolean isTrueDamageSource(DamageSource source) {
        return source != null && source.is(ModDamageTypes.TRUE_DAMAGE);
    }

    /**
     * 执行真实伤害转化计算与施加
     *
     * 先经 KubeJS 事件修正转化金额（是否取消由事件的显式取消标记决定），再拆分普通部分与
     * 真实部分：普通部分留在当前事件，真实部分延迟一 tick 施加。
     *
     * @param event             实体受到伤害事件
     * @param player            受击玩家
     * @param attacker          原始攻击者
     * @param source            原始伤害源
     * @param armorValue        玩家护甲值
     * @param trueDamagePercent 真实伤害百分比
     */
    private void applyTrueDamageConversion(
        LivingIncomingDamageEvent event,
        ServerPlayer player,
        LivingEntity attacker,
        DamageSource source,
        double armorValue,
        double trueDamagePercent
    ) {
        float originalDamage = event.getAmount();
        float trueDamageAmount = originalDamage * (float) (trueDamagePercent / 100.0);

        // 触发 KubeJS 伤害计算事件（取消标记显式返回，避免与"全额真实伤害"的数值相等冲突）
        KubeJSEventTrigger.DamageCalculationResult calculationResult =
            KubeJSEventTrigger.triggerDamageCalculation(
                attacker, player, originalDamage, trueDamageAmount,
                armorValue / Config.LOW_ARMOR_THRESHOLD.get()
            );

        // 只有脚本显式调用 cancelEvent() 才取消真实伤害转换
        if (calculationResult.isCancelled()) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "玩家 {} 的真实伤害转化被 KubeJS 事件取消",
                    player.getName().getString()
                );
            }
            return;
        }

        float finalTrueDamage = calculationResult.getDamage();
        float reducedDamage = originalDamage - finalTrueDamage;

        // 真实伤害金额过小则不拆分，保持原始伤害
        if (finalTrueDamage <= 0.01f) {
            return;
        }

        // 普通部分留在当前事件结算，真实部分延迟施加
        event.setAmount(reducedDamage);
        scheduleTrueDamage(player, source, finalTrueDamage);

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 受到真实伤害转化: 原始={}, 真实比例={}%, 真实伤害={}, 剩余={}",
                player.getName().getString(),
                String.format("%.2f", originalDamage),
                String.format("%.1f", trueDamagePercent),
                String.format("%.2f", finalTrueDamage),
                String.format("%.2f", reducedDamage)
            );
        }
    }

    /**
     * 延迟一 tick 施加真实伤害
     *
     * 延迟执行避免在伤害事件处理中重入 hurt 造成递归；
     * 执行前复查玩家存活状态并做服务端实例判空（玩家断线等场景放弃施加）。
     * 施加前反射清零无敌帧，保证真实伤害全额生效（见类注释的无敌帧说明）。
     *
     * @param player           受击玩家
     * @param source           原始伤害源（用于提取归因实体）
     * @param trueDamageAmount 真实伤害金额
     */
    private void scheduleTrueDamage(ServerPlayer player, DamageSource source, float trueDamageAmount) {
        // 归因策略（P-13）：direct=直接施加方（近战为原始攻击者，投射物为投射物本体），
        // causing=归因实体（原始攻击者或其拥有者）；入口已过滤无攻击者场景
        Entity directEntity = source.getDirectEntity();
        if (directEntity == null) {
            directEntity = source.getEntity();
        }
        DamageSource trueDamageSource = player.level().damageSources().source(
            ModDamageTypes.TRUE_DAMAGE, directEntity, source.getEntity()
        );

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        server.execute(() -> {
            if (player.isAlive()) {
                resetInvulnerableTime(player);
                player.hurt(trueDamageSource, trueDamageAmount);
            }
        });
    }

    /**
     * 反射清零玩家的伤害无敌帧
     *
     * 1.21.1 的 hurt 结算在 invulnerableTime > 10 时只结算超出 lastHurt 的差值，
     * 延迟一 tick 的真实伤害会被无敌帧吞掉；清零后 hurt 走完整结算路径。
     * 字段解析失败时仅告警一次并跳过清零（行为回退为原版无敌帧结算）。
     *
     * @param player 目标玩家
     */
    private static void resetInvulnerableTime(ServerPlayer player) {
        if (!invulnerableFieldResolved) {
            try {
                Field field = LivingEntity.class.getDeclaredField("invulnerableTime");
                field.setAccessible(true);
                invulnerableTimeField = field;
            } catch (ReflectiveOperationException e) {
                AdaptiveNemesisMod.LOGGER.warn(
                    "无法访问无敌帧字段，真实伤害将按原版无敌帧规则结算: {}", e.getMessage()
                );
            }
            invulnerableFieldResolved = true;
        }

        Field field = invulnerableTimeField;
        if (field == null) {
            return;
        }
        try {
            field.setInt(player, 0);
        } catch (IllegalAccessException e) {
            // 单次清零失败不影响主流程，真实伤害仍会按差值规则尝试结算
        }
    }

    /**
     * 根据护甲值获取真实伤害比例
     *
     * 按配置中的低/中/高护甲阈值分段计算真实伤害比例，
     * 使真实伤害与护甲等级匹配，避免线性插值偏离设计预期。
     *
     * @param armorValue 玩家护甲值
     * @return 真实伤害百分比
     */
    public double getTrueDamagePercent(double armorValue) {
        return calculateTrueDamagePercent(
            armorValue,
            Config.LOW_ARMOR_THRESHOLD.get(),
            Config.LOW_ARMOR_TRUE_DAMAGE_PERCENT.get(),
            Config.MEDIUM_ARMOR_THRESHOLD.get(),
            Config.MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT.get(),
            Config.HIGH_ARMOR_THRESHOLD.get(),
            Config.HIGH_ARMOR_TRUE_DAMAGE_PERCENT.get(),
            Config.TURTLE_TRUE_DAMAGE_PERCENT.get()
        );
    }

    /**
     * 计算真实伤害比例（纯静态逻辑，便于单元测试）
     *
     * @param armorValue     玩家护甲值
     * @param lowThreshold   低护甲阈值
     * @param lowPercent     低护甲真实伤害比例
     * @param mediumThreshold 中护甲阈值
     * @param mediumPercent  中护甲真实伤害比例
     * @param highThreshold  高护甲阈值
     * @param highPercent    高护甲真实伤害比例
     * @param turtlePercent  超高护甲真实伤害比例
     * @return 真实伤害百分比
     */
    public static double calculateTrueDamagePercent(
        double armorValue,
        double lowThreshold, double lowPercent,
        double mediumThreshold, double mediumPercent,
        double highThreshold, double highPercent,
        double turtlePercent
    ) {
        if (armorValue <= lowThreshold) {
            return lowPercent;
        }
        if (armorValue <= mediumThreshold) {
            return lerp(armorValue, lowThreshold, mediumThreshold, lowPercent, mediumPercent);
        }
        if (armorValue <= highThreshold) {
            return lerp(armorValue, mediumThreshold, highThreshold, mediumPercent, highPercent);
        }
        return turtlePercent;
    }

    /**
     * 线性插值辅助方法
     *
     * @param value 当前值
     * @param minValue 区间下限
     * @param maxValue 区间上限
     * @param minResult 区间下限对应结果
     * @param maxResult 区间上限对应结果
     * @return 插值结果
     */
    private static double lerp(double value, double minValue, double maxValue, double minResult, double maxResult) {
        if (maxValue <= minValue) {
            return minResult;
        }
        double t = (value - minValue) / (maxValue - minValue);
        return minResult + t * (maxResult - minResult);
    }

    /**
     * 获取护甲等级描述
     *
     * @param armorValue 玩家护甲值
     * @return 护甲等级描述组件（已本地化）
     */
    public Component getArmorLevelDescription(double armorValue) {
        double baseArmor = Config.LOW_ARMOR_THRESHOLD.get();
        double armorMultiplier = armorValue / baseArmor;

        if (armorMultiplier <= 1.0) {
            return Component.translatable("adaptive_nemesis.armor_level.standard");
        } else if (armorMultiplier <= 2.0) {
            return Component.translatable("adaptive_nemesis.armor_level.reinforced");
        } else if (armorMultiplier <= 3.0) {
            return Component.translatable("adaptive_nemesis.armor_level.high");
        } else if (armorMultiplier <= 5.0) {
            return Component.translatable("adaptive_nemesis.armor_level.turtle");
        } else {
            return Component.translatable("adaptive_nemesis.armor_level.ultimate_turtle");
        }
    }
}
