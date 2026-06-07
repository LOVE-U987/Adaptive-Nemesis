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

            // 测试配置值范围
            if (difficulty < 0.1 || difficulty > 10.0) {
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
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok").getString());

            // 测试玩家强度计算（如果执行者是玩家）
            if (source.getEntity() instanceof ServerPlayer player) {
                PlayerStrengthData strengthData = evaluator.updatePlayerStrength(player);
                if (strengthData != null) {
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_strength_calc"), "✓ " + Component.translatable("adaptive_nemesis.command.test.status_ok").getString());
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_total_strength"), String.format("%.2f", strengthData.getTotalStrength()));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_defense_strength"), String.format("%.2f", strengthData.getDefenseStrength()));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_damage_strength"), String.format("%.2f", strengthData.getDamageStrength()));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_apotheosis"), String.format("%.2f", strengthData.getApotheosisStrength()));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_irons_spells"), String.format("%.2f", strengthData.getIronsSpellsStrength()));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_epic_fight"), String.format("%.2f", strengthData.getEpicFightStrength()));

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
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok").getString());

            // 测试浮动系统
            AdaptiveFloatSystem floatSystem = AdaptiveFloatSystem.getInstance();
            double floatMultiplier = floatSystem.getFloatMultiplier();
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_current_float"), String.format("%.2f", floatMultiplier));

            // 验证浮动倍率范围
            if (floatMultiplier < Config.FLOAT_MIN.get() || floatMultiplier > Config.FLOAT_MAX.get()) {
                printTestWarning(source, Component.translatable("adaptive_nemesis.command.test.label_float_out_of_range"));
            }

            // 显示敌人加成上限配置
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_max_hp_cap"), String.format("%.2f", Config.MAX_HEALTH_MULTIPLIER.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_max_damage_cap"), String.format("%.2f", Config.MAX_DAMAGE_MULTIPLIER.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_max_armor_cap"), String.format("%.2f", Config.MAX_ARMOR_MULTIPLIER.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_sync_range"), Config.AREA_SYNC_RANGE.get() + " 区块");

            // 如果执行者是玩家，显示针对该玩家的敌人加成
            if (source.getEntity() instanceof ServerPlayer player) {
                PlayerStrengthData strengthData = PlayerStrengthEvaluator.getInstance().getPlayerStrength(player);
                if (strengthData != null) {
                    double baseMultiplier = 1.0 + (strengthData.getTotalStrength() * Config.DIFFICULTY_BASE_MULTIPLIER.get() / 100.0);
                    double finalMultiplier = baseMultiplier * floatMultiplier;

                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_your_base"), String.format("%.2f", baseMultiplier));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_your_final"), String.format("%.2f", finalMultiplier));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_enemy_hp_bonus"), String.format("+%.0f%%", (Math.min(finalMultiplier, Config.MAX_HEALTH_MULTIPLIER.get()) - 1.0) * 100));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_enemy_damage_bonus"), String.format("+%.0f%%", (Math.min(finalMultiplier, Config.MAX_DAMAGE_MULTIPLIER.get()) - 1.0) * 100));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_enemy_armor_bonus"), String.format("+%.0f%%", (Math.min(finalMultiplier, Config.MAX_ARMOR_MULTIPLIER.get()) - 1.0) * 100));
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
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok").getString());

            // 测试护甲阈值计算
            double lowPercent = handler.getTrueDamagePercent(10);   // 低护甲
            double mediumPercent = handler.getTrueDamagePercent(30); // 中护甲
            double highPercent = handler.getTrueDamagePercent(70);   // 高护甲
            double turtlePercent = handler.getTrueDamagePercent(150); // 铁乌龟

            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_low_armor_threshold"), "< " + Config.LOW_ARMOR_THRESHOLD.get());
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_medium_armor_threshold"), Config.LOW_ARMOR_THRESHOLD.get() + " - " + Config.MEDIUM_ARMOR_THRESHOLD.get());
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_high_armor_threshold"), Config.MEDIUM_ARMOR_THRESHOLD.get() + " - " + Config.HIGH_ARMOR_THRESHOLD.get());
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_turtle_threshold"), "> " + Config.HIGH_ARMOR_THRESHOLD.get());

            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_low_true_damage"), String.format("%.1f%%", lowPercent));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_medium_true_damage"), String.format("%.1f%%", mediumPercent));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_high_true_damage"), String.format("%.1f%%", highPercent));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_turtle_true_damage"), String.format("%.1f%%", turtlePercent));

            // 验证阈值递增
            if (!(lowPercent <= mediumPercent && mediumPercent <= highPercent && highPercent <= turtlePercent)) {
                printTestWarning(source, Component.translatable("adaptive_nemesis.command.test.label_td_not_increasing"));
            }

            // 如果执行者是玩家，显示针对该玩家的真实伤害
            if (source.getEntity() instanceof ServerPlayer player) {
                double armorValue = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
                double playerTrueDamagePercent = handler.getTrueDamagePercent(armorValue);
                String armorLevel = handler.getArmorLevelDescription(armorValue);

                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_your_armor"), String.format("%.1f", armorValue));
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_your_armor_level"), armorLevel);
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_your_td_percent"), String.format("%.1f%%", playerTrueDamagePercent));

                // 计算实际伤害示例
                double exampleDamage = 20.0;
                double trueDamage = exampleDamage * (playerTrueDamagePercent / 100.0);
                double reducedDamage = exampleDamage - trueDamage;

                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_damage_example"), String.format("%.1f 伤害", exampleDamage));
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_true_damage_part"), String.format("%.1f", trueDamage));
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_mitigated_part"), String.format("%.1f", reducedDamage));
            }

            printTestSuccess(source, Component.translatable("adaptive_nemesis.command.test.module_damage"));
            return 1;
        } catch (Exception e) {
            printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_damage_error", e.getMessage()));
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
        printTestHeader(source, Component.translatable("adaptive_nemesis.command.test.module_boss"));

        try {
            BossDamageCapHandler handler = BossDamageCapHandler.getInstance();
            if (handler == null) {
                printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_no_boss_handler"));
                return 0;
            }
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok").getString());

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
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok").getString());

            // 测试默认倍率
            double defaultMultiplier = floatSystem.getFloatMultiplier();
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_default_float"), String.format("%.2f", defaultMultiplier));

            // 测试配置值
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_float_min"), String.valueOf(Config.FLOAT_MIN.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_float_max"), String.valueOf(Config.FLOAT_MAX.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_kill_bonus"), String.format("+%.0f%%", Config.KILL_STREAK_MULTIPLIER_INCREASE.get() * 100));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_death_reduction"), String.format("-%.0f%%", Config.DEATH_STREAK_MULTIPLIER_DECREASE.get() * 100));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_reset_time"), Config.FLOAT_RESET_TIME_MINUTES.get() + " 分钟");

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
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok").getString());

            // 测试档案获取
            if (source.getEntity() instanceof ServerPlayer player) {
                NemesisProfile profile = memorySystem.getProfile(player.getUUID());
                if (profile != null) {
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_player_profile"), "✓ " + Component.translatable("adaptive_nemesis.command.test.status_exists").getString());
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_total_kills"), String.valueOf(profile.getTotalKills()));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_total_deaths"), String.valueOf(profile.getTotalDeaths()));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_kda"), String.format("%.2f", profile.getKdaRatio()));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_dominant_style"), profile.getDominantStyle().getDisplayName());
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_melee_kills"), String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.MELEE)));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_ranged_kills"), String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.RANGED)));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_magic_kills"), String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.MAGIC)));

                    // 显示宿敌加成（新增）
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_nemesis_bonus"), "");
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_melee_resist"), String.format("+%.1f%%", profile.getMeleeResistanceBonus() * 100));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_ranged_resist"), String.format("+%.1f%%", profile.getRangedResistanceBonus() * 100));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_magic_resist"), String.format("+%.1f%%", profile.getMagicResistanceBonus() * 100));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_attack_bonus"), String.format("+%.1f%%", profile.getAttackBonus() * 100));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_speed_bonus"), String.format("+%.1f%%", profile.getSpeedBonus() * 100));
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_health_bonus"), String.format("+%.1f%%", profile.getHealthBonus() * 100));

                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_bonus_summary"), profile.getBonusDescription());
                } else {
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_player_profile"), "✗ " + Component.translatable("adaptive_nemesis.command.test.status_no_records").getString());
                    printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_tip"), Component.translatable("adaptive_nemesis.command.test.tip_kill_first").getString());
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
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.singleton"), Component.translatable("adaptive_nemesis.command.test.status_ok").getString());

            // 测试配置
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_protection_enabled"), String.valueOf(Config.ENABLE_NEWBIE_PROTECTION.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_strength_threshold"), String.valueOf(Config.NEWBIE_STRENGTH_THRESHOLD.get()));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_duration_info"), Config.NEWBIE_PROTECTION_DURATION.get() + "分钟");
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_reduction"), String.format("%.0f%%", Config.NEWBIE_PROTECTION_REDUCTION.get() * 100));
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_death_bonus"), Config.DEATH_PROTECTION_BONUS.get() + "分钟");
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_death_streak"), String.valueOf(Config.DEATH_STREAK_THRESHOLD.get()));

            // 测试玩家保护状态
            if (source.getEntity() instanceof ServerPlayer player) {
                boolean isProtected = handler.isProtected(player.getUUID());
                double reduction = handler.getProtectionReduction(player.getUUID());
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_protection_status"), Component.translatable(isProtected ? "adaptive_nemesis.command.enabled" : "adaptive_nemesis.command.disabled").getString());
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

            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_mod_irons_spells"), Component.translatable(ironsSpellsLoaded ? "adaptive_nemesis.command.test.loaded" : "adaptive_nemesis.command.test.not_loaded").getString());
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_mod_epic_fight"), Component.translatable(epicFightLoaded ? "adaptive_nemesis.command.test.loaded" : "adaptive_nemesis.command.test.not_loaded").getString());
            printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_mod_apotheosis"), Component.translatable(apotheosisLoaded ? "adaptive_nemesis.command.test.loaded" : "adaptive_nemesis.command.test.not_loaded").getString());

            // 测试兼容处理器
            if (ironsSpellsLoaded) {
                var ironsCompat = ModCompatManager.getIronsSpellsCompat();
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_irons_compat"), ironsCompat != null ? "✓ " + Component.translatable("adaptive_nemesis.command.test.status_ok").getString() : "✗ " + Component.translatable("adaptive_nemesis.command.test.status_error").getString());
            }

            if (epicFightLoaded) {
                var epicCompat = ModCompatManager.getEpicFightCompat();
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_epic_compat"), epicCompat != null ? "✓ " + Component.translatable("adaptive_nemesis.command.test.status_ok").getString() : "✗ " + Component.translatable("adaptive_nemesis.command.test.status_error").getString());
            }

            if (apotheosisLoaded) {
                var apothCompat = ModCompatManager.getApotheosisCompat();
                printTestLog(source, Component.translatable("adaptive_nemesis.command.test.label_apoth_compat"), apothCompat != null ? "✓ " + Component.translatable("adaptive_nemesis.command.test.status_ok").getString() : "✗ " + Component.translatable("adaptive_nemesis.command.test.status_error").getString());
            }

            printTestSuccess(source, Component.translatable("adaptive_nemesis.command.test.module_compat"));
            return 1;
        } catch (Exception e) {
            printTestError(source, Component.translatable("adaptive_nemesis.command.test.label_compat_error", e.getMessage()));
            return 0;
        }
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
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.test.error", message
        ), false);
    }
}
