package com.adaptive_nemesis.adaptive_nemesismod.nemesis;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;

import java.util.*;

/**
 * 宿敌名称生成器
 *
 * 根据敌人的类型和强化属性生成中世纪魔法风格的宿敌称号
 * 称号文本从语言文件中读取，支持多语言本地化
 * 如："法师克星—弑神者"、"近战克星—暗影领主"等
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
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
     * 称号前缀语言文件基础键
     */
    private static final String PREFIX_MELEE_KEY = "adaptive_nemesis.nemesis.name.prefix.melee";
    private static final String PREFIX_RANGED_KEY = "adaptive_nemesis.nemesis.name.prefix.ranged";
    private static final String PREFIX_MAGIC_KEY = "adaptive_nemesis.nemesis.name.prefix.magic";

    /**
     * 称号后缀语言文件基础键
     */
    private static final String SUFFIX_NORMAL_KEY = "adaptive_nemesis.nemesis.name.suffix.normal";
    private static final String SUFFIX_LEGENDARY_KEY = "adaptive_nemesis.nemesis.name.suffix.legendary";
    private static final String SUFFIX_EPIC_KEY = "adaptive_nemesis.nemesis.name.suffix.epic";

    /**
     * 翻译键数量缓存，避免重复探测
     */
    private final Map<String, Integer> translationCounts = new HashMap<>();

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

        Component prefix = determinePrefix(entity);
        Component suffix = determineSuffix(multiplier);
        return Component.translatable("adaptive_nemesis.nemesis.name.format", prefix, suffix)
            .withStyle(ChatFormatting.RED);
    }

    /**
     * 根据敌人类型决定称号前缀
     *
     * @param entity 敌人实体
     * @return 称号前缀组件
     */
    private Component determinePrefix(Monster entity) {
        EntityType<?> type = entity.getType();
        List<String> availableConfigPrefixes = new ArrayList<>();

        String magicConfig = Config.MAGIC_NEMESIS_PREFIXES.get();
        String rangedConfig = Config.RANGED_NEMESIS_PREFIXES.get();
        String meleeConfig = Config.MELEE_NEMESIS_PREFIXES.get();

        if (MAGIC_TYPES.contains(type) && magicConfig != null && !magicConfig.isEmpty()) {
            availableConfigPrefixes.addAll(Arrays.asList(magicConfig.split(",")));
        }
        if (RANGED_TYPES.contains(type) && rangedConfig != null && !rangedConfig.isEmpty()) {
            availableConfigPrefixes.addAll(Arrays.asList(rangedConfig.split(",")));
        }
        if (MELEE_TYPES.contains(type) && meleeConfig != null && !meleeConfig.isEmpty()) {
            availableConfigPrefixes.addAll(Arrays.asList(meleeConfig.split(",")));
        }

        if (!availableConfigPrefixes.isEmpty()) {
            return Component.literal(availableConfigPrefixes.get(random.nextInt(availableConfigPrefixes.size())));
        }

        // 未配置覆盖时使用语言文件中的翻译键
        List<String> baseKeys = new ArrayList<>();
        if (MAGIC_TYPES.contains(type)) {
            baseKeys.add(PREFIX_MAGIC_KEY);
        }
        if (RANGED_TYPES.contains(type)) {
            baseKeys.add(PREFIX_RANGED_KEY);
        }
        if (MELEE_TYPES.contains(type) || baseKeys.isEmpty()) {
            baseKeys.add(PREFIX_MELEE_KEY);
        }

        List<String> candidateKeys = new ArrayList<>();
        for (String baseKey : baseKeys) {
            int count = getTranslationCount(baseKey);
            for (int i = 0; i < count; i++) {
                candidateKeys.add(baseKey + "." + i);
            }
        }

        if (candidateKeys.isEmpty()) {
            return Component.empty();
        }
        return Component.translatable(candidateKeys.get(random.nextInt(candidateKeys.size())));
    }

    /**
     * 根据强化倍率决定称号后缀
     * 倍率越高，称号越强大
     *
     * @param multiplier 强化倍率
     * @return 称号后缀组件
     */
    private Component determineSuffix(double multiplier) {
        String configValue = Config.NEMESIS_SUFFIXES.get();
        if (configValue != null && !configValue.isEmpty()) {
            List<String> suffixes = Arrays.asList(configValue.split(","));
            return Component.literal(suffixes.get(random.nextInt(suffixes.size())));
        }

        String tierKey;
        if (multiplier >= 3.0) {
            tierKey = SUFFIX_EPIC_KEY;
        } else if (multiplier >= 2.5) {
            tierKey = SUFFIX_LEGENDARY_KEY;
        } else {
            tierKey = SUFFIX_NORMAL_KEY;
        }

        int count = getTranslationCount(tierKey);
        if (count == 0) {
            return Component.empty();
        }
        return Component.translatable(tierKey + "." + random.nextInt(count));
    }

    /**
     * 获取指定基础键下的翻译条目数量
     *
     * @param baseKey 基础翻译键
     * @return 可用条目数量
     */
    private int getTranslationCount(String baseKey) {
        Integer cached = translationCounts.get(baseKey);
        if (cached != null) {
            return cached;
        }

        int count = 0;
        while (true) {
            String key = baseKey + "." + count;
            String resolved = Component.translatable(key).getString();
            if (resolved.equals(key)) {
                break;
            }
            count++;
        }
        translationCounts.put(baseKey, count);
        return count;
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
