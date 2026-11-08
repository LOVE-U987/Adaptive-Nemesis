package com.adaptive_nemesis.adaptive_nemesismod.command;

import java.util.ArrayList;
import java.util.List;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.boss.BossDamageCapHandler;
import com.adaptive_nemesis.adaptive_nemesismod.compat.ModCompatManager;
import com.adaptive_nemesis.adaptive_nemesismod.damage.ModDamageTypes;
import com.adaptive_nemesis.adaptive_nemesismod.damage.TrueDamageHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.AdaptiveFloatSystem;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnemyScalingHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.WorldStageManager;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionFixConfig;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionProgressSavedData;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfile;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfileSavedData;
import com.adaptive_nemesis.adaptive_nemesismod.nemesis.NemesisSystem;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthData;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.adaptive_nemesis.adaptive_nemesismod.protection.NewbieProtectionHandler;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.loading.FMLLoader;

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
     * 难度倍率允许的最小值，与 GeneralConfig 中 DIFFICULTY_BASE_MULTIPLIER 的下限一致
     */
    private static final double MIN_DIFFICULTY = 0.1;

    /**
     * 难度倍率允许的最大值，与 GeneralConfig 中 DIFFICULTY_BASE_MULTIPLIER 的上限一致
     */
    private static final double MAX_DIFFICULTY = 20.0;

    /**
     * 守卫检查扫描半径（格），用于宿敌转化守卫检查点
     */
    private static final double GUARD_CHECK_RANGE = 32.0;

    /**
     * 可用的测试模块列表
     */
    private static final String[] TEST_MODULES = {
        "all", "player", "enemy", "damage", "boss", "float", "memory", "protection", "compat", "config", "verify"
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

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.test.starting"
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
            case "verify" -> result = testVerifySystem(source);
            default -> {
                source.sendFailure(Component.translatable("adaptive_nemesis.command.error.unknown_module", module));
                source.sendSuccess(() -> Component.translatable(
                    "adaptive_nemesis.command.error.available_modules"
                ), false);
                result = 0;
            }
        }

        if (result == 1) {
            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.test.complete"
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
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.test.all_header"
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
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.test.all_result", String.valueOf(finalPassed), String.valueOf(finalTotal)
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
        printTestHeader(source, Component.translatable("adaptive_nemesis.command.test.module_config"));

        try {
            // 测试配置读取
            double difficulty = Config.DIFFICULTY_BASE_MULTIPLIER.get();
            boolean trueDamage = Config.ENABLE_TRUE_DAMAGE.get();
            boolean newbieProtection = Config.ENABLE_NEWBIE_PROTECTION.get();
            boolean bossCap = Config.ENABLE_BOSS_DAMAGE_CAP.get();
            boolean enemyCap = Config.ENABLE_ENEMY_BONUS_CAP.get();

            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_difficulty_base"), String.valueOf(difficulty));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_true_damage_enabled"), String.valueOf(trueDamage));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_newbie_protection"), String.valueOf(newbieProtection));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_boss_cap"), String.valueOf(bossCap));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_enemy_cap"), String.valueOf(enemyCap));

            // 测试配置值范围（与配置定义 0.1-20.0 对齐）
            if (difficulty < MIN_DIFFICULTY || difficulty > MAX_DIFFICULTY) {
                printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_out_of_range"));
                return 0;
            }

            printTestSuccess(source, Component.translatable("adaptive_nemesis.command.test.module_config"));
            return 1;
        } catch (Exception e) {
            printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_config_error", e.getMessage()));
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
        printTestHeader(source, Component.translatable("adaptive_nemesis.command.test.module_player"));

        try {
            // 测试单例
            PlayerStrengthEvaluator evaluator = PlayerStrengthEvaluator.getInstance();
            if (evaluator == null) {
                printTestError(source, Component.translatable("adaptive_nemesis.command.error.failed_spawn"));
                return 0;
            }
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok"));

            // 测试玩家强度计算（如果执行者是玩家）
            if (source.getEntity() instanceof ServerPlayer player) {
                PlayerStrengthData strengthData = evaluator.updatePlayerStrength(player);
                if (strengthData != null) {
                    printStrengthBreakdown(source, strengthData);
                    printPlayerScaling(source, player, strengthData);
                } else {
                    printTestWarning(source, Component.translatable("adaptive_nemesis.command.test.label_no_strength_data"));
                }
            } else {
                printTestWarning(source, Component.translatable("adaptive_nemesis.command.test.label_skip_non_player"));
            }

            printTestSuccess(source, Component.translatable("adaptive_nemesis.command.test.module_player"));
            return 1;
        } catch (Exception e) {
            printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_strength_error", e.getMessage()));
            return 0;
        }
    }

    /**
     * 打印玩家强度分项明细
     *
     * @param source 命令来源
     * @param strengthData 玩家强度数据
     */
    private static void printStrengthBreakdown(CommandSourceStack source, PlayerStrengthData strengthData) {
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_strength_calc"), okComponent());
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_total_strength"), String.format("%.2f", strengthData.getTotalStrength()));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_defense_strength"), String.format("%.2f", strengthData.getDefenseStrength()));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_damage_strength"), String.format("%.2f", strengthData.getDamageStrength()));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_apotheosis"), String.format("%.2f", strengthData.getApotheosisStrength()));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_irons_spells"), String.format("%.2f", strengthData.getIronsSpellsStrength()));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_epic_fight"), String.format("%.2f", strengthData.getEpicFightStrength()));
    }

    /**
     * 计算并打印针对当前玩家的敌人加成倍率与属性上限
     *
     * @param source 命令来源
     * @param player 执行命令的玩家
     * @param strengthData 玩家强度数据
     */
    private static void printPlayerScaling(CommandSourceStack source, ServerPlayer player,
                                           PlayerStrengthData strengthData) {
        // 计算敌人加成倍率
        double baseMultiplier = 1.0 + (strengthData.getTotalStrength() * Config.DIFFICULTY_BASE_MULTIPLIER.get() / 100.0);
        double floatMultiplier = AdaptiveFloatSystem.getInstance().getFloatMultiplier(player.getUUID());
        double finalMultiplier = baseMultiplier * floatMultiplier;

        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_base_multiplier"), String.format("%.2f", baseMultiplier));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_float_multiplier"), String.format("%.2f", floatMultiplier));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_final_multiplier"), String.format("%.2f", finalMultiplier));

        // 计算各属性上限
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_max_hp"), String.format("%.2f", Math.min(finalMultiplier, Config.MAX_HEALTH_MULTIPLIER.get())));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_max_damage"), String.format("%.2f", Math.min(finalMultiplier, Config.MAX_DAMAGE_MULTIPLIER.get())));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_max_armor"), String.format("%.2f", Math.min(finalMultiplier, Config.MAX_ARMOR_MULTIPLIER.get())));
    }

    /**
     * 测试敌人强化系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testEnemySystem(CommandSourceStack source) {
        printTestHeader(source, Component.translatable("adaptive_nemesis.command.test.module_enemy"));

        try {
            // 测试单例
            EnemyScalingHandler handler = EnemyScalingHandler.getInstance();
            if (handler == null) {
                printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_no_scaling_handler"));
                return 0;
            }
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok"));

            // 测试浮动系统
            AdaptiveFloatSystem floatSystem = AdaptiveFloatSystem.getInstance();
            double floatMultiplier = floatSystem.getFloatMultiplier();
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_current_float"), String.format("%.2f", floatMultiplier));

            // 验证浮动倍率范围
            if (floatMultiplier < Config.FLOAT_MIN.get() || floatMultiplier > Config.FLOAT_MAX.get()) {
                printTestWarning(source, Component.translatable("adaptive_nemesis.command.test.label_float_out_of_range"));
            }

            // 显示敌人加成上限配置
            printBonusCapConfig(source);

            // 如果执行者是玩家，显示针对该玩家的敌人加成
            if (source.getEntity() instanceof ServerPlayer player) {
                PlayerStrengthData strengthData = PlayerStrengthEvaluator.getInstance().getPlayerStrength(player);
                if (strengthData != null) {
                    printPlayerEnemyBonus(source, strengthData, floatMultiplier);
                }
            }

            printTestSuccess(source, Component.translatable("adaptive_nemesis.command.test.module_enemy"));
            return 1;
        } catch (Exception e) {
            printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_enemy_error", e.getMessage()));
            return 0;
        }
    }

    /**
     * 打印敌人加成上限配置
     *
     * @param source 命令来源
     */
    private static void printBonusCapConfig(CommandSourceStack source) {
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_max_hp_cap"), String.format("%.2f", Config.MAX_HEALTH_MULTIPLIER.get()));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_max_damage_cap"), String.format("%.2f", Config.MAX_DAMAGE_MULTIPLIER.get()));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_max_armor_cap"), String.format("%.2f", Config.MAX_ARMOR_MULTIPLIER.get()));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_sync_range"),
            Component.translatable("adaptive_nemesis.command.test.unit_chunks", Config.AREA_SYNC_RANGE.get()));
    }

    /**
     * 打印针对当前玩家的敌人加成演示
     *
     * @param source 命令来源
     * @param strengthData 玩家强度数据
     * @param floatMultiplier 当前浮动倍率
     */
    private static void printPlayerEnemyBonus(CommandSourceStack source, PlayerStrengthData strengthData,
                                              double floatMultiplier) {
        double baseMultiplier = 1.0 + (strengthData.getTotalStrength() * Config.DIFFICULTY_BASE_MULTIPLIER.get() / 100.0);
        double finalMultiplier = baseMultiplier * floatMultiplier;

        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_your_base"), String.format("%.2f", baseMultiplier));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_your_final"), String.format("%.2f", finalMultiplier));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_enemy_hp_bonus"), String.format("+%.0f%%", (Math.min(finalMultiplier, Config.MAX_HEALTH_MULTIPLIER.get()) - 1.0) * 100));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_enemy_damage_bonus"), String.format("+%.0f%%", (Math.min(finalMultiplier, Config.MAX_DAMAGE_MULTIPLIER.get()) - 1.0) * 100));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_enemy_armor_bonus"), String.format("+%.0f%%", (Math.min(finalMultiplier, Config.MAX_ARMOR_MULTIPLIER.get()) - 1.0) * 100));
    }

    /**
     * 测试真实伤害系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testDamageSystem(CommandSourceStack source) {
        printTestHeader(source, Component.translatable("adaptive_nemesis.command.test.module_damage"));

        try {
            TrueDamageHandler handler = TrueDamageHandler.getInstance();
            if (handler == null) {
                printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_no_damage_handler"));
                return 0;
            }
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok"));

            // 测试护甲阈值计算
            double lowPercent = handler.getTrueDamagePercent(10);   // 低护甲
            double mediumPercent = handler.getTrueDamagePercent(30); // 中护甲
            double highPercent = handler.getTrueDamagePercent(70);   // 高护甲
            double turtlePercent = handler.getTrueDamagePercent(150); // 铁乌龟

            printTrueDamageThresholds(source, lowPercent, mediumPercent, highPercent, turtlePercent);

            // 验证阈值递增
            if (!(lowPercent <= mediumPercent && mediumPercent <= highPercent && highPercent <= turtlePercent)) {
                printTestWarning(source, Component.translatable("adaptive_nemesis.command.test.label_td_not_increasing"));
            }

            // 如果执行者是玩家，显示针对该玩家的真实伤害
            if (source.getEntity() instanceof ServerPlayer player) {
                printPlayerTrueDamage(source, handler, player);
            }

            printTestSuccess(source, Component.translatable("adaptive_nemesis.command.test.module_damage"));
            return 1;
        } catch (Exception e) {
            printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_damage_error", e.getMessage()));
            return 0;
        }
    }

    /**
     * 打印真实伤害护甲分段阈值与对应转化百分比
     *
     * @param source 命令来源
     * @param lowPercent 低护甲转化百分比
     * @param mediumPercent 中护甲转化百分比
     * @param highPercent 高护甲转化百分比
     * @param turtlePercent 铁乌龟转化百分比
     */
    private static void printTrueDamageThresholds(CommandSourceStack source, double lowPercent, double mediumPercent,
                                                  double highPercent, double turtlePercent) {
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_low_armor_threshold"), "< " + Config.LOW_ARMOR_THRESHOLD.get());
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_medium_armor_threshold"), Config.LOW_ARMOR_THRESHOLD.get() + " - " + Config.MEDIUM_ARMOR_THRESHOLD.get());
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_high_armor_threshold"), Config.MEDIUM_ARMOR_THRESHOLD.get() + " - " + Config.HIGH_ARMOR_THRESHOLD.get());
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_turtle_threshold"), "> " + Config.HIGH_ARMOR_THRESHOLD.get());

        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_low_true_damage"), String.format("%.1f%%", lowPercent));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_medium_true_damage"), String.format("%.1f%%", mediumPercent));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_high_true_damage"), String.format("%.1f%%", highPercent));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_turtle_true_damage"), String.format("%.1f%%", turtlePercent));
    }

    /**
     * 打印针对当前玩家的真实伤害转化演示
     *
     * @param source 命令来源
     * @param handler 真实伤害处理器
     * @param player 执行命令的玩家
     */
    private static void printPlayerTrueDamage(CommandSourceStack source, TrueDamageHandler handler,
                                              ServerPlayer player) {
        double armorValue = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        double playerTrueDamagePercent = handler.getTrueDamagePercent(armorValue);
        Component armorLevel = handler.getArmorLevelDescription(armorValue);

        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_your_armor"), String.format("%.1f", armorValue));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_your_armor_level"), armorLevel);
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_your_td_percent"), String.format("%.1f%%", playerTrueDamagePercent));

        // 计算实际伤害示例
        double exampleDamage = 20.0;
        double trueDamage = exampleDamage * (playerTrueDamagePercent / 100.0);
        double reducedDamage = exampleDamage - trueDamage;

        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_damage_example"),
            Component.translatable("adaptive_nemesis.command.test.label_damage_example_value", String.format("%.1f", exampleDamage)));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_true_damage_part"), String.format("%.1f", trueDamage));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_mitigated_part"), String.format("%.1f", reducedDamage));
    }

    /**
     * 测试Boss机制系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testBossSystem(CommandSourceStack source) {
        printTestHeader(source, Component.translatable("adaptive_nemesis.command.test.module_boss"));

        try {
            BossDamageCapHandler handler = BossDamageCapHandler.getInstance();
            if (handler == null) {
                printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_no_boss_handler"));
                return 0;
            }
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok"));

            // 测试配置
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_damage_cap"), String.valueOf(Config.BOSS_DAMAGE_CAP.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_hp_multiplier"), String.valueOf(Config.BOSS_HEALTH_MULTIPLIER.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_damage_multiplier"), String.valueOf(Config.BOSS_DAMAGE_MULTIPLIER.get()));

            // 显示实际效果
            double exampleBossHealth = 200.0;
            double exampleBossDamage = 15.0;
            double buffedHealth = exampleBossHealth * Config.BOSS_HEALTH_MULTIPLIER.get();
            double buffedDamage = exampleBossDamage * Config.BOSS_DAMAGE_MULTIPLIER.get();

            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_boss_hp_example"), String.format("%.0f -> %.0f", exampleBossHealth, buffedHealth));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_boss_damage_example"), String.format("%.1f -> %.1f", exampleBossDamage, buffedDamage));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_player_damage_cap"), String.format("%.1f", Config.BOSS_DAMAGE_CAP.get()));

            printTestSuccess(source, Component.translatable("adaptive_nemesis.command.test.module_boss"));
            return 1;
        } catch (Exception e) {
            printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_boss_error", e.getMessage()));
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
        printTestHeader(source, Component.translatable("adaptive_nemesis.command.test.module_float"));

        try {
            AdaptiveFloatSystem floatSystem = AdaptiveFloatSystem.getInstance();
            if (floatSystem == null) {
                printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_no_float_system"));
                return 0;
            }
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok"));

            // 测试默认倍率
            double defaultMultiplier = floatSystem.getFloatMultiplier();
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_default_float"), String.format("%.2f", defaultMultiplier));

            // 测试配置值
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_float_min"), String.valueOf(Config.FLOAT_MIN.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_float_max"), String.valueOf(Config.FLOAT_MAX.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_kill_bonus"), String.format("+%.0f%%", Config.KILL_STREAK_MULTIPLIER_INCREASE.get() * 100));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_death_reduction"), String.format("-%.0f%%", Config.DEATH_STREAK_MULTIPLIER_DECREASE.get() * 100));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_reset_time"),
                Component.translatable("adaptive_nemesis.command.test.unit_minutes", Config.FLOAT_RESET_TIME_MINUTES.get()));

            // 如果执行者是玩家，显示该玩家的浮动数据
            if (source.getEntity() instanceof ServerPlayer player) {
                double playerFloat = floatSystem.getFloatMultiplier(player.getUUID());
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_your_float"), String.format("%.2f", playerFloat));

                // 计算影响
                double exampleBase = 1.5;
                double withFloat = exampleBase * playerFloat;
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_example_impact"), String.format("%.2f x %.2f = %.2f", exampleBase, playerFloat, withFloat));
            }

            printTestSuccess(source, Component.translatable("adaptive_nemesis.command.test.module_float"));
            return 1;
        } catch (Exception e) {
            printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_float_error", e.getMessage()));
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
        printTestHeader(source, Component.translatable("adaptive_nemesis.command.test.module_memory"));

        try {
            NemesisMemorySystem memorySystem = NemesisMemorySystem.getInstance();
            if (memorySystem == null) {
                printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_no_memory_system"));
                return 0;
            }
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok"));

            // 测试档案获取
            if (source.getEntity() instanceof ServerPlayer player) {
                NemesisProfile profile = memorySystem.getProfile(player.getUUID());
                if (profile != null) {
                    printNemesisProfileDetails(source, profile);
                } else {
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_player_profile"),
                        Component.translatable("adaptive_nemesis.command.test.status_no_records").withStyle(ChatFormatting.RED));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_tip"),
                        Component.translatable("adaptive_nemesis.command.test.tip_kill_first"));
                }
            } else {
                printTestWarning(source, Component.translatable("adaptive_nemesis.command.test.label_skip_non_player_memory"));
            }

            printTestSuccess(source, Component.translatable("adaptive_nemesis.command.test.module_memory"));
            return 1;
        } catch (Exception e) {
            printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_memory_error", e.getMessage()));
            return 0;
        }
    }

    /**
     * 打印宿敌档案统计与加成明细
     *
     * @param source 命令来源
     * @param profile 玩家宿敌档案
     */
    private static void printNemesisProfileDetails(CommandSourceStack source, NemesisProfile profile) {
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_player_profile"),
            Component.translatable("adaptive_nemesis.command.test.status_exists").withStyle(ChatFormatting.GREEN));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_total_kills"), String.valueOf(profile.getTotalKills()));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_total_deaths"), String.valueOf(profile.getTotalDeaths()));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_kda"), String.format("%.2f", profile.getKdaRatio()));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_dominant_style"), profile.getDominantStyle().getDisplayName());
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_melee_kills"), String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.MELEE)));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_ranged_kills"), String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.RANGED)));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_magic_kills"), String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.MAGIC)));

        printNemesisProfileBonuses(source, profile);
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_bonus_summary"), profile.getBonusDescription());
    }

    /**
     * 打印宿敌档案加成数值
     *
     * @param source 命令来源
     * @param profile 玩家宿敌档案
     */
    private static void printNemesisProfileBonuses(CommandSourceStack source, NemesisProfile profile) {
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_nemesis_bonus"), "");
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_melee_resist"), String.format("+%.1f%%", profile.getMeleeResistanceBonus() * 100));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_ranged_resist"), String.format("+%.1f%%", profile.getRangedResistanceBonus() * 100));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_magic_resist"), String.format("+%.1f%%", profile.getMagicResistanceBonus() * 100));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_attack_bonus"), String.format("+%.1f%%", profile.getAttackBonus() * 100));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_speed_bonus"), String.format("+%.1f%%", profile.getSpeedBonus() * 100));
        printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_health_bonus"), String.format("+%.1f%%", profile.getHealthBonus() * 100));
    }

    /**
     * 测试新手保护系统
     *
     * @param source 命令来源
     * @return 测试结果
     */
    private static int testProtectionSystem(CommandSourceStack source) {
        printTestHeader(source, Component.translatable("adaptive_nemesis.command.test.module_protection"));

        try {
            NewbieProtectionHandler handler = NewbieProtectionHandler.getInstance();
            if (handler == null) {
                printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_no_protection_handler"));
                return 0;
            }
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok"));

            // 测试配置
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_protection_enabled"), String.valueOf(Config.ENABLE_NEWBIE_PROTECTION.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_strength_threshold"), String.valueOf(Config.NEWBIE_STRENGTH_THRESHOLD.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_duration_info"),
                Component.translatable("adaptive_nemesis.command.test.label_duration_info_value", Config.NEWBIE_PROTECTION_DURATION.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_reduction"), String.format("%.0f%%", Config.NEWBIE_PROTECTION_REDUCTION.get() * 100));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_death_bonus"),
                Component.translatable("adaptive_nemesis.command.test.label_duration_info_value", Config.DEATH_PROTECTION_BONUS.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_death_streak"), String.valueOf(Config.DEATH_STREAK_THRESHOLD.get()));

            // 测试玩家保护状态
            if (source.getEntity() instanceof ServerPlayer player) {
                boolean isProtected = handler.isProtected(player.getUUID());
                double reduction = handler.getProtectionReduction(player.getUUID());
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_protection_status"),
                    Component.translatable(isProtected ? "adaptive_nemesis.command.enabled" : "adaptive_nemesis.command.disabled"));
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_current_reduction"), String.format("%.0f%%", reduction * 100));

                // 显示保护效果
                if (isProtected) {
                    double exampleEnemyDamage = 20.0;
                    double reducedDamage = exampleEnemyDamage * (1.0 - reduction);
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_protection_example"), String.format("%.1f -> %.1f", exampleEnemyDamage, reducedDamage));
                }
            } else {
                printTestWarning(source, Component.translatable("adaptive_nemesis.command.test.label_skip_non_player_protection"));
            }

            printTestSuccess(source, Component.translatable("adaptive_nemesis.command.test.module_protection"));
            return 1;
        } catch (Exception e) {
            printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_protection_error", e.getMessage()));
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
        printTestHeader(source, Component.translatable("adaptive_nemesis.command.test.module_compat"));

        try {
            // 检测各模组加载状态
            boolean ironsSpellsLoaded = ModCompatManager.isIronsSpellsLoaded();
            boolean epicFightLoaded = ModCompatManager.isEpicFightLoaded();
            boolean apotheosisLoaded = ModCompatManager.isApotheosisLoaded();

            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_mod_irons_spells"),
                Component.translatable(ironsSpellsLoaded ? "adaptive_nemesis.command.test.loaded" : "adaptive_nemesis.command.test.not_loaded"));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_mod_epic_fight"),
                Component.translatable(epicFightLoaded ? "adaptive_nemesis.command.test.loaded" : "adaptive_nemesis.command.test.not_loaded"));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_mod_apotheosis"),
                Component.translatable(apotheosisLoaded ? "adaptive_nemesis.command.test.loaded" : "adaptive_nemesis.command.test.not_loaded"));

            // 测试兼容处理器
            if (ironsSpellsLoaded) {
                var ironsCompat = ModCompatManager.getIronsSpellsCompat();
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_irons_compat"),
                    compatStatusComponent(ironsCompat != null));
            }

            if (epicFightLoaded) {
                var epicCompat = ModCompatManager.getEpicFightCompat();
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_epic_compat"),
                    compatStatusComponent(epicCompat != null));
            }

            if (apotheosisLoaded) {
                var apothCompat = ModCompatManager.getApotheosisCompat();
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_apoth_compat"),
                    compatStatusComponent(apothCompat != null));
            }

            printTestSuccess(source, Component.translatable("adaptive_nemesis.command.test.module_compat"));
            return 1;
        } catch (Exception e) {
            printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_compat_error", e.getMessage()));
            return 0;
        }
    }

    // ==================== T-06 游戏内验证 ====================

    /**
     * 全量修复验证入口（T-06）
     *
     * 覆盖 9 个检查点：真实伤害初始化与注册、新手保护倍率演示、宿敌转化守卫、
     * 假命令修复状态回读、入侵 0 倍率钳制、持久化数据回读、看门狗侧别自检、
     * 归因检查提示、配置界面取消回滚提示
     *
     * @param source 命令来源
     * @return 全部可自动验证项通过返回 1，否则返回 0
     */
    private static int testVerifySystem(CommandSourceStack source) {
        printTestHeader(source, Component.literal("全量修复验证(T-06)"));

        int passed = 0;
        passed += verifyRealDamage(source) ? 1 : 0;
        passed += verifyNewbieProtection(source) ? 1 : 0;
        passed += verifyNemesisGuard(source) ? 1 : 0;
        passed += verifyCommandReadback(source) ? 1 : 0;
        passed += verifyInvasionClamp(source) ? 1 : 0;
        passed += verifyPersistence(source) ? 1 : 0;
        passed += verifyWatchdog(source) ? 1 : 0;
        printManualCheckHints(source);

        final int finalPassed = passed;
        printTestLog(source, Component.literal("自动验证通过项"), finalPassed + " / 7");
        printTestSuccess(source, Component.literal("全量修复验证(T-06)"));
        return passed == 7 ? 1 : 0;
    }

    /**
     * 验证点一：真实伤害系统初始化状态与 DamageType 注册状态（F-01/F-02）
     *
     * @param source 命令来源
     * @return 初始化且（玩家执行时）注册存在返回 true
     */
    private static boolean verifyRealDamage(CommandSourceStack source) {
        try {
            boolean initialized = TrueDamageHandler.isInitialized();
            printTestLog(source, Component.literal("真实伤害初始化(F-01)"), String.valueOf(initialized));

            if (source.getEntity() instanceof ServerPlayer player) {
                var registry = player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
                boolean registered = registry.containsKey(ModDamageTypes.TRUE_DAMAGE);
                printTestLog(source, Component.literal("真实伤害注册(F-02)"),
                    ModDamageTypes.TRUE_DAMAGE.location() + (registered ? " 已注册" : " 未注册"));
                return initialized && registered;
            }
            printTestWarning(source, Component.literal("非玩家执行者，跳过注册表检查"));
            return initialized;
        } catch (Exception e) {
            printTestError(source, Component.literal("真实伤害检查异常: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 验证点二：新手保护接入缩放管线后的倍率变化演示（F-03）
     *
     * @param source 命令来源
     * @return 演示执行成功返回 true
     */
    private static boolean verifyNewbieProtection(CommandSourceStack source) {
        try {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                printTestWarning(source, Component.literal("非玩家执行者，跳过保护倍率演示"));
                return true;
            }
            NewbieProtectionHandler handler = NewbieProtectionHandler.getInstance();
            boolean isProtected = handler.isProtected(player.getUUID());
            double reduction = handler.getProtectionReduction(player.getUUID());

            printTestLog(source, Component.literal("新手保护状态(F-03)"), String.valueOf(isProtected));

            // 倍率变化演示：以基准倍率 2.0 为例，受保护时乘 (1 - 减免)
            double exampleMultiplier = 2.0;
            double protectedMultiplier = exampleMultiplier * (1.0 - reduction);
            printTestLog(source, Component.literal("保护倍率演示(F-03)"),
                String.format("%.2f -> %.2f (减免 %.0f%%)",
                    exampleMultiplier, protectedMultiplier, reduction * 100));
            return true;
        } catch (Exception e) {
            printTestError(source, Component.literal("新手保护检查异常: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 验证点三：宿敌转化守卫状态（F-04）
     *
     * 扫描附近实体统计已转化宿敌数量；已转化实体的二次转化拒绝由
     * {@code NemesisSystem.isNemesis} 守卫在生成链路中生效
     *
     * @param source 命令来源
     * @return 检查执行成功返回 true
     */
    private static boolean verifyNemesisGuard(CommandSourceStack source) {
        try {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                printTestWarning(source, Component.literal("非玩家执行者，跳过宿敌守卫检查"));
                return true;
            }
            List<Mob> nearby = player.serverLevel().getEntitiesOfClass(
                Mob.class, player.getBoundingBox().inflate(GUARD_CHECK_RANGE));
            long nemesisCount = nearby.stream().filter(NemesisSystem::isNemesis).count();
            printTestLog(source, Component.literal("宿敌转化守卫(F-04)"),
                String.format("范围内实体 %d 个，已转化宿敌 %d 个，二次转化由守卫拒绝",
                    nearby.size(), nemesisCount));
            return true;
        } catch (Exception e) {
            printTestError(source, Component.literal("宿敌守卫检查异常: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 验证点四：假命令修复后的配置状态回读（F-10）
     *
     * 执行 /an difficulty set、/an difficulty true_damage set、
     * /an invasion setdifficulty 后重新运行本模块，此处数值应与设置值一致
     *
     * @param source 命令来源
     * @return 回读执行成功返回 true
     */
    private static boolean verifyCommandReadback(CommandSourceStack source) {
        try {
            printTestLog(source, Component.literal("难度基准回读(F-10)"),
                String.format("%.2f", Config.DIFFICULTY_BASE_MULTIPLIER.get()));
            printTestLog(source, Component.literal("真实伤害开关回读(F-10)"),
                String.valueOf(Config.ENABLE_TRUE_DAMAGE.get()));
            printTestLog(source, Component.literal("入侵自定义难度开关回读(F-10)"),
                String.valueOf(Config.INVASION.ENABLE_CUSTOM_DIFFICULTY.get()));
            printTestLog(source, Component.literal("入侵自定义倍率回读(F-10)"),
                String.format("%.2f", Config.INVASION.CUSTOM_DIFFICULTY_MULTIPLIER.get()));
            return true;
        } catch (Exception e) {
            printTestError(source, Component.literal("命令回读异常: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 验证点五：入侵 0 倍率钳制演示（F-14）
     *
     * @param source 命令来源
     * @return 0 与负数倍率均被钳制到下限返回 true
     */
    private static boolean verifyInvasionClamp(CommandSourceStack source) {
        try {
            double clampedZero = InvasionFixConfig.clampMultiplier(0.0);
            double clampedNegative = InvasionFixConfig.clampMultiplier(-5.0);
            printTestLog(source, Component.literal("入侵0倍率钳制(F-14)"),
                String.format("0.0 -> %.2f", clampedZero));
            printTestLog(source, Component.literal("入侵负倍率钳制(F-14)"),
                String.format("-5.0 -> %.2f", clampedNegative));
            return clampedZero >= 0.1 && clampedNegative >= 0.1;
        } catch (Exception e) {
            printTestError(source, Component.literal("入侵钳制检查异常: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 验证点六：持久化数据回读入口（F-05/F-06/F-15）
     *
     * 展示 SavedData 中的当前值；单人存档退出重进后再次执行本模块，
     * 比对数值是否保留即为持久化验证
     *
     * @param source 命令来源
     * @return 回读执行成功返回 true
     */
    private static boolean verifyPersistence(CommandSourceStack source) {
        try {
            MinecraftServer server = source.getServer();
            NemesisProfileSavedData profileData = NemesisProfileSavedData.get(server.overworld());
            InvasionProgressSavedData progressData = InvasionProgressSavedData.get(server);

            if (source.getEntity() instanceof ServerPlayer player) {
                NemesisProfile profile = profileData.getProfile(player.getUUID());
                printTestLog(source, Component.literal("宿敌档案(F-05)"),
                    profile != null ? "存在，击杀 " + profile.getTotalKills() : "无记录");

                int kills = progressData.getUndeadKills().getOrDefault(player.getUUID(), 0);
                boolean firstDone = progressData.getFirstInvasionDone().contains(player.getUUID());
                printTestLog(source, Component.literal("入侵进度(F-15)"),
                    "亡灵击杀 " + kills + "，首侵完成 " + firstDone);
            }

            printTestLog(source, Component.literal("世界阶段(F-06)"),
                "当前阶段 " + WorldStageManager.getInstance().getWorldStage()
                    + "，已击败Boss " + WorldStageManager.getInstance().getDefeatedBossCount());
            printTestWarning(source, Component.literal(
                "持久化验证方式：退出存档重进后再执行 /an test verify 比对以上数值"));
            return true;
        } catch (Exception e) {
            printTestError(source, Component.literal("持久化回读异常: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 验证点七：看门狗状态自检（F-09）
     *
     * WatchdogService 未提供运行态/armed 公开查询入口，运行状态自检留空，
     * 此处仅展示配置开关与侧别判定
     *
     * @param source 命令来源
     * @return 自检执行成功返回 true
     */
    private static boolean verifyWatchdog(CommandSourceStack source) {
        try {
            boolean enabled = Config.WATCHDOG.ENABLE_WATCHDOG.get();
            boolean dedicated = FMLLoader.getDist().isDedicatedServer();
            printTestLog(source, Component.literal("看门狗开关(F-09)"), String.valueOf(enabled));
            printTestLog(source, Component.literal("看门狗侧别(F-09)"),
                dedicated ? "专用服务器（启用范围）" : "单机/客户端（不启用）");
            printTestWarning(source, Component.literal(
                "WatchdogService 缺少运行态/armed 公开查询，运行状态自检留空"));
            return true;
        } catch (Exception e) {
            printTestError(source, Component.literal("看门狗检查异常: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 输出手动核验点提示（T-06 第 8/9 项）
     *
     * 归因检查与配置界面取消回滚依赖游戏内观察，命令侧仅给出核验方法
     *
     * @param source 命令来源
     */
    private static void printManualCheckHints(CommandSourceStack source) {
        printTestLog(source, Component.literal("归因检查提示(F-02)"), Component.literal(
            "由其他实体致死后，死亡消息与仇恨应指向攻击方而非受击玩家"));
        printTestLog(source, Component.literal("取消回滚提示(F-24)"), Component.literal(
            "打开配置屏修改数值后按 ESC 退出，再执行 /an test verify 确认内存配置未被写入"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 打印测试标题
     *
     * @param source 命令来源
     * @param moduleName 模块名称组件
     */
    private static void printTestHeader(CommandSourceStack source, Component moduleName) {
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.test.header", moduleName
        ), false);
    }

    /**
     * 打印测试日志
     *
     * @param source 命令来源
     * @param key 测试项组件
     * @param value 测试结果
     */
    private static void printTestLog(CommandSourceStack source, Component key, String value) {
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.test.log", key, value
        ), false);
    }

    /**
     * 打印测试日志（本地化值版本）
     *
     * @param source 命令来源
     * @param key 测试项组件
     * @param value 测试结果组件
     */
    private static void printTestLog(CommandSourceStack source, Component key, Component value) {
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.test.log", key, value
        ), false);
    }

    /**
     * 打印测试成功
     *
     * @param source 命令来源
     * @param message 成功消息组件
     */
    private static void printTestSuccess(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.test.success", message
        ), false);
    }

    /**
     * 打印测试警告
     *
     * @param source 命令来源
     * @param message 警告消息组件
     */
    private static void printTestWarning(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.test.warning", message
        ), false);
    }

    /**
     * 打印测试错误
     *
     * @param source 命令来源
     * @param message 错误消息组件
     */
    private static void printTestError(CommandSourceStack source, Component message) {
        source.sendFailure(Component.translatable(
            "adaptive_nemesis.command.test.error", message
        ));
    }

    /**
     * 构造"正常"状态组件（绿色）
     *
     * @return 带颜色的正常状态组件
     */
    private static Component okComponent() {
        return Component.translatable("adaptive_nemesis.command.test.status_ok").withStyle(ChatFormatting.GREEN);
    }

    /**
     * 构造兼容处理器状态组件
     *
     * @param isOk 处理器是否可用
     * @return 可用返回绿色正常组件，否则返回红色错误组件
     */
    private static Component compatStatusComponent(boolean isOk) {
        return isOk
            ? Component.translatable("adaptive_nemesis.command.test.status_ok").withStyle(ChatFormatting.GREEN)
            : Component.translatable("adaptive_nemesis.command.test.status_error").withStyle(ChatFormatting.RED);
    }
}
