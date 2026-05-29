package com.adaptive_nemesis.adaptive_nemesismod.boss;

import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Boss识别责任链
 * 
 * 管理多个Boss识别策略，按优先级顺序依次执行识别。
 * 任一策略识别为Boss即返回true，getBossType返回首个成功匹配的策略结果。
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class BossIdentifierChain implements BossIdentifier {

    private final List<BossIdentifier> identifiers;

    /**
     * 创建Boss识别责任链
     * 
     * @param identifiers 按优先级排序的识别策略列表（高优先级在前）
     */
    public BossIdentifierChain(List<BossIdentifier> identifiers) {
        this.identifiers = new ArrayList<>(identifiers);
    }

    @Override
    public boolean isBoss(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return identifiers.stream().anyMatch(id -> id.isBoss(entity));
    }

    @Override
    public String getBossType(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        return identifiers.stream()
            .map(id -> id.getBossType(entity))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    /**
     * 在责任链末尾添加识别策略
     * 
     * @param identifier Boss识别策略
     */
    public void addIdentifier(BossIdentifier identifier) {
        identifiers.add(identifier);
    }

    /**
     * 获取当前责任链中的所有策略（只读视图）
     * 
     * @return 不可修改的策略列表
     */
    public List<BossIdentifier> getIdentifiers() {
        return List.copyOf(identifiers);
    }
}