package com.adaptive_nemesis.adaptive_nemesismod.boss;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Boss 识别统一服务
 * 
 * 提供全局统一的 Boss 识别能力，所有模块通过此服务判断 Boss。
 * 采用策略模式 + 责任链模式，支持多种识别策略组合使用。
 * 策略初始化参数从配置文件加载，支持运行时热重载。
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class BossIdentificationService {

    private static BossIdentificationService INSTANCE;
    private static boolean initialized = false;

    private final BossIdentifierChain identifierChain;

    /**
     * 创建 Boss 识别服务
     * 
     * @param identifierChain 配置好的 Boss 识别责任链
     */
    BossIdentificationService(BossIdentifierChain identifierChain) {
        this.identifierChain = identifierChain;
    }

    /**
     * 初始化 Boss 识别服务（从配置加载策略）
     * 
     * 按优先级创建识别策略：
     * 1. TagBasedBossIdentifier - BOSS 标签识别（最高优先级）
     * 2. TypeBasedBossIdentifier - 类型识别
     * 3. NameBasedBossIdentifier - 名称识别
     * 4. HealthThresholdBossIdentifier - 血量阈值识别（最低优先级）
     * 
     * @return 如果初始化成功返回 true，如果配置未加载则返回 false
     */
    public static synchronized boolean initialize() {
        if (initialized) {
            return true;
        }
        
        // 如果配置未加载，延迟初始化
        if (!isConfigLoaded()) {
            return false;
        }

        List<BossIdentifier> identifiers = new ArrayList<>();

        // 1. BOSS 标签识别策略（最高优先级）
        // 通过实体类型标签判断是否为 Boss，最精准可靠的方式
        identifiers.add(new TagBasedBossIdentifier());

        // 2. 类型识别策略
        identifiers.add(new TypeBasedBossIdentifier());

        // 3. 名称识别策略 - 配置已确保加载
        Set<String> bossKeywords = parseKeywords(Config.BOSS_IDENTIFICATION_KEYWORDS.get());
        identifiers.add(new NameBasedBossIdentifier(bossKeywords));

        // 4. 血量阈值识别策略 - 配置已确保加载
        double healthThreshold = Config.BOSS_HEALTH_THRESHOLD.get();
        identifiers.add(new HealthThresholdBossIdentifier(healthThreshold));

        BossIdentifierChain chain = new BossIdentifierChain(identifiers);
        INSTANCE = new BossIdentificationService(chain);
        initialized = true;
        return true;
    }

    /**
     * 检查配置是否已加载完成
     * 
     * @return 如果配置已加载返回 true，否则返回 false
     */
    public static boolean isConfigLoaded() {
        // 通过检查 AdaptiveNemesisMod 的 configLoaded 标志来判断
        // 这比检查 Config.MOD_CONFIG 更可靠，因为 MOD_CONFIG 可能在事件处理中为 null
        try {
            java.lang.reflect.Field field = AdaptiveNemesisMod.class.getDeclaredField("configLoaded");
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (Exception e) {
            // 反射失败时回退到检查 MOD_CONFIG
            return Config.MOD_CONFIG != null;
        }
    }

    /**
     * 检查初始化是否完成
     * 
     * @return 如果初始化完成返回 true，否则返回 false
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 解析配置中的关键词字符串
     * 
     * @param configValue 逗号分隔的关键词字符串
     * @return 关键词集合
     */
    private static Set<String> parseKeywords(String configValue) {
        Set<String> keywords = new HashSet<>();
        if (configValue != null && !configValue.trim().isEmpty()) {
            Arrays.stream(configValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(keywords::add);
        }
        return keywords;
    }

    /**
     * 获取单例实例（延迟初始化）
     * 
     * 注意：此方法不会主动触发初始化，初始化应在配置加载完成后的
     * onModConfigEvent 中进行。如果调用此方法时配置未加载，将返回 null。
     * 
     * @return Boss 识别服务实例，如果未初始化则返回 null
     */
    public static BossIdentificationService getInstance() {
        return INSTANCE;
    }

    /**
     * 判断实体是否为 Boss
     * 
     * @param entity 目标实体
     * @return 如果是 Boss 返回 true
     */
    public boolean isBoss(LivingEntity entity) {
        return identifierChain.isBoss(entity);
    }

    /**
     * 获取 Boss 类型标识
     * 
     * @param entity Boss 实体
     * @return Boss 类型标识字符串，如果无法识别返回 null
     */
    public String getBossType(LivingEntity entity) {
        return identifierChain.getBossType(entity);
    }

    /**
     * 强制重新初始化（配置热重载时调用）
     */
    public static synchronized void reinitialize() {
        initialized = false;
        initialize();
    }
    
    /**
     * 重试初始化（当配置刚被加载时调用）
     * 
     * @return 如果初始化成功返回 true，如果配置未加载则返回 false
     */
    public static synchronized boolean retryInitialize() {
        if (initialized) {
            return true;
        }
        
        if (!isConfigLoaded()) {
            return false;
        }
        
        return initialize();
    }
}
