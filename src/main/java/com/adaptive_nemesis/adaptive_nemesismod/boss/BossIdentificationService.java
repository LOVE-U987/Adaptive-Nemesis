package com.adaptive_nemesis.adaptive_nemesismod.boss;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Boss识别统一服务
 * 
 * 提供全局统一的Boss识别能力，所有模块通过此服务判断Boss。
 * 采用策略模式 + 责任链模式，支持多种识别策略组合使用。
 * 策略初始化参数从配置文件加载，支持运行时热重载。
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class BossIdentificationService {

    private static BossIdentificationService INSTANCE;

    private final BossIdentifierChain identifierChain;

    /**
     * 创建Boss识别服务
     * 
     * @param identifierChain 配置好的Boss识别责任链
     */
    BossIdentificationService(BossIdentifierChain identifierChain) {
        this.identifierChain = identifierChain;
    }

    /**
     * 初始化Boss识别服务（从配置加载策略）
     * 
     * 按优先级创建识别策略：
     * 1. TypeBasedBossIdentifier - 类型识别（最高优先级）
     * 2. NameBasedBossIdentifier - 名称识别
     * 3. HealthThresholdBossIdentifier - 血量阈值识别
     */
    public static synchronized void initialize() {
        if (INSTANCE != null) {
            return;
        }

        List<BossIdentifier> identifiers = new ArrayList<>();

        // 1. 类型识别策略（最高优先级）
        identifiers.add(new TypeBasedBossIdentifier());

        // 2. 名称识别策略
        Set<String> bossKeywords = parseKeywords(Config.BOSS_IDENTIFICATION_KEYWORDS.get());
        identifiers.add(new NameBasedBossIdentifier(bossKeywords));

        // 3. 血量阈值识别策略（最低优先级）
        double healthThreshold = Config.BOSS_HEALTH_THRESHOLD.get();
        identifiers.add(new HealthThresholdBossIdentifier(healthThreshold));

        BossIdentifierChain chain = new BossIdentifierChain(identifiers);
        INSTANCE = new BossIdentificationService(chain);
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
     * 获取单例实例
     * 
     * @return Boss识别服务实例
     */
    public static BossIdentificationService getInstance() {
        if (INSTANCE == null) {
            initialize();
        }
        return INSTANCE;
    }

    /**
     * 判断实体是否为Boss
     * 
     * @param entity 目标实体
     * @return 如果是Boss返回true
     */
    public boolean isBoss(LivingEntity entity) {
        return identifierChain.isBoss(entity);
    }

    /**
     * 获取Boss类型标识
     * 
     * @param entity Boss实体
     * @return Boss类型标识字符串，如果无法识别返回null
     */
    public String getBossType(LivingEntity entity) {
        return identifierChain.getBossType(entity);
    }

    /**
     * 强制重新初始化（配置热重载时调用）
     */
    public static synchronized void reinitialize() {
        INSTANCE = null;
        initialize();
    }
}