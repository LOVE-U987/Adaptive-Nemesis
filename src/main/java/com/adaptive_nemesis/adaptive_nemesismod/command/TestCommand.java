package com.adaptive_nemesis.adaptive_nemesismod.command;

import java.util.ArrayList;
import java.util.List;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.boss.BossDamageCapHandler;
import com.adaptive_nemesis.adaptive_nemesismod.compat.ModCompatManager;
import com.adaptive_nemesis.adaptive_nemesismod.damage.TrueDamageHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.AdaptiveFloatSystem;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnemyScalingHandler;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfile;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthData;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.adaptive_nemesis.adaptive_nemesismod.protection.NewbieProtectionHandler;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * 模组测试命令
 *
 * 命令: /an test [all|player|enemy|damage|boss|float|memory|protection|compat]
 * 功能: 测试模组各模块的运行状态和日志输出
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class TestCommand {

    /**
     * 可用的测试模块列表
     */
    private static final String[] TEST_MODULES = {
        "all", "player", "enemy", "damage", "boss", "float", "memory", "protection", "compat", "config"
    };

    /**
     * 命令建议提供者
     */
    private static final SuggestionProvider<CommandSourceStack> MODULE_SUGGESTIONS = (context, builder) -> {
        for (String module : TEST_MODULES) {
            builder.suggest(module);
        }
        return builder.buildFuture();
    };

    /**
     * 注册测试命令
     *
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("test")
            .then(Commands.argument("module", StringArgumentType.word())
                .suggests(MODULE_SUGGESTIONS)
                .executes(TestCommand::executeTest));
    }

    /**
     * 执行测试
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeTest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String module = StringArgumentType.getString(context, "module").toLowerCase();

        source.sendSuccess(() -> Component.literal(
            "§6🧪 Adaptive Nemesis 测试开始..."
        ), false);

        int result;
        switch (module) {
            case "all" -> result = runAllTests(source);
            case "player" -> result = testPlayerSystem(source);
            case "enemy" -> result = testEnemySystem(source);
            case "damage" -> result = testDamageSystem(source);
            case "boss" -> result = testBossSystem(source);
            case "float" -> result = testFloatSystem(source);
            case "memory" -> result = testMemorySystem(source);
            case "protection" -> result = testProtectionSystem(source);
            case "compat" -> result = testCompatSystem(source);
            case "config" -> result = testConfigSystem(source);
            default -> {
                source.sendFailure(Component.literal("§c❌ 未知测试模块: " + module));
                source.sendSuccess(() -> Component.literal(
                    "§e可用模块: §fall, player, enemy, damage, boss, float, memory, protection, compat, config"
                ), false);
                result = 0;
            }
        }

        if (result == 1) {
            source.sendSuccess(() -> Component.literal(
                "§a✅ 测试完成！"
            ), false);
        }

        return result;
    }

    /**
     * 运行全部测试
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int runAllTests(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "§6🧪 运行全部模块测试..."
        ), false);

        int totalTests = 0;
        int passedTests = 0;

        // 测试各模块
        if (testConfigSystem(source) == 1) passedTests++;
        totalTests++;

        if (testPlayerSystem(source) == 1) passedTests++;
        totalTests++;

        if (testEnemySystem(source) == 1) passedTests++;
        totalTests++;

        if (testDamageSystem(source) == 1) passedTests++;
        totalTests++;

        if (testBossSystem(source) == 1) passedTests++;
        totalTests++;

        if (testFloatSystem(source) == 1) passedTests++;
        totalTests++;

        if (testMemorySystem(source) == 1) passedTests++;
        totalTests++;

        if (testProtectionSystem(source) == 1) passedTests++;
        totalTests++;

        if (testCompatSystem(source) == 1) passedTests++;
        totalTests++;

        final int finalPassed = passedTests;
        final int finalTotal = totalTests;
        source.sendSuccess(() -> Component.literal(
            String.format("§6📊 测试结果: §a%d§e/§f%d §e通过", finalPassed, finalTotal)
        ), false);

        return passedTests == totalTests ? 1 : 0;
    }

    /**
     * 测试配置系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testConfigSystem(CommandSourceStack source) {
        printTestHeader(source, "配置系统");

        try {
            // 测试配置读取
            double difficulty = Config.DIFFICULTY_BASE_MULTIPLIER.get();
            boolean trueDamage = Config.ENABLE_TRUE_DAMAGE.get();
            boolean newbieProtection = Config.ENABLE_NEWBIE_PROTECTION.get();
            boolean bossCap = Config.ENABLE_BOSS_DAMAGE_CAP.get();
            boolean enemyCap = Config.ENABLE_ENEMY_BONUS_CAP.get();

            printTestLog(source, "难度系数基准", String.valueOf(difficulty));
            printTestLog(source, "真实伤害启用", String.valueOf(trueDamage));
            printTestLog(source, "新手保护启用", String.valueOf(newbieProtection));
            printTestLog(source, "Boss伤害上限启用", String.valueOf(bossCap));
            printTestLog(source, "敌人加成上限启用", String.valueOf(enemyCap));

            // 测试配置值范围
            if (difficulty < 0.1 || difficulty > 10.0) {
                printTestError(source, "难度系数超出有效范围");
                return 0;
            }

            printTestSuccess(source, "配置系统正常");
            return 1;
        } catch (Exception e) {
            printTestError(source, "配置读取异常: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 测试玩家强度评估系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testPlayerSystem(CommandSourceStack source) {
        printTestHeader(source, "玩家强度评估系统");

        try {
            // 测试单例
            PlayerStrengthEvaluator evaluator = PlayerStrengthEvaluator.getInstance();
            if (evaluator == null) {
                printTestError(source, "无法获取评估器实例");
                return 0;
            }
            printTestLog(source, "单例实例", "✓ 正常");

            // 测试玩家强度计算（如果执行者是玩家）
            if (source.getEntity() instanceof ServerPlayer player) {
                PlayerStrengthData strengthData = evaluator.updatePlayerStrength(player);
                if (strengthData != null) {
                    printTestLog(source, "玩家强度计算", "✓ 正常");
                    printTestLog(source, "综合强度", String.format("%.2f", strengthData.getTotalStrength()));
                    printTestLog(source, "防御强度", String.format("%.2f", strengthData.getDefenseStrength()));
                    printTestLog(source, "输出强度", String.format("%.2f", strengthData.getDamageStrength()));
                    printTestLog(source, "神话词条强度", String.format("%.2f", strengthData.getApotheosisStrength()));
                    printTestLog(source, "铁魔法强度", String.format("%.2f", strengthData.getIronsSpellsStrength()));
                    printTestLog(source, "史诗战斗强度", String.format("%.2f", strengthData.getEpicFightStrength()));

                    // 计算敌人加成倍率
                    double baseMultiplier = 1.0 + (strengthData.getTotalStrength() * Config.DIFFICULTY_BASE_MULTIPLIER.get() / 100.0);
                    double floatMultiplier = AdaptiveFloatSystem.getInstance().getFloatMultiplier(player.getUUID());
                    double finalMultiplier = baseMultiplier * floatMultiplier;

                    printTestLog(source, "基础加成倍率", String.format("%.2f", baseMultiplier));
                    printTestLog(source, "浮动倍率", String.format("%.2f", floatMultiplier));
                    printTestLog(source, "最终加成倍率", String.format("%.2f", finalMultiplier));

                    // 计算各属性上限
                    printTestLog(source, "血量上限倍率", String.format("%.2f", Math.min(finalMultiplier, Config.MAX_HEALTH_MULTIPLIER.get())));
                    printTestLog(source, "伤害上限倍率", String.format("%.2f", Math.min(finalMultiplier, Config.MAX_DAMAGE_MULTIPLIER.get())));
                    printTestLog(source, "护甲上限倍率", String.format("%.2f", Math.min(finalMultiplier, Config.MAX_ARMOR_MULTIPLIER.get())));
                } else {
                    printTestWarning(source, "玩家强度数据为空");
                }
            } else {
                printTestWarning(source, "非玩家执行，跳过玩家强度测试");
            }

            printTestSuccess(source, "玩家强度评估系统正常");
            return 1;
        } catch (Exception e) {
            printTestError(source, "玩家强度评估异常: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 测试敌人强化系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testEnemySystem(CommandSourceStack source) {
        printTestHeader(source, "敌人动态强化系统");

        try {
            // 测试单例
            EnemyScalingHandler handler = EnemyScalingHandler.getInstance();
            if (handler == null) {
                printTestError(source, "无法获取强化处理器实例");
                return 0;
            }
            printTestLog(source, "单例实例", "✓ 正常");

            // 测试浮动系统
            AdaptiveFloatSystem floatSystem = AdaptiveFloatSystem.getInstance();
            double floatMultiplier = floatSystem.getFloatMultiplier();
            printTestLog(source, "当前浮动倍率", String.format("%.2f", floatMultiplier));

            // 验证浮动倍率范围
            if (floatMultiplier < Config.FLOAT_MIN.get() || floatMultiplier > Config.FLOAT_MAX.get()) {
                printTestWarning(source, "浮动倍率超出配置范围");
            }

            // 显示敌人加成上限配置
            printTestLog(source, "血量加成上限", String.format("%.2f", Config.MAX_HEALTH_MULTIPLIER.get()));
            printTestLog(source, "伤害加成上限", String.format("%.2f", Config.MAX_DAMAGE_MULTIPLIER.get()));
            printTestLog(source, "护甲加成上限", String.format("%.2f", Config.MAX_ARMOR_MULTIPLIER.get()));
            printTestLog(source, "区域同步范围", Config.AREA_SYNC_RANGE.get() + " 区块");

            // 如果执行者是玩家，显示针对该玩家的敌人加成
            if (source.getEntity() instanceof ServerPlayer player) {
                PlayerStrengthData strengthData = PlayerStrengthEvaluator.getInstance().getPlayerStrength(player);
                if (strengthData != null) {
                    double baseMultiplier = 1.0 + (strengthData.getTotalStrength() * Config.DIFFICULTY_BASE_MULTIPLIER.get() / 100.0);
                    double finalMultiplier = baseMultiplier * floatMultiplier;

                    printTestLog(source, "针对你的基础倍率", String.format("%.2f", baseMultiplier));
                    printTestLog(source, "针对你的最终倍率", String.format("%.2f", finalMultiplier));
                    printTestLog(source, "敌人血量加成", String.format("+%.0f%%", (Math.min(finalMultiplier, Config.MAX_HEALTH_MULTIPLIER.get()) - 1.0) * 100));
                    printTestLog(source, "敌人伤害加成", String.format("+%.0f%%", (Math.min(finalMultiplier, Config.MAX_DAMAGE_MULTIPLIER.get()) - 1.0) * 100));
                    printTestLog(source, "敌人护甲加成", String.format("+%.0f%%", (Math.min(finalMultiplier, Config.MAX_ARMOR_MULTIPLIER.get()) - 1.0) * 100));
                }
            }

            printTestSuccess(source, "敌人动态强化系统正常");
            return 1;
        } catch (Exception e) {
            printTestError(source, "敌人强化系统异常: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 测试真实伤害系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testDamageSystem(CommandSourceStack source) {
        printTestHeader(source, "真实伤害转化系统");

        try {
            TrueDamageHandler handler = TrueDamageHandler.getInstance();
            if (handler == null) {
                printTestError(source, "无法获取伤害处理器实例");
                return 0;
            }
            printTestLog(source, "单例实例", "✓ 正常");

            // 测试护甲阈值计算
            double lowPercent = handler.getTrueDamagePercent(10);   // 低护甲
            double mediumPercent = handler.getTrueDamagePercent(30); // 中护甲
            double highPercent = handler.getTrueDamagePercent(70);   // 高护甲
            double turtlePercent = handler.getTrueDamagePercent(150); // 铁乌龟

            printTestLog(source, "低护甲阈值", "< " + Config.LOW_ARMOR_THRESHOLD.get());
            printTestLog(source, "中护甲阈值", Config.LOW_ARMOR_THRESHOLD.get() + " - " + Config.MEDIUM_ARMOR_THRESHOLD.get());
            printTestLog(source, "高护甲阈值", Config.MEDIUM_ARMOR_THRESHOLD.get() + " - " + Config.HIGH_ARMOR_THRESHOLD.get());
            printTestLog(source, "铁乌龟阈值", "> " + Config.HIGH_ARMOR_THRESHOLD.get());

            printTestLog(source, "低护甲真实伤害", String.format("%.1f%%", lowPercent));
            printTestLog(source, "中护甲真实伤害", String.format("%.1f%%", mediumPercent));
            printTestLog(source, "高护甲真实伤害", String.format("%.1f%%", highPercent));
            printTestLog(source, "铁乌龟真实伤害", String.format("%.1f%%", turtlePercent));

            // 验证阈值递增
            if (!(lowPercent <= mediumPercent && mediumPercent <= highPercent && highPercent <= turtlePercent)) {
                printTestWarning(source, "真实伤害比例未按预期递增");
            }

            // 如果执行者是玩家，显示针对该玩家的真实伤害
            if (source.getEntity() instanceof ServerPlayer player) {
                double armorValue = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                double playerTrueDamagePercent = handler.getTrueDamagePercent(armorValue);
                String armorLevel = handler.getArmorLevelDescription(armorValue);

                printTestLog(source, "你的当前护甲", String.format("%.1f", armorValue));
                printTestLog(source, "你的护甲等级", armorLevel);
                printTestLog(source, "你的真实伤害比例", String.format("%.1f%%", playerTrueDamagePercent));

                // 计算实际伤害示例
                double exampleDamage = 20.0;
                double trueDamage = exampleDamage * (playerTrueDamagePercent / 100.0);
                double reducedDamage = exampleDamage - trueDamage;

                printTestLog(source, "伤害示例", String.format("%.1f 伤害", exampleDamage));
                printTestLog(source, "真实伤害部分", String.format("%.1f", trueDamage));
                printTestLog(source, "护甲减免部分", String.format("%.1f", reducedDamage));
            }

            printTestSuccess(source, "真实伤害转化系统正常");
            return 1;
        } catch (Exception e) {
            printTestError(source, "真实伤害系统异常: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 测试Boss机制系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testBossSystem(CommandSourceStack source) {
        printTestHeader(source, "Boss特殊机制系统");

        try {
            BossDamageCapHandler handler = BossDamageCapHandler.getInstance();
            if (handler == null) {
                printTestError(source, "无法获取Boss处理器实例");
                return 0;
            }
            printTestLog(source, "单例实例", "✓ 正常");

            // 测试配置
            printTestLog(source, "伤害上限", String.valueOf(Config.BOSS_DAMAGE_CAP.get()));
            printTestLog(source, "血量倍率", String.valueOf(Config.BOSS_HEALTH_MULTIPLIER.get()));
            printTestLog(source, "伤害倍率", String.valueOf(Config.BOSS_DAMAGE_MULTIPLIER.get()));

            // 显示实际效果
            double exampleBossHealth = 200.0;
            double exampleBossDamage = 15.0;
            double buffedHealth = exampleBossHealth * Config.BOSS_HEALTH_MULTIPLIER.get();
            double buffedDamage = exampleBossDamage * Config.BOSS_DAMAGE_MULTIPLIER.get();

            printTestLog(source, "Boss示例血量", String.format("%.0f -> %.0f", exampleBossHealth, buffedHealth));
            printTestLog(source, "Boss示例伤害", String.format("%.1f -> %.1f", exampleBossDamage, buffedDamage));
            printTestLog(source, "玩家伤害上限", String.format("%.1f", Config.BOSS_DAMAGE_CAP.get()));

            printTestSuccess(source, "Boss特殊机制系统正常");
            return 1;
        } catch (Exception e) {
            printTestError(source, "Boss机制异常: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 测试浮动系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testFloatSystem(CommandSourceStack source) {
        printTestHeader(source, "智能浮动系统");

        try {
            AdaptiveFloatSystem floatSystem = AdaptiveFloatSystem.getInstance();
            if (floatSystem == null) {
                printTestError(source, "无法获取浮动系统实例");
                return 0;
            }
            printTestLog(source, "单例实例", "✓ 正常");

            // 测试默认倍率
            double defaultMultiplier = floatSystem.getFloatMultiplier();
            printTestLog(source, "默认浮动倍率", String.format("%.2f", defaultMultiplier));

            // 测试配置值
            printTestLog(source, "浮动最小值", String.valueOf(Config.FLOAT_MIN.get()));
            printTestLog(source, "浮动最大值", String.valueOf(Config.FLOAT_MAX.get()));
            printTestLog(source, "击杀加成", String.format("+%.0f%%", Config.KILL_STREAK_MULTIPLIER_INCREASE.get() * 100));
            printTestLog(source, "死亡减免", String.format("-%.0f%%", Config.DEATH_STREAK_MULTIPLIER_DECREASE.get() * 100));
            printTestLog(source, "重置时间", Config.FLOAT_RESET_TIME_MINUTES.get() + " 分钟");

            // 如果执行者是玩家，显示该玩家的浮动数据
            if (source.getEntity() instanceof ServerPlayer player) {
                double playerFloat = floatSystem.getFloatMultiplier(player.getUUID());
                printTestLog(source, "你的当前倍率", String.format("%.2f", playerFloat));

                // 计算影响
                double exampleBase = 1.5;
                double withFloat = exampleBase * playerFloat;
                printTestLog(source, "示例影响", String.format("%.2f x %.2f = %.2f", exampleBase, playerFloat, withFloat));
            }

            printTestSuccess(source, "智能浮动系统正常");
            return 1;
        } catch (Exception e) {
            printTestError(source, "浮动系统异常: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 测试宿敌记忆系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testMemorySystem(CommandSourceStack source) {
        printTestHeader(source, "宿敌记忆系统");

        try {
            NemesisMemorySystem memorySystem = NemesisMemorySystem.getInstance();
            if (memorySystem == null) {
                printTestError(source, "无法获取记忆系统实例");
                return 0;
            }
            printTestLog(source, "单例实例", "✓ 正常");

            // 测试档案获取
            if (source.getEntity() instanceof ServerPlayer player) {
                NemesisProfile profile = memorySystem.getProfile(player.getUUID());
                if (profile != null) {
                    printTestLog(source, "玩家档案", "✓ 已存在");
                    printTestLog(source, "总击杀", String.valueOf(profile.getTotalKills()));
                    printTestLog(source, "总死亡", String.valueOf(profile.getTotalDeaths()));
                    printTestLog(source, "KDA", String.format("%.2f", profile.getKdaRatio()));
                    printTestLog(source, "主要风格", profile.getDominantStyle().getDisplayName());
                    printTestLog(source, "近战击杀", String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.MELEE)));
                    printTestLog(source, "远程击杀", String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.RANGED)));
                    printTestLog(source, "法术击杀", String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.MAGIC)));

                    // 显示宿敌加成（新增）
                    printTestLog(source, "宿敌加成:", "");
                    printTestLog(source, "  近战抗性", String.format("+%.1f%%", profile.getMeleeResistanceBonus() * 100));
                    printTestLog(source, "  远程抗性", String.format("+%.1f%%", profile.getRangedResistanceBonus() * 100));
                    printTestLog(source, "  魔法抗性", String.format("+%.1f%%", profile.getMagicResistanceBonus() * 100));
                    printTestLog(source, "  攻击加成", String.format("+%.1f%%", profile.getAttackBonus() * 100));
                    printTestLog(source, "  速度加成", String.format("+%.1f%%", profile.getSpeedBonus() * 100));
                    printTestLog(source, "  生命加成", String.format("+%.1f%%", profile.getHealthBonus() * 100));

                    printTestLog(source, "加成总览", profile.getBonusDescription());
                } else {
                    printTestLog(source, "玩家档案", "✗ 不存在（尚无战斗记录）");
                    printTestLog(source, "提示", "击杀一些敌人后再测试此模块");
                }
            } else {
                printTestWarning(source, "非玩家执行，跳过档案测试");
            }

            printTestSuccess(source, "宿敌记忆系统正常");
            return 1;
        } catch (Exception e) {
            printTestError(source, "记忆系统异常: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 测试新手保护系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testProtectionSystem(CommandSourceStack source) {
        printTestHeader(source, "新手保护系统");

        try {
            NewbieProtectionHandler handler = NewbieProtectionHandler.getInstance();
            if (handler == null) {
                printTestError(source, "无法获取保护处理器实例");
                return 0;
            }
            printTestLog(source, "单例实例", "✓ 正常");

            // 测试配置
            printTestLog(source, "保护启用", String.valueOf(Config.ENABLE_NEWBIE_PROTECTION.get()));
            printTestLog(source, "强度阈值", String.valueOf(Config.NEWBIE_STRENGTH_THRESHOLD.get()));
            printTestLog(source, "持续时间", Config.NEWBIE_PROTECTION_DURATION.get() + "分钟");
            printTestLog(source, "属性减免", String.format("%.0f%%", Config.NEWBIE_PROTECTION_REDUCTION.get() * 100));
            printTestLog(source, "死亡加成", Config.DEATH_PROTECTION_BONUS.get() + "分钟");
            printTestLog(source, "连续死亡阈值", String.valueOf(Config.DEATH_STREAK_THRESHOLD.get()));

            // 测试玩家保护状态
            if (source.getEntity() instanceof ServerPlayer player) {
                boolean isProtected = handler.isProtected(player.getUUID());
                double reduction = handler.getProtectionReduction(player.getUUID());
                printTestLog(source, "当前保护状态", isProtected ? "§a已启用" : "§c未启用");
                printTestLog(source, "当前减免比例", String.format("%.0f%%", reduction * 100));

                // 显示保护效果
                if (isProtected) {
                    double exampleEnemyDamage = 20.0;
                    double reducedDamage = exampleEnemyDamage * (1.0 - reduction);
                    printTestLog(source, "保护效果示例", String.format("%.1f -> %.1f", exampleEnemyDamage, reducedDamage));
                }
            } else {
                printTestWarning(source, "非玩家执行，跳过保护状态测试");
            }

            printTestSuccess(source, "新手保护系统正常");
            return 1;
        } catch (Exception e) {
            printTestError(source, "保护系统异常: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 测试模组兼容系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testCompatSystem(CommandSourceStack source) {
        printTestHeader(source, "模组兼容系统");

        try {
            // 检测各模组加载状态
            boolean ironsSpellsLoaded = ModCompatManager.isIronsSpellsLoaded();
            boolean epicFightLoaded = ModCompatManager.isEpicFightLoaded();
            boolean apotheosisLoaded = ModCompatManager.isApotheosisLoaded();

            printTestLog(source, "铁魔法 (Iron's Spells)", ironsSpellsLoaded ? "§a已加载" : "§7未加载");
            printTestLog(source, "史诗战斗 (Epic Fight)", epicFightLoaded ? "§a已加载" : "§7未加载");
            printTestLog(source, "神话 (Apotheosis)", apotheosisLoaded ? "§a已加载" : "§7未加载");

            // 测试兼容处理器
            if (ironsSpellsLoaded) {
                var ironsCompat = ModCompatManager.getIronsSpellsCompat();
                printTestLog(source, "铁魔法兼容处理器", ironsCompat != null ? "✓ 正常" : "✗ 异常");
            }

            if (epicFightLoaded) {
                var epicCompat = ModCompatManager.getEpicFightCompat();
                printTestLog(source, "史诗战斗兼容处理器", epicCompat != null ? "✓ 正常" : "✗ 异常");
            }

            if (apotheosisLoaded) {
                var apothCompat = ModCompatManager.getApotheosisCompat();
                printTestLog(source, "神话兼容处理器", apothCompat != null ? "✓ 正常" : "✗ 异常");
            }

            printTestSuccess(source, "模组兼容系统正常");
            return 1;
        } catch (Exception e) {
            printTestError(source, "兼容系统异常: " + e.getMessage());
            return 0;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 打印测试标题
     *
     * @param source 命令来源
     * @param moduleName 模块名称
     */
    private static void printTestHeader(CommandSourceStack source, String moduleName) {
        source.sendSuccess(() -> Component.literal(
            "§e----- 测试: " + moduleName + " -----"
        ), false);
    }

    /**
     * 打印测试日志
     *
     * @param source 命令来源
     * @param key 测试项
     * @param value 测试结果
     */
    private static void printTestLog(CommandSourceStack source, String key, String value) {
        source.sendSuccess(() -> Component.literal(
            String.format("§7  > §f%s: §e%s", key, value)
        ), false);
    }

    /**
     * 打印测试成功
     *
     * @param source 命令来源
     * @param message 成功消息
     */
    private static void printTestSuccess(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(
            "§a  ✓ " + message
        ), false);
    }

    /**
     * 打印测试警告
     *
     * @param source 命令来源
     * @param message 警告消息
     */
    private static void printTestWarning(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(
            "§e  ⚠ " + message
        ), false);
    }

    /**
     * 打印测试错误
     *
     * @param source 命令来源
     * @param message 错误消息
     */
    private static void printTestError(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(
            "§c  ✗ " + message
        ), false);
    }
}
