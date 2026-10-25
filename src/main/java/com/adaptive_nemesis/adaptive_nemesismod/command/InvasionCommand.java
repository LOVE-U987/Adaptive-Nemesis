package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.ActiveInvasion;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionDataLoader;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem.InvasionType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ResourceLocationException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * 入侵事件命令
 *
 * 命令: /an invasion [trigger|status|setdifficulty]
 * 功能: 管理入侵事件，支持通过资源位置触发任意数据包入侵类型
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class InvasionCommand {

    /**
     * 入侵系统实例
     */
    private static InvasionSystem invasionSystem;

    /**
     * 入侵类型建议提供者
     */
    private static final SuggestionProvider<CommandSourceStack> INVASION_TYPE_SUGGESTIONS = (context, builder) -> {
        Map<ResourceLocation, com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionData> invasions =
            InvasionDataLoader.getInstance().getAllInvasions();
        for (ResourceLocation id : invasions.keySet()) {
            builder.suggest(id.toString());
        }
        return builder.buildFuture();
    };

    /**
     * 设置入侵系统实例
     *
     * @param system 入侵系统实例
     */
    public static void setInvasionSystem(InvasionSystem system) {
        invasionSystem = system;
    }

    /**
     * 注册入侵命令
     *
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("invasion")
            .then(Commands.literal("trigger")
                .executes(InvasionCommand::executeTriggerDefault)
                .then(Commands.argument("type", StringArgumentType.string())
                    .suggests(INVASION_TYPE_SUGGESTIONS)
                    .executes(InvasionCommand::executeTriggerWithType)
                    .then(Commands.argument("waves", IntegerArgumentType.integer(1, 20))
                        .then(Commands.argument("difficulty", DoubleArgumentType.doubleArg(0.1, 10.0))
                            .executes(InvasionCommand::executeTriggerCustom))))
                .then(Commands.argument("waves_legacy", IntegerArgumentType.integer(1, 20))
                    .then(Commands.argument("difficulty_legacy", DoubleArgumentType.doubleArg(0.1, 10.0))
                        .executes(InvasionCommand::executeTriggerLegacyCustom))))
            .then(Commands.literal("status")
                .executes(InvasionCommand::executeStatus))
            .then(Commands.literal("setdifficulty")
                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.1, 10.0))
                    .executes(InvasionCommand::executeSetDifficulty)));
    }

    /**
     * 执行触发入侵（默认参数）
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeTriggerDefault(CommandContext<CommandSourceStack> context) {
        return executeTrigger(context, InvasionType.UNDEAD.getId(), null, null);
    }

    /**
     * 执行触发指定类型入侵（默认参数）
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeTriggerWithType(CommandContext<CommandSourceStack> context) {
        ResourceLocation id = parseInvasionId(context);
        if (id == null) {
            return 0;
        }
        return executeTrigger(context, id, null, null);
    }

    /**
     * 执行触发指定类型入侵（自定义参数）
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeTriggerCustom(CommandContext<CommandSourceStack> context) {
        ResourceLocation id = parseInvasionId(context);
        if (id == null) {
            return 0;
        }
        int waves = IntegerArgumentType.getInteger(context, "waves");
        double difficulty = DoubleArgumentType.getDouble(context, "difficulty");
        return executeTrigger(context, id, waves, difficulty);
    }

    /**
     * 兼容旧版语法：/an invasion trigger <waves> <difficulty>
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeTriggerLegacyCustom(CommandContext<CommandSourceStack> context) {
        int waves = IntegerArgumentType.getInteger(context, "waves_legacy");
        double difficulty = DoubleArgumentType.getDouble(context, "difficulty_legacy");
        return executeTrigger(context, InvasionType.UNDEAD.getId(), waves, difficulty);
    }

    /**
     * 统一执行触发逻辑
     *
     * @param context 命令上下文
     * @param id 入侵类型资源位置
     * @param customWaves 自定义波次数，null 表示使用默认
     * @param customDifficulty 自定义难度倍率，null 表示使用默认
     * @return 命令执行结果
     */
    private static int executeTrigger(CommandContext<CommandSourceStack> context, ResourceLocation id,
                                       Integer customWaves, Double customDifficulty) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.invasion.player_only"));
            return 0;
        }

        if (invasionSystem == null) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.invasion.not_initialized"));
            return 0;
        }

        InvasionType type = InvasionType.of(id);
        boolean success;
        if (customWaves != null && customDifficulty != null) {
            success = invasionSystem.triggerInvasionManual(player, type, customWaves, customDifficulty);
        } else {
            success = invasionSystem.triggerInvasion(player.level(), player, type);
        }

        if (success) {
            String typeId = type.getId().toString();
            if (customWaves != null && customDifficulty != null) {
                source.sendSuccess(() -> Component.translatable(
                    "adaptive_nemesis.command.invasion.trigger_success_detailed", typeId, customWaves, customDifficulty
                ), true);
            } else {
                source.sendSuccess(() -> Component.translatable(
                    "adaptive_nemesis.command.invasion.trigger_success_type", typeId
                ), true);
            }
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 通过命令触发了入侵事件: 类型={}", player.getName().getString(), type
            );
            return 1;
        } else {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.invasion.trigger_failed"));
            return 0;
        }
    }

    /**
     * 从命令参数解析入侵类型标识符
     *
     * 支持两种写法：
     * 1. 完整资源位置：{@code <namespace>:<path>}
     * 2. 省略命名空间：{@code <path>}（如 {@code undead_invasion}），
     *    回退到模组命名空间 {@code adaptive_nemesis:<path>} 解析，
     *    从而可直接触发无需数据包的内置亡灵入侵
     *
     * @param context 命令上下文
     * @return 入侵类型资源位置，解析失败或不存在时返回 null 并已发送错误消息
     */
    private static ResourceLocation parseInvasionId(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String typeString = StringArgumentType.getString(context, "type");
        ResourceLocation id = ResourceLocation.tryParse(typeString);

        // 内置亡灵入侵（adaptive_nemesis:undead_invasion）无需数据包也可触发
        if (InvasionType.UNDEAD.getId().equals(id)) {
            return id;
        }

        // 省略命名空间时 tryParse 返回 null，回退到模组命名空间再试
        if (id == null) {
            id = tryParseModNamespaceId(typeString);
            if (id == null) {
                sendUnknownType(source, typeString);
                return null;
            }
        }

        // minecraft: 命名空间未加载时，回退尝试模组命名空间
        if ("minecraft".equals(id.getNamespace())
            && !InvasionDataLoader.getInstance().getAllInvasions().containsKey(id)) {
            ResourceLocation modId = tryParseModNamespaceId(id.getPath());
            if (modId != null) {
                id = modId;
            }
        }

        // 校验：允许数据包已加载的入侵类型，以及无需数据包的内置亡灵入侵
        if (!InvasionDataLoader.getInstance().getAllInvasions().containsKey(id)
            && !InvasionType.UNDEAD.getId().equals(id)) {
            sendUnknownType(source, id.toString());
            return null;
        }

        return id;
    }

    /**
     * 尝试按模组命名空间解析入侵类型路径
     *
     * 输入可能包含非法路径字符（tryParse 失败的原因不限于省略命名空间），
     * 构造资源位置时捕获非法输入异常并返回 null，避免命令执行崩溃。
     *
     * @param path 入侵类型路径
     * @return 解析成功返回资源位置，非法输入返回 null
     */
    private static ResourceLocation tryParseModNamespaceId(String path) {
        try {
            return ResourceLocation.fromNamespaceAndPath(AdaptiveNemesisMod.MODID, path);
        } catch (ResourceLocationException e) {
            return null;
        }
    }

    /**
     * 发送未知入侵类型错误消息
     *
     * @param source 命令来源
     * @param typeString 用户输入的类型标识
     */
    private static void sendUnknownType(CommandSourceStack source, String typeString) {
        source.sendFailure(Component.translatable(
            "adaptive_nemesis.command.invasion.unknown_type", typeString
        ));
    }

    /**
     * 执行查看入侵状态
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.invasion.player_only"));
            return 0;
        }

        if (invasionSystem == null) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.invasion.not_initialized"));
            return 0;
        }

        ActiveInvasion invasion = invasionSystem.getActiveInvasion(player);

        if (invasion == null) {
            source.sendSuccess(() -> Component.translatable("adaptive_nemesis.command.invasion.status_none"), true);
        } else {
            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.invasion.status_header",
                invasion.getType(),
                invasion.getCurrentWave(),
                invasion.getTotalWaves(),
                invasion.getDifficultyMultiplier()
            ), true);

            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.invasion.status_remaining",
                invasion.getRemainingEnemies()
            ), false);
        }

        return 1;
    }

    /**
     * 执行设置入侵难度
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeSetDifficulty(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double multiplier = DoubleArgumentType.getDouble(context, "multiplier");

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.invasion.difficulty_set", multiplier
        ), true);

        AdaptiveNemesisMod.LOGGER.debug(
            "管理员设置入侵自定义难度倍率为: {}", multiplier
        );

        return 1;
    }
}
