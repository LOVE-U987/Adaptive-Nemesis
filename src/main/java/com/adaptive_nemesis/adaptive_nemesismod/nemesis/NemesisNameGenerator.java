package com.adaptive_nemesis.adaptive_nemesismod.nemesis;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 宿敌名称生成器
 * 
 * 根据敌人的类型和强化属性生成中世纪魔法风格的宿敌称号
 * 如："法师克星—弑神者"、"近战克星—暗影领主"等
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NemesisNameGenerator {

    /**
     * 近战类型实体列表
     */
    private static final Set<EntityType<?>> MELEE_TYPES = Set.of(
        EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
        EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.ZOMBIE_VILLAGER,
        EntityType.HUSK, EntityType.DROWNED, EntityType.VINDICATOR,
        EntityType.EVOKER, EntityType.PILLAGER, EntityType.RAVAGER,
        EntityType.WITCH, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
        EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.GUARDIAN,
        EntityType.ELDER_GUARDIAN, EntityType.SHULKER, EntityType.SILVERFISH,
        EntityType.ENDERMITE, EntityType.POLAR_BEAR, EntityType.WOLF
    );

    /**
     * 远程类型实体列表
     */
    private static final Set<EntityType<?>> RANGED_TYPES = Set.of(
        EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON,
        EntityType.PILLAGER, EntityType.WITCH, EntityType.GHAST,
        EntityType.SHULKER, EntityType.BLAZE, EntityType.VEX
    );

    /**
     * 魔法类型实体列表
     */
    private static final Set<EntityType<?>> MAGIC_TYPES = Set.of(
        EntityType.WITCH, EntityType.EVOKER, EntityType.VEX, EntityType.SHULKER,
        EntityType.BLAZE, EntityType.GHAST, EntityType.WITHER,
        EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.ELDER_GUARDIAN
    );

    /**
     * 近战强化称号前缀
     */
    private static final List<String> DEFAULT_MELEE_PREFIXES = Arrays.asList(
        "近战克星", "战神", "屠夫", "狂战士", "嗜血者",
        "毁灭者", "斩击者", "狂暴者", "无畏勇士", "死亡使者",
        "杀戮机器", "血腥猎手", "利刃舞者", "钢铁战士", "暗影刺客"
    );

    /**
     * 远程强化称号前缀
     */
    private static final List<String> DEFAULT_RANGED_PREFIXES = Arrays.asList(
        "弓术克星", "狙击者", "鹰眼", "暗影猎手", "迅捷杀手",
        "远程大师", "箭雨使者", "冰霜射手", "火焰射手", "致命射手",
        "黑暗射手", "风暴使者", "精准打击", "致命距离", "无声猎手"
    );

    /**
     * 魔法强化称号前缀
     */
    private static final List<String> DEFAULT_MAGIC_PREFIXES = Arrays.asList(
        "魔法克星", "弑神者", "咒术师", "虚空行者", "奥术大师",
        "黑暗法师", "元素使者", "灵魂收割者", "死灵法师", "混沌法师",
        "暗影巫师", "深渊行者", "符文大师", "魔力主宰", "虚空领主"
    );

    /**
     * 通用称号后缀
     */
    private static final List<String> DEFAULT_SUFFIXES = Arrays.asList(
        "—末日使者", "—暗影领主", "—死亡骑士", "—深渊行者", "—地狱使者",
        "—黑暗先知", "—亡灵统帅", "—恐惧化身", "—毁灭化身", "—虚空使者",
        "—暗影主宰", "—亡灵君主", "—地狱领主", "—黑暗君王", "—深渊领主",
        "—死亡主宰", "—毁灭领主", "—恐惧领主", "—混沌领主", "—虚空领主"
    );

    private final Random random;

    /**
     * 构造函数
     * 使用默认随机种子
     */
    public NemesisNameGenerator() {
        this.random = new Random();
    }

    /**
     * 使用指定随机种子构造
     * 
     * @param seed 随机种子
     */
    public NemesisNameGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * 根据敌人类型和强化属性生成宿敌名称
     * 
     * @param entity 敌人实体
     * @param multiplier 强化倍率
     * @return 格式化的宿敌名称组件
     */
    public Component generateNemesisName(Monster entity, double multiplier) {
        if (!Config.SHOW_NEMESIS_NAME.get()) {
            return Component.empty();
        }

        String prefix = determinePrefix(entity);
        String suffix = determineSuffix(multiplier);
        String fullName = prefix + suffix;

        return createColoredComponent(fullName);
    }

    /**
     * 根据敌人类型决定称号前缀
     * 
     * @param entity 敌人实体
     * @return 称号前缀
     */
    private String determinePrefix(Monster entity) {
        EntityType<?> type = entity.getType();
        List<String> availablePrefixes = new ArrayList<>();

        if (MAGIC_TYPES.contains(type)) {
            availablePrefixes.addAll(getMagicPrefixes());
        }
        if (RANGED_TYPES.contains(type)) {
            availablePrefixes.addAll(getRangedPrefixes());
        }
        if (MELEE_TYPES.contains(type) || availablePrefixes.isEmpty()) {
            availablePrefixes.addAll(getMeleePrefixes());
        }

        if (availablePrefixes.isEmpty()) {
            availablePrefixes.addAll(DEFAULT_MELEE_PREFIXES);
        }

        return availablePrefixes.get(random.nextInt(availablePrefixes.size()));
    }

    /**
     * 根据强化倍率决定称号后缀
     * 倍率越高，称号越强大
     * 
     * @param multiplier 强化倍率
     * @return 称号后缀
     */
    private String determineSuffix(double multiplier) {
        List<String> suffixes = getSuffixes();
        
        if (multiplier >= 3.0) {
            List<String> epicSuffixes = suffixes.stream()
                .filter(s -> s.contains("主宰") || s.contains("君主") || s.contains("弑神") || s.contains("毁灭"))
                .collect(Collectors.toList());
            if (!epicSuffixes.isEmpty()) {
                return epicSuffixes.get(random.nextInt(epicSuffixes.size()));
            }
        } else if (multiplier >= 2.5) {
            List<String> legendarySuffixes = suffixes.stream()
                .filter(s -> s.contains("领主") || s.contains("化身") || s.contains("统帅"))
                .collect(Collectors.toList());
            if (!legendarySuffixes.isEmpty()) {
                return legendarySuffixes.get(random.nextInt(legendarySuffixes.size()));
            }
        }

        return suffixes.get(random.nextInt(suffixes.size()));
    }

    /**
     * 创建带颜色的名称组件
     * 
     * @param name 名称文本
     * @return 格式化的组件
     */
    private Component createColoredComponent(String name) {
        return Component.literal(name)
            .withStyle(ChatFormatting.RED);
    }

    /**
     * 获取近战称号前缀列表
     * 
     * @return 前缀列表
     */
    private List<String> getMeleePrefixes() {
        String configValue = Config.MELEE_NEMESIS_PREFIXES.get();
        if (configValue == null || configValue.isEmpty()) {
            return DEFAULT_MELEE_PREFIXES;
        }
        return Arrays.asList(configValue.split(","));
    }

    /**
     * 获取远程称号前缀列表
     * 
     * @return 前缀列表
     */
    private List<String> getRangedPrefixes() {
        String configValue = Config.RANGED_NEMESIS_PREFIXES.get();
        if (configValue == null || configValue.isEmpty()) {
            return DEFAULT_RANGED_PREFIXES;
        }
        return Arrays.asList(configValue.split(","));
    }

    /**
     * 获取魔法称号前缀列表
     * 
     * @return 前缀列表
     */
    private List<String> getMagicPrefixes() {
        String configValue = Config.MAGIC_NEMESIS_PREFIXES.get();
        if (configValue == null || configValue.isEmpty()) {
            return DEFAULT_MAGIC_PREFIXES;
        }
        return Arrays.asList(configValue.split(","));
    }

    /**
     * 获取称号后缀列表
     * 
     * @return 后缀列表
     */
    private List<String> getSuffixes() {
        String configValue = Config.NEMESIS_SUFFIXES.get();
        if (configValue == null || configValue.isEmpty()) {
            return DEFAULT_SUFFIXES;
        }
        return Arrays.asList(configValue.split(","));
    }

    /**
     * 判断敌人是否应该获得魔法前缀
     * 
     * @param type 实体类型
     * @return 是否为魔法类型
     */
    public static boolean isMagicType(EntityType<?> type) {
        return MAGIC_TYPES.contains(type);
    }

    /**
     * 判断敌人是否应该获得远程前缀
     * 
     * @param type 实体类型
     * @return 是否为远程类型
     */
    public static boolean isRangedType(EntityType<?> type) {
        return RANGED_TYPES.contains(type);
    }

    /**
     * 判断敌人是否应该获得近战前缀
     * 
     * @param type 实体类型
     * @return 是否为近战类型
     */
    public static boolean isMeleeType(EntityType<?> type) {
        return MELEE_TYPES.contains(type);
    }

    /**
     * 获取当前使用的随机数生成器
     * 
     * @return 随机数生成器
     */
    public Random getRandom() {
        return random;
    }
}
