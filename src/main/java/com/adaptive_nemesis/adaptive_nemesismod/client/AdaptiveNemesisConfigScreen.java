package com.adaptive_nemesis.adaptive_nemesismod.client;

import com.adaptive_nemesis.adaptive_nemesismod.Config;

import net.neoforged.neoforge.common.ModConfigSpec;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 自适应宿敌模组配置界面
 *
 * 提供模组列表中可直接访问的配置屏幕，采用暗色游戏风格设计：
 * - 流畅的滚动动画和控件入场动画
 * - 分类折叠/展开功能
 * - 实时预览效果
 * - 悬停高亮和提示
 *
 * 注意：遵循 NeoForge 1.21.1 配置界面最佳实践：
 * - 禁用背景模糊（避免文字模糊）
 * - 手动控制渲染顺序（避免控件超出边界）
 * - 动态可见性控制（避免超出边界仍可交互）
 * - 使用裁剪区域（正确裁剪内容）
 *
 * @author Adaptive Nemesis Team
 * @version 1.2.0
 */
public class AdaptiveNemesisConfigScreen extends Screen {

    /** 父屏幕引用 */
    private final Screen parent;
    /** 配置是否已修改 */
    private boolean configChanged = false;
    /** 所有配置控件列表 */
    private final List<AbstractWidget> configWidgets = new ArrayList<>();
    /** 所有配置项标签列表（用于渲染） */
    private final List<ConfigEntry> configEntries = new ArrayList<>();
    /** 当前滚动偏移（目标值，用于平滑动画） */
    private float scrollOffset = 0;
    /** 当前 tick 的实际滚动位置（tick 中推进） */
    private float currentScroll = 0;
    /** 上一 tick 的实际滚动位置（用于帧间插值） */
    private float lastScroll = 0;
    /** 内容总高度 */
    private int totalContentHeight = 0;
    /** 界面打开时间（用于入场动画） */
    private float openTime = 0;
    /** 是否正在拖拽滚动条 */
    private boolean isDraggingScrollbar = false;
    /** 打开界面时被修改配置值的快照（ESC/取消路径回滚用） */
    private final java.util.Map<ModConfigSpec.ConfigValue<?>, Object> snapshots = new java.util.LinkedHashMap<>();

    // 布局常量
    private static final int LIST_TOP = 48;
    private static final int LIST_BOTTOM = 48;
    private static final int PADDING = 28;
    private static final int ROW_HEIGHT = 30;
    private static final int CATEGORY_HEIGHT = 26;
    private static final int WIDGET_WIDTH = 110;

    // 颜色常量 - 暗色游戏风格
    private static final int BG_COLOR = 0xFF151515;
    private static final int PANEL_COLOR = 0xDD111111;
    private static final int BORDER_COLOR = 0xFF333333;
    private static final int BORDER_LIGHT = 0xFF4A4A4A;
    private static final int TEXT_PRIMARY = 0xFFE8E8E8;
    private static final int TEXT_SECONDARY = 0xFF999999;
    private static final int TEXT_HIGHLIGHT = 0xFFFFB020;
    private static final int CATEGORY_BG = 0xFF1E1E1E;
    private static final int ROW_HOVER_BG = 0x22FFFFFF;
    private static final int SCROLLBAR_BG = 0xFF2A2A2A;
    private static final int SCROLLBAR_THUMB = 0xFF666666;
    private static final int SCROLLBAR_THUMB_HOVER = 0xFF888888;

    // 动画常量
    private static final float SCROLL_SMOOTHNESS = 0.15f;
    private static final float OPEN_ANIMATION_DURATION = 20.0f;

    /**
     * 配置项数据类
     * 用于同步控件和文本渲染
     * 缓存翻译后的组件，避免每帧重复创建翻译对象
     */
    private static class ConfigEntry {
        final String labelKey;
        final int categoryColor;
        final boolean isCategory;
        /** 缓存翻译后的标签组件 */
        final Component labelComponent;
        /** 缓存翻译后的提示组件（分类条目为 null） */
        final Component tooltipComponent;
        AbstractWidget widget;
        int renderY;

        ConfigEntry(String labelKey, String tooltipKey, int categoryColor, boolean isCategory) {
            this.labelKey = labelKey;
            this.categoryColor = categoryColor;
            this.isCategory = isCategory;
            this.labelComponent = Component.translatable(labelKey);
            this.tooltipComponent = tooltipKey != null ? Component.translatable(tooltipKey) : null;
        }
    }

    /**
     * 构造函数
     *
     * @param parent 父屏幕
     */
    public AdaptiveNemesisConfigScreen(Screen parent) {
        super(Component.translatable("adaptive_nemesis.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 检查玩家是否在游戏内
        if (this.minecraft.player == null || this.minecraft.level == null) {
            // 主菜单等无世界场景直接切屏会异常，延迟一帧处理
            net.minecraft.client.gui.screens.Screen screen = createNoPlayerNoticeScreen();
            net.minecraft.client.Minecraft.getInstance().tell(() -> this.minecraft.setScreen(screen));
            return;
        }
        
        this.configWidgets.clear();
        this.configEntries.clear();
        this.scrollOffset = 0;
        this.currentScroll = 0;
        this.lastScroll = 0;
        this.openTime = 0;
        this.isDraggingScrollbar = false;

        int widgetX = this.width - PADDING - WIDGET_WIDTH;
        int currentY = addAllSections(widgetX);

        this.totalContentHeight = currentY - LIST_TOP;

        // 添加返回按钮
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> confirmAndClose())
            .bounds(this.width / 2 - 100, this.height - 36, 200, 24)
            .build());
    }

    /**
     * 创建无世界场景的提示界面（主菜单打开配置屏时展示）
     *
     * 界面文案说明配置修改需进入世界才能生效，仅提供关闭按钮
     *
     * @return 提示界面实例
     */
    private net.minecraft.client.gui.screens.Screen createNoPlayerNoticeScreen() {
        return new net.minecraft.client.gui.screens.Screen(
            Component.translatable("adaptive_nemesis.config.error.no_player.title")) {
            @Override
            protected void init() {
                super.init();

                // 添加关闭按钮
                this.addRenderableWidget(new Button.Builder(
                    Component.translatable("adaptive_nemesis.config.error.no_player.close"),
                    button -> {
                        AdaptiveNemesisConfigScreen.this.minecraft.setScreen(AdaptiveNemesisConfigScreen.this.parent);
                    }
                ).bounds(width / 2 - 100, height / 2 + 40, 200, 20).build());
            }

            @Override
            public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
                super.render(guiGraphics, mouseX, mouseY, partialTicks);

                // 居中显示提示信息（说明需进入世界）
                String message = Component.translatable("adaptive_nemesis.config.error.no_player.message").getString();
                int messageWidth = this.font.width(message);
                guiGraphics.drawString(this.font, message, this.width / 2 - messageWidth / 2, this.height / 2 - 20,
                    0xFFFFFF, true);
            }
        };
    }


    // ==================== 配置分组条目构建 ====================

    /**
     * 依次构建全部配置分组条目
     *
     * @param widgetX 控件 X 坐标
     * @return 全部条目结束 Y 坐标
     */
    private int addAllSections(int widgetX) {
        int currentY = LIST_TOP;

        currentY = addBasicSection(currentY, widgetX);
        currentY = addEnemyBonusCapSection(currentY, widgetX);
        currentY = addTrueDamageSection(currentY, widgetX);
        currentY = addBossSection(currentY, widgetX);
        currentY = addNewbieSection(currentY, widgetX);
        currentY = addRandomSection(currentY, widgetX);
        currentY = addSmoothingSection(currentY, widgetX);
        currentY = addAdaptiveFloatSection(currentY, widgetX);
        currentY = addFloatDecaySection(currentY, widgetX);
        currentY = addWorldStageSection(currentY, widgetX);
        currentY = addEnchantmentSection(currentY, widgetX);
        currentY = addEquipmentSection(currentY, widgetX);
        currentY = addWeaponDamageCapSection(currentY, widgetX);
        currentY = addWeightsSection(currentY, widgetX);
        currentY = addEpicFightSection(currentY, widgetX);
        currentY = addModCompatSection(currentY, widgetX);
        currentY = addEntityFilterSection(currentY, widgetX);
        currentY = addWatchdogSection(currentY, widgetX);
        currentY = addNemesisSection(currentY, widgetX);
        currentY = addNemesisNameStyleSection(currentY, widgetX);
        currentY = addInvasionSection(currentY, widgetX);
        currentY = addInvasionDifficultySection(currentY, widgetX);
        currentY = addInvasionNotifySection(currentY, widgetX);
        currentY = addDebugSection(currentY, widgetX);
        return currentY;
    }

    /**
     * 添加 basic 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addBasicSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.basic", 0xFFFFAA00);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.difficulty_base_multiplier",
            "adaptive_nemesis.config.tooltip.difficulty_base_multiplier",
            createDoubleEditBox(Config.DIFFICULTY_BASE_MULTIPLIER.get(), 0.1, 20.0,
                value -> setConfigValue(Config.DIFFICULTY_BASE_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.area_sync_range",
            "adaptive_nemesis.config.tooltip.area_sync_range",
            createIntEditBox(Config.AREA_SYNC_RANGE.get(), 1, 32,
                value -> setConfigValue(Config.AREA_SYNC_RANGE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_enemy_bonus_cap",
            "adaptive_nemesis.config.tooltip.enable_enemy_bonus_cap",
            createBooleanButton(Config.ENABLE_ENEMY_BONUS_CAP.get(),
                value -> setConfigValue(Config.ENABLE_ENEMY_BONUS_CAP, value)),
            widgetX);
        return addEnemyBonusCapSection(currentY, widgetX);
    }

    /**
     * 添加敌人加成上限配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addEnemyBonusCapSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_health_multiplier",
            "adaptive_nemesis.config.tooltip.max_health_multiplier",
            createDoubleEditBox(Config.MAX_HEALTH_MULTIPLIER.get(), 1.0, 50.0,
                value -> setConfigValue(Config.MAX_HEALTH_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_damage_multiplier",
            "adaptive_nemesis.config.tooltip.max_damage_multiplier",
            createDoubleEditBox(Config.MAX_DAMAGE_MULTIPLIER.get(), 1.0, 50.0,
                value -> setConfigValue(Config.MAX_DAMAGE_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_armor_multiplier",
            "adaptive_nemesis.config.tooltip.max_armor_multiplier",
            createDoubleEditBox(Config.MAX_ARMOR_MULTIPLIER.get(), 1.0, 50.0,
                value -> setConfigValue(Config.MAX_ARMOR_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_spell_power_multiplier",
            "adaptive_nemesis.config.tooltip.max_spell_power_multiplier",
            createDoubleEditBox(Config.MAX_SPELL_POWER_MULTIPLIER.get(), 1.0, 100.0,
                value -> setConfigValue(Config.MAX_SPELL_POWER_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_spell_resist_multiplier",
            "adaptive_nemesis.config.tooltip.max_spell_resist_multiplier",
            createDoubleEditBox(Config.MAX_SPELL_RESIST_MULTIPLIER.get(), 1.0, 100.0,
                value -> setConfigValue(Config.MAX_SPELL_RESIST_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_hit_resist_multiplier",
            "adaptive_nemesis.config.tooltip.max_hit_resist_multiplier",
            createDoubleEditBox(Config.MAX_HIT_RESIST_MULTIPLIER.get(), 1.0, 100.0,
                value -> setConfigValue(Config.MAX_HIT_RESIST_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_knockdown_resist_multiplier",
            "adaptive_nemesis.config.tooltip.max_knockdown_resist_multiplier",
            createDoubleEditBox(Config.MAX_KNOCKDOWN_RESIST_MULTIPLIER.get(), 1.0, 100.0,
                value -> setConfigValue(Config.MAX_KNOCKDOWN_RESIST_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_stamina_multiplier",
            "adaptive_nemesis.config.tooltip.max_stamina_multiplier",
            createDoubleEditBox(Config.MAX_STAMINA_MULTIPLIER.get(), 1.0, 100.0,
                value -> setConfigValue(Config.MAX_STAMINA_MULTIPLIER, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 true_damage 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addTrueDamageSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.true_damage", 0xFFFF5555);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_true_damage",
            "adaptive_nemesis.config.tooltip.enable_true_damage",
            createBooleanButton(Config.ENABLE_TRUE_DAMAGE.get(),
                value -> setConfigValue(Config.ENABLE_TRUE_DAMAGE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.low_armor_threshold",
            "adaptive_nemesis.config.tooltip.low_armor_threshold",
            createIntEditBox(Config.LOW_ARMOR_THRESHOLD.get(), 1, 50,
                value -> setConfigValue(Config.LOW_ARMOR_THRESHOLD, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.low_armor_true_damage_percent",
            "adaptive_nemesis.config.tooltip.low_armor_true_damage_percent",
            createDoubleEditBox(Config.LOW_ARMOR_TRUE_DAMAGE_PERCENT.get(), 0.0, 100.0,
                value -> setConfigValue(Config.LOW_ARMOR_TRUE_DAMAGE_PERCENT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.medium_armor_threshold",
            "adaptive_nemesis.config.tooltip.medium_armor_threshold",
            createIntEditBox(Config.MEDIUM_ARMOR_THRESHOLD.get(), 0, 200,
                value -> setConfigValue(Config.MEDIUM_ARMOR_THRESHOLD, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.medium_armor_true_damage_percent",
            "adaptive_nemesis.config.tooltip.medium_armor_true_damage_percent",
            createDoubleEditBox(Config.MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT.get(), 0.0, 100.0,
                value -> setConfigValue(Config.MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.high_armor_threshold",
            "adaptive_nemesis.config.tooltip.high_armor_threshold",
            createIntEditBox(Config.HIGH_ARMOR_THRESHOLD.get(), 0, 500,
                value -> setConfigValue(Config.HIGH_ARMOR_THRESHOLD, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.high_armor_true_damage_percent",
            "adaptive_nemesis.config.tooltip.high_armor_true_damage_percent",
            createDoubleEditBox(Config.HIGH_ARMOR_TRUE_DAMAGE_PERCENT.get(), 0.0, 100.0,
                value -> setConfigValue(Config.HIGH_ARMOR_TRUE_DAMAGE_PERCENT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.turtle_true_damage_percent",
            "adaptive_nemesis.config.tooltip.turtle_true_damage_percent",
            createDoubleEditBox(Config.TURTLE_TRUE_DAMAGE_PERCENT.get(), 0.0, 100.0,
                value -> setConfigValue(Config.TURTLE_TRUE_DAMAGE_PERCENT, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 boss 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addBossSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.boss", 0xFFAA55FF);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_boss_damage_cap",
            "adaptive_nemesis.config.tooltip.enable_boss_damage_cap",
            createBooleanButton(Config.ENABLE_BOSS_DAMAGE_CAP.get(),
                value -> setConfigValue(Config.ENABLE_BOSS_DAMAGE_CAP, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.boss_health_multiplier",
            "adaptive_nemesis.config.tooltip.boss_health_multiplier",
            createDoubleEditBox(Config.BOSS_HEALTH_MULTIPLIER.get(), 1.0, 20.0,
                value -> setConfigValue(Config.BOSS_HEALTH_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.boss_damage_multiplier",
            "adaptive_nemesis.config.tooltip.boss_damage_multiplier",
            createDoubleEditBox(Config.BOSS_DAMAGE_MULTIPLIER.get(), 1.0, 20.0,
                value -> setConfigValue(Config.BOSS_DAMAGE_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.boss_damage_cap",
            "adaptive_nemesis.config.tooltip.boss_damage_cap",
            createDoubleEditBox(Config.BOSS_DAMAGE_CAP.get(), 1.0, 10000.0,
                value -> setConfigValue(Config.BOSS_DAMAGE_CAP, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.boss_damage_cap_exclusions",
            "adaptive_nemesis.config.tooltip.boss_damage_cap_exclusions",
            createStringEditBox("adaptive_nemesis.config.boss_damage_cap_exclusions",
                () -> Config.BOSS_DAMAGE_CAP_EXCLUSIONS.get(),
                value -> setConfigValue(Config.BOSS_DAMAGE_CAP_EXCLUSIONS, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.boss_identification_keywords",
            "adaptive_nemesis.config.tooltip.boss_identification_keywords",
            createStringEditBox("adaptive_nemesis.config.boss_identification_keywords",
                () -> Config.BOSS_IDENTIFICATION_KEYWORDS.get(),
                value -> setConfigValue(Config.BOSS_IDENTIFICATION_KEYWORDS, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.boss_health_threshold",
            "adaptive_nemesis.config.tooltip.boss_health_threshold",
            createDoubleEditBox(Config.BOSS_HEALTH_THRESHOLD.get(), 0.0, 10000.0,
                value -> setConfigValue(Config.BOSS_HEALTH_THRESHOLD, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 newbie 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addNewbieSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.newbie", 0xFF55FF55);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_newbie_protection",
            "adaptive_nemesis.config.tooltip.enable_newbie_protection",
            createBooleanButton(Config.ENABLE_NEWBIE_PROTECTION.get(),
                value -> setConfigValue(Config.ENABLE_NEWBIE_PROTECTION, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.newbie_strength_threshold",
            "adaptive_nemesis.config.tooltip.newbie_strength_threshold",
            createDoubleEditBox(Config.NEWBIE_STRENGTH_THRESHOLD.get(), 0.0, 1000.0,
                value -> setConfigValue(Config.NEWBIE_STRENGTH_THRESHOLD, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.newbie_protection_duration",
            "adaptive_nemesis.config.tooltip.newbie_protection_duration",
            createIntEditBox(Config.NEWBIE_PROTECTION_DURATION.get(), 0, 120,
                value -> setConfigValue(Config.NEWBIE_PROTECTION_DURATION, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.newbie_protection_reduction",
            "adaptive_nemesis.config.tooltip.newbie_protection_reduction",
            createDoubleEditBox(Config.NEWBIE_PROTECTION_REDUCTION.get(), 0.0, 1.0,
                value -> setConfigValue(Config.NEWBIE_PROTECTION_REDUCTION, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.death_protection_bonus",
            "adaptive_nemesis.config.tooltip.death_protection_bonus",
            createIntEditBox(Config.DEATH_PROTECTION_BONUS.get(), 0, 60,
                value -> setConfigValue(Config.DEATH_PROTECTION_BONUS, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.death_streak_threshold",
            "adaptive_nemesis.config.tooltip.death_streak_threshold",
            createIntEditBox(Config.DEATH_STREAK_THRESHOLD.get(), 1, 10,
                value -> setConfigValue(Config.DEATH_STREAK_THRESHOLD, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 random 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addRandomSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.random", 0xFF55AAFF);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_random_distribution",
            "adaptive_nemesis.config.tooltip.enable_random_distribution",
            createBooleanButton(Config.ENABLE_RANDOM_DISTRIBUTION.get(),
                value -> setConfigValue(Config.ENABLE_RANDOM_DISTRIBUTION, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.fix_speed_bonus",
            "adaptive_nemesis.config.tooltip.fix_speed_bonus",
            createBooleanButton(Config.FIX_SPEED_BONUS_TO_ZERO.get(),
                value -> setConfigValue(Config.FIX_SPEED_BONUS_TO_ZERO, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.random_min_factor",
            "adaptive_nemesis.config.tooltip.random_min_factor",
            createDoubleEditBox(Config.RANDOM_MIN_FACTOR.get(), 0.1, 1.0,
                value -> setConfigValue(Config.RANDOM_MIN_FACTOR, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.random_max_factor",
            "adaptive_nemesis.config.tooltip.random_max_factor",
            createDoubleEditBox(Config.RANDOM_MAX_FACTOR.get(), 1.0, 2.0,
                value -> setConfigValue(Config.RANDOM_MAX_FACTOR, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 smoothing 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addSmoothingSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.smoothing", 0xFF55CCFF);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_difficulty_smoothing",
            "adaptive_nemesis.config.tooltip.enable_difficulty_smoothing",
            createBooleanButton(Config.ENABLE_DIFFICULTY_SMOOTHING.get(),
                value -> setConfigValue(Config.ENABLE_DIFFICULTY_SMOOTHING, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.difficulty_smoothing_factor",
            "adaptive_nemesis.config.tooltip.difficulty_smoothing_factor",
            createDoubleEditBox(Config.DIFFICULTY_SMOOTHING_FACTOR.get(), 0.01, 0.5,
                value -> setConfigValue(Config.DIFFICULTY_SMOOTHING_FACTOR, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.difficulty_smoothing_tick_interval",
            "adaptive_nemesis.config.tooltip.difficulty_smoothing_tick_interval",
            createIntEditBox(Config.DIFFICULTY_SMOOTHING_TICK_INTERVAL.get(), 1, 40,
                value -> setConfigValue(Config.DIFFICULTY_SMOOTHING_TICK_INTERVAL, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 adaptive_float 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addAdaptiveFloatSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.adaptive_float", 0xFF66CCFF);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.float_min",
            "adaptive_nemesis.config.tooltip.float_min",
            createDoubleEditBox(Config.FLOAT_MIN.get(), 0.1, 1.0,
                value -> setConfigValue(Config.FLOAT_MIN, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.float_max",
            "adaptive_nemesis.config.tooltip.float_max",
            createDoubleEditBox(Config.FLOAT_MAX.get(), 1.0, 5.0,
                value -> setConfigValue(Config.FLOAT_MAX, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.kill_streak_multiplier_increase",
            "adaptive_nemesis.config.tooltip.kill_streak_multiplier_increase",
            createDoubleEditBox(Config.KILL_STREAK_MULTIPLIER_INCREASE.get(), 0.0, 1.0,
                value -> setConfigValue(Config.KILL_STREAK_MULTIPLIER_INCREASE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.death_streak_multiplier_decrease",
            "adaptive_nemesis.config.tooltip.death_streak_multiplier_decrease",
            createDoubleEditBox(Config.DEATH_STREAK_MULTIPLIER_DECREASE.get(), 0.0, 1.0,
                value -> setConfigValue(Config.DEATH_STREAK_MULTIPLIER_DECREASE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.float_reset_time_minutes",
            "adaptive_nemesis.config.tooltip.float_reset_time_minutes",
            createIntEditBox(Config.FLOAT_RESET_TIME_MINUTES.get(), 1, 60,
                value -> setConfigValue(Config.FLOAT_RESET_TIME_MINUTES, value)),
            widgetX);
        return addFloatDecaySection(currentY, widgetX);
    }

    /**
     * 添加浮动衰减与效率调整配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addFloatDecaySection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_idle_decay",
            "adaptive_nemesis.config.tooltip.enable_idle_decay",
            createBooleanButton(Config.ENABLE_IDLE_DECAY.get(),
                value -> setConfigValue(Config.ENABLE_IDLE_DECAY, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.idle_decay_rate",
            "adaptive_nemesis.config.tooltip.idle_decay_rate",
            createDoubleEditBox(Config.IDLE_DECAY_RATE.get(), 0.0, 0.5,
                value -> setConfigValue(Config.IDLE_DECAY_RATE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.idle_decay_check_interval",
            "adaptive_nemesis.config.tooltip.idle_decay_check_interval",
            createIntEditBox(Config.IDLE_DECAY_CHECK_INTERVAL.get(), 1, 60,
                value -> setConfigValue(Config.IDLE_DECAY_CHECK_INTERVAL, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_efficiency_adjustment",
            "adaptive_nemesis.config.tooltip.enable_efficiency_adjustment",
            createBooleanButton(Config.ENABLE_EFFICIENCY_ADJUSTMENT.get(),
                value -> setConfigValue(Config.ENABLE_EFFICIENCY_ADJUSTMENT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.combat_efficiency_threshold",
            "adaptive_nemesis.config.tooltip.combat_efficiency_threshold",
            createDoubleEditBox(Config.COMBAT_EFFICIENCY_THRESHOLD.get(), 0.0, 1.0,
                value -> setConfigValue(Config.COMBAT_EFFICIENCY_THRESHOLD, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.efficiency_based_decrease",
            "adaptive_nemesis.config.tooltip.efficiency_based_decrease",
            createDoubleEditBox(Config.EFFICIENCY_BASED_DECREASE.get(), 0.0, 0.5,
                value -> setConfigValue(Config.EFFICIENCY_BASED_DECREASE, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 world_stage 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addWorldStageSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.world_stage", 0xFFFF8855);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_world_stage",
            "adaptive_nemesis.config.tooltip.enable_world_stage",
            createBooleanButton(Config.ENABLE_WORLD_STAGE.get(),
                value -> setConfigValue(Config.ENABLE_WORLD_STAGE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.world_stage_multiplier_per_stage",
            "adaptive_nemesis.config.tooltip.world_stage_multiplier_per_stage",
            createDoubleEditBox(Config.WORLD_STAGE_MULTIPLIER_PER_STAGE.get(), 0.0, 5.0,
                value -> setConfigValue(Config.WORLD_STAGE_MULTIPLIER_PER_STAGE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.world_stage_max_stage",
            "adaptive_nemesis.config.tooltip.world_stage_max_stage",
            createIntEditBox(Config.WORLD_STAGE_MAX_STAGE.get(), 1, 100,
                value -> setConfigValue(Config.WORLD_STAGE_MAX_STAGE, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 enchantment 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addEnchantmentSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.enchantment", 0xFF88FF55);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_enchantment_scaling",
            "adaptive_nemesis.config.tooltip.enable_enchantment_scaling",
            createBooleanButton(Config.ENABLE_ENCHANTMENT_SCALING.get(),
                value -> setConfigValue(Config.ENABLE_ENCHANTMENT_SCALING, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enchantment_chance_base",
            "adaptive_nemesis.config.tooltip.enchantment_chance_base",
            createDoubleEditBox(Config.ENCHANTMENT_CHANCE_BASE.get(), 0.0, 1.0,
                value -> setConfigValue(Config.ENCHANTMENT_CHANCE_BASE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enchantment_chance_per_difficulty",
            "adaptive_nemesis.config.tooltip.enchantment_chance_per_difficulty",
            createDoubleEditBox(Config.ENCHANTMENT_CHANCE_PER_DIFFICULTY.get(), 0.0, 0.5,
                value -> setConfigValue(Config.ENCHANTMENT_CHANCE_PER_DIFFICULTY, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enchantment_level_per_difficulty",
            "adaptive_nemesis.config.tooltip.enchantment_level_per_difficulty",
            createDoubleEditBox(Config.ENCHANTMENT_LEVEL_PER_DIFFICULTY.get(), 0.0, 5.0,
                value -> setConfigValue(Config.ENCHANTMENT_LEVEL_PER_DIFFICULTY, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enchantment_max_level",
            "adaptive_nemesis.config.tooltip.enchantment_max_level",
            createIntEditBox(Config.ENCHANTMENT_MAX_LEVEL.get(), 1, 10,
                value -> setConfigValue(Config.ENCHANTMENT_MAX_LEVEL, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 equipment 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addEquipmentSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.equipment", 0xFF44FFAA);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.equipment_base_chance",
            "adaptive_nemesis.config.tooltip.equipment_base_chance",
            createDoubleEditBox(Config.EQUIPMENT_BASE_CHANCE.get(), 0.0, 1.0,
                value -> setConfigValue(Config.EQUIPMENT_BASE_CHANCE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.equipment_chance_per_difficulty",
            "adaptive_nemesis.config.tooltip.equipment_chance_per_difficulty",
            createDoubleEditBox(Config.EQUIPMENT_CHANCE_PER_DIFFICULTY.get(), 0.0, 1.0,
                value -> setConfigValue(Config.EQUIPMENT_CHANCE_PER_DIFFICULTY, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.equipment_tier_upgrade_chance",
            "adaptive_nemesis.config.tooltip.equipment_tier_upgrade_chance",
            createDoubleEditBox(Config.EQUIPMENT_TIER_UPGRADE_CHANCE.get(), 0.0, 1.0,
                value -> setConfigValue(Config.EQUIPMENT_TIER_UPGRADE_CHANCE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.equipment_mod_compat_chance",
            "adaptive_nemesis.config.tooltip.equipment_mod_compat_chance",
            createDoubleEditBox(Config.EQUIPMENT_MOD_COMPAT_CHANCE.get(), 0.0, 1.0,
                value -> setConfigValue(Config.EQUIPMENT_MOD_COMPAT_CHANCE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.disable_equipment_drop",
            "adaptive_nemesis.config.tooltip.disable_equipment_drop",
            createBooleanButton(Config.DISABLE_EQUIPMENT_DROP.get(),
                value -> setConfigValue(Config.DISABLE_EQUIPMENT_DROP, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.disabled_equipment_slots",
            "adaptive_nemesis.config.tooltip.disabled_equipment_slots",
            createStringEditBox("adaptive_nemesis.config.disabled_equipment_slots",
                () -> Config.DISABLED_EQUIPMENT_SLOTS.get(),
                value -> setConfigValue(Config.DISABLED_EQUIPMENT_SLOTS, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.equipment_blacklist",
            "adaptive_nemesis.config.tooltip.equipment_blacklist",
            createStringEditBox("adaptive_nemesis.config.equipment_blacklist",
                () -> Config.EQUIPMENT_BLACKLIST.get(),
                value -> setConfigValue(Config.EQUIPMENT_BLACKLIST, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 weapon_damage_cap 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addWeaponDamageCapSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.weapon_damage_cap", 0xFF88FF88);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.weapon_damage_base_cap",
            "adaptive_nemesis.config.tooltip.weapon_damage_base_cap",
            createDoubleEditBox(Config.WEAPON_DAMAGE_BASE_CAP.get(), 1.0, 100.0,
                value -> setConfigValue(Config.WEAPON_DAMAGE_BASE_CAP, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.weapon_damage_cap_per_difficulty",
            "adaptive_nemesis.config.tooltip.weapon_damage_cap_per_difficulty",
            createDoubleEditBox(Config.WEAPON_DAMAGE_CAP_PER_DIFFICULTY.get(), 0.0, 50.0,
                value -> setConfigValue(Config.WEAPON_DAMAGE_CAP_PER_DIFFICULTY, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.weapon_damage_max_cap",
            "adaptive_nemesis.config.tooltip.weapon_damage_max_cap",
            createDoubleEditBox(Config.WEAPON_DAMAGE_MAX_CAP.get(), 1.0, 500.0,
                value -> setConfigValue(Config.WEAPON_DAMAGE_MAX_CAP, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 weights 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addWeightsSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.weights", 0xFFFFAA55);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.defense_weight",
            "adaptive_nemesis.config.tooltip.defense_weight",
            createDoubleEditBox(Config.DEFENSE_WEIGHT.get(), 0.0, 5.0,
                value -> setConfigValue(Config.DEFENSE_WEIGHT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.damage_weight",
            "adaptive_nemesis.config.tooltip.damage_weight",
            createDoubleEditBox(Config.DAMAGE_WEIGHT.get(), 0.0, 5.0,
                value -> setConfigValue(Config.DAMAGE_WEIGHT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.apotheosis_weight",
            "adaptive_nemesis.config.tooltip.apotheosis_weight",
            createDoubleEditBox(Config.APOTHEOSIS_WEIGHT.get(), 0.0, 5.0,
                value -> setConfigValue(Config.APOTHEOSIS_WEIGHT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.irons_spells_weight",
            "adaptive_nemesis.config.tooltip.irons_spells_weight",
            createDoubleEditBox(Config.IRONS_SPELLS_WEIGHT.get(), 0.0, 5.0,
                value -> setConfigValue(Config.IRONS_SPELLS_WEIGHT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.epic_fight_weight",
            "adaptive_nemesis.config.tooltip.epic_fight_weight",
            createDoubleEditBox(Config.EPIC_FIGHT_WEIGHT.get(), 0.0, 5.0,
                value -> setConfigValue(Config.EPIC_FIGHT_WEIGHT, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 epic_fight 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addEpicFightSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.epic_fight", 0xFFFF6644);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.weight_min_bonus",
            "adaptive_nemesis.config.tooltip.weight_min_bonus",
            createDoubleEditBox(Config.WEIGHT_MIN_BONUS.get(), 0.0, 100.0,
                value -> setConfigValue(Config.WEIGHT_MIN_BONUS, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.weight_per_multiplier",
            "adaptive_nemesis.config.tooltip.weight_per_multiplier",
            createDoubleEditBox(Config.WEIGHT_PER_MULTIPLIER.get(), 0.0, 200.0,
                value -> setConfigValue(Config.WEIGHT_PER_MULTIPLIER, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 mod_compat 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addModCompatSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.mod_compat", 0xFF44BBFF);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.mod_compat_l2hostility",
            "adaptive_nemesis.config.tooltip.mod_compat_l2hostility",
            createBooleanButton(Config.MOD_COMPAT_L2HOSTILITY_ENABLED.get(),
                value -> setConfigValue(Config.MOD_COMPAT_L2HOSTILITY_ENABLED, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.mod_compat_epic_fight",
            "adaptive_nemesis.config.tooltip.mod_compat_epic_fight",
            createBooleanButton(Config.MOD_COMPAT_EPIC_FIGHT_ENABLED.get(),
                value -> setConfigValue(Config.MOD_COMPAT_EPIC_FIGHT_ENABLED, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.mod_compat_irons_spells",
            "adaptive_nemesis.config.tooltip.mod_compat_irons_spells",
            createBooleanButton(Config.MOD_COMPAT_IRONS_SPELLS_ENABLED.get(),
                value -> setConfigValue(Config.MOD_COMPAT_IRONS_SPELLS_ENABLED, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.mod_compat_apotheosis",
            "adaptive_nemesis.config.tooltip.mod_compat_apotheosis",
            createBooleanButton(Config.MOD_COMPAT_APOTHEOSIS_ENABLED.get(),
                value -> setConfigValue(Config.MOD_COMPAT_APOTHEOSIS_ENABLED, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 entity_filter 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addEntityFilterSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.entity_filter", 0xFFFF8800);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_entity_filter",
            "adaptive_nemesis.config.tooltip.enable_entity_filter",
            createBooleanButton(Config.ENABLE_ENTITY_FILTER.get(),
                value -> setConfigValue(Config.ENABLE_ENTITY_FILTER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.entity_blacklist",
            "adaptive_nemesis.config.tooltip.entity_blacklist",
            createStringEditBox("adaptive_nemesis.config.entity_blacklist",
                () -> Config.ENTITY_BLACKLIST.get(),
                value -> setConfigValue(Config.ENTITY_BLACKLIST, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 watchdog 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addWatchdogSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.watchdog", 0xFFAA00AA);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_watchdog",
            "adaptive_nemesis.config.tooltip.enable_watchdog",
            createBooleanButton(Config.WATCHDOG.ENABLE_WATCHDOG.get(),
                value -> setConfigValue(Config.WATCHDOG.ENABLE_WATCHDOG, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.watchdog_check_interval",
            "adaptive_nemesis.config.tooltip.watchdog_check_interval",
            createIntEditBox(Config.WATCHDOG.WATCHDOG_CHECK_INTERVAL.get(), 1, 30,
                value -> setConfigValue(Config.WATCHDOG.WATCHDOG_CHECK_INTERVAL, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.watchdog_warn_threshold",
            "adaptive_nemesis.config.tooltip.watchdog_warn_threshold",
            createIntEditBox(Config.WATCHDOG.WATCHDOG_WARN_THRESHOLD.get(), 10, 120,
                value -> setConfigValue(Config.WATCHDOG.WATCHDOG_WARN_THRESHOLD, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.watchdog_critical_threshold",
            "adaptive_nemesis.config.tooltip.watchdog_critical_threshold",
            createIntEditBox(Config.WATCHDOG.WATCHDOG_CRITICAL_THRESHOLD.get(), 30, 300,
                value -> setConfigValue(Config.WATCHDOG.WATCHDOG_CRITICAL_THRESHOLD, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 nemesis 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addNemesisSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.nemesis", 0xFF00AAFF);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_nemesis_spawn",
            "adaptive_nemesis.config.tooltip.enable_nemesis_spawn",
            createBooleanButton(Config.NEMESIS.ENABLE_NEMESIS_SPAWN.get(),
                value -> setConfigValue(Config.NEMESIS.ENABLE_NEMESIS_SPAWN, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.nemesis_spawn_chance",
            "adaptive_nemesis.config.tooltip.nemesis_spawn_chance",
            createDoubleEditBox(Config.NEMESIS.NEMESIS_SPAWN_CHANCE.get(), 0.0, 1.0,
                value -> setConfigValue(Config.NEMESIS.NEMESIS_SPAWN_CHANCE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.nemesis_base_multiplier",
            "adaptive_nemesis.config.tooltip.nemesis_base_multiplier",
            createDoubleEditBox(Config.NEMESIS.NEMESIS_BASE_MULTIPLIER.get(), 1.0, 10.0,
                value -> setConfigValue(Config.NEMESIS.NEMESIS_BASE_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.nemesis_min_multiplier",
            "adaptive_nemesis.config.tooltip.nemesis_min_multiplier",
            createDoubleEditBox(Config.NEMESIS.NEMESIS_MIN_MULTIPLIER.get(), 1.0, 10.0,
                value -> setConfigValue(Config.NEMESIS.NEMESIS_MIN_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.nemesis_max_multiplier",
            "adaptive_nemesis.config.tooltip.nemesis_max_multiplier",
            createDoubleEditBox(Config.NEMESIS.NEMESIS_MAX_MULTIPLIER.get(), 1.0, 20.0,
                value -> setConfigValue(Config.NEMESIS.NEMESIS_MAX_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.show_nemesis_name",
            "adaptive_nemesis.config.tooltip.show_nemesis_name",
            createBooleanButton(Config.NEMESIS.SHOW_NEMESIS_NAME.get(),
                value -> setConfigValue(Config.NEMESIS.SHOW_NEMESIS_NAME, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.nemesis_name_always_visible",
            "adaptive_nemesis.config.tooltip.nemesis_name_always_visible",
            createBooleanButton(Config.NEMESIS.NEMESIS_NAME_ALWAYS_VISIBLE.get(),
                value -> setConfigValue(Config.NEMESIS.NEMESIS_NAME_ALWAYS_VISIBLE, value)),
            widgetX);
        return addNemesisNameStyleSection(currentY, widgetX);
    }

    /**
     * 添加宿敌称号词缀与样式配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addNemesisNameStyleSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.melee_nemesis_prefixes",
            "adaptive_nemesis.config.tooltip.melee_nemesis_prefixes",
            createStringEditBox("adaptive_nemesis.config.melee_nemesis_prefixes",
                () -> Config.NEMESIS.MELEE_NEMESIS_PREFIXES.get(),
                value -> setConfigValue(Config.NEMESIS.MELEE_NEMESIS_PREFIXES, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.ranged_nemesis_prefixes",
            "adaptive_nemesis.config.tooltip.ranged_nemesis_prefixes",
            createStringEditBox("adaptive_nemesis.config.ranged_nemesis_prefixes",
                () -> Config.NEMESIS.RANGED_NEMESIS_PREFIXES.get(),
                value -> setConfigValue(Config.NEMESIS.RANGED_NEMESIS_PREFIXES, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.magic_nemesis_prefixes",
            "adaptive_nemesis.config.tooltip.magic_nemesis_prefixes",
            createStringEditBox("adaptive_nemesis.config.magic_nemesis_prefixes",
                () -> Config.NEMESIS.MAGIC_NEMESIS_PREFIXES.get(),
                value -> setConfigValue(Config.NEMESIS.MAGIC_NEMESIS_PREFIXES, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.nemesis_suffixes",
            "adaptive_nemesis.config.tooltip.nemesis_suffixes",
            createStringEditBox("adaptive_nemesis.config.nemesis_suffixes",
                () -> Config.NEMESIS.NEMESIS_SUFFIXES.get(),
                value -> setConfigValue(Config.NEMESIS.NEMESIS_SUFFIXES, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.nemesis_name_color",
            "adaptive_nemesis.config.tooltip.nemesis_name_color",
            createStringEditBox("adaptive_nemesis.config.nemesis_name_color",
                () -> Config.NEMESIS.NEMESIS_NAME_COLOR.get(),
                value -> setConfigValue(Config.NEMESIS.NEMESIS_NAME_COLOR, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.nemesis_require_attack_damage",
            "adaptive_nemesis.config.tooltip.nemesis_require_attack_damage",
            createBooleanButton(Config.NEMESIS.NEMESIS_REQUIRE_ATTACK_DAMAGE.get(),
                value -> setConfigValue(Config.NEMESIS.NEMESIS_REQUIRE_ATTACK_DAMAGE, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 invasion 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addInvasionSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.invasion", 0xFFFF00AA);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_invasion",
            "adaptive_nemesis.config.tooltip.enable_invasion",
            createBooleanButton(Config.INVASION.ENABLE_INVASION.get(),
                value -> setConfigValue(Config.INVASION.ENABLE_INVASION, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.invasion_trigger_chance",
            "adaptive_nemesis.config.tooltip.invasion_trigger_chance",
            createDoubleEditBox(Config.INVASION.INVASION_TRIGGER_CHANCE.get(), 0.0, 1.0,
                value -> setConfigValue(Config.INVASION.INVASION_TRIGGER_CHANCE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.base_wave_count",
            "adaptive_nemesis.config.tooltip.base_wave_count",
            createIntEditBox(Config.INVASION.BASE_WAVE_COUNT.get(), 1, 10,
                value -> setConfigValue(Config.INVASION.BASE_WAVE_COUNT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_wave_count",
            "adaptive_nemesis.config.tooltip.max_wave_count",
            createIntEditBox(Config.INVASION.MAX_WAVE_COUNT.get(), 1, 20,
                value -> setConfigValue(Config.INVASION.MAX_WAVE_COUNT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_enemies_per_wave",
            "adaptive_nemesis.config.tooltip.max_enemies_per_wave",
            createIntEditBox(Config.INVASION.MAX_ENEMIES_PER_WAVE.get(), 1, 50,
                value -> setConfigValue(Config.INVASION.MAX_ENEMIES_PER_WAVE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.spawn_distance",
            "adaptive_nemesis.config.tooltip.spawn_distance",
            createIntEditBox(Config.INVASION.SPAWN_DISTANCE.get(), 10, 100,
                value -> setConfigValue(Config.INVASION.SPAWN_DISTANCE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_frost_walker_on_water",
            "adaptive_nemesis.config.tooltip.enable_frost_walker_on_water",
            createBooleanButton(Config.INVASION.ENABLE_FROST_WALKER_ON_WATER.get(),
                value -> setConfigValue(Config.INVASION.ENABLE_FROST_WALKER_ON_WATER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_glowing_effect",
            "adaptive_nemesis.config.tooltip.enable_glowing_effect",
            createBooleanButton(Config.INVASION.ENABLE_GLOWING_EFFECT.get(),
                value -> setConfigValue(Config.INVASION.ENABLE_GLOWING_EFFECT, value)),
            widgetX);
        return addInvasionDifficultySection(currentY, widgetX);
    }

    /**
     * 添加入侵自定义难度与计时配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addInvasionDifficultySection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.difficulty_decrease_on_victory",
            "adaptive_nemesis.config.tooltip.difficulty_decrease_on_victory",
            createDoubleEditBox(Config.INVASION.DIFFICULTY_DECREASE_ON_VICTORY.get(), 0.0, 0.5,
                value -> setConfigValue(Config.INVASION.DIFFICULTY_DECREASE_ON_VICTORY, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.loot_rarity_bonus",
            "adaptive_nemesis.config.tooltip.loot_rarity_bonus",
            createDoubleEditBox(Config.INVASION.LOOT_RARITY_BONUS.get(), 0.0, 1.0,
                value -> setConfigValue(Config.INVASION.LOOT_RARITY_BONUS, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_custom_difficulty",
            "adaptive_nemesis.config.tooltip.enable_custom_difficulty",
            createBooleanButton(Config.INVASION.ENABLE_CUSTOM_DIFFICULTY.get(),
                value -> setConfigValue(Config.INVASION.ENABLE_CUSTOM_DIFFICULTY, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.custom_difficulty_multiplier",
            "adaptive_nemesis.config.tooltip.custom_difficulty_multiplier",
            createDoubleEditBox(Config.INVASION.CUSTOM_DIFFICULTY_MULTIPLIER.get(), 0.1, 10.0,
                value -> setConfigValue(Config.INVASION.CUSTOM_DIFFICULTY_MULTIPLIER, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.initial_cooldown_minutes",
            "adaptive_nemesis.config.tooltip.initial_cooldown_minutes",
            createIntEditBox(Config.INVASION.INITIAL_COOLDOWN_MINUTES.get(), 1, 60,
                value -> setConfigValue(Config.INVASION.INITIAL_COOLDOWN_MINUTES, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.warning_seconds_before_invasion",
            "adaptive_nemesis.config.tooltip.warning_seconds_before_invasion",
            createIntEditBox(Config.INVASION.WARNING_SECONDS_BEFORE_INVASION.get(), 1, 60,
                value -> setConfigValue(Config.INVASION.WARNING_SECONDS_BEFORE_INVASION, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.loot_rarity_bonus_duration",
            "adaptive_nemesis.config.tooltip.loot_rarity_bonus_duration",
            createIntEditBox(Config.INVASION.LOOT_RARITY_BONUS_DURATION.get(), 1, 120,
                value -> setConfigValue(Config.INVASION.LOOT_RARITY_BONUS_DURATION, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.invasion_cooldown_minutes",
            "adaptive_nemesis.config.tooltip.invasion_cooldown_minutes",
            createIntEditBox(Config.INVASION.INVASION_COOLDOWN_MINUTES.get(), 1, 120,
                value -> setConfigValue(Config.INVASION.INVASION_COOLDOWN_MINUTES, value)),
            widgetX);
        return addInvasionNotifySection(currentY, widgetX);
    }

    /**
     * 添加入侵通知与战利品配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addInvasionNotifySection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_player_notification",
            "adaptive_nemesis.config.tooltip.enable_player_notification",
            createBooleanButton(Config.INVASION.ENABLE_PLAYER_NOTIFICATION.get(),
                value -> setConfigValue(Config.INVASION.ENABLE_PLAYER_NOTIFICATION, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.show_wave_progress",
            "adaptive_nemesis.config.tooltip.show_wave_progress",
            createBooleanButton(Config.INVASION.SHOW_WAVE_PROGRESS.get(),
                value -> setConfigValue(Config.INVASION.SHOW_WAVE_PROGRESS, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_invasion_loot",
            "adaptive_nemesis.config.tooltip.enable_invasion_loot",
            createBooleanButton(Config.INVASION.ENABLE_INVASION_LOOT.get(),
                value -> setConfigValue(Config.INVASION.ENABLE_INVASION_LOOT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.base_loot_count",
            "adaptive_nemesis.config.tooltip.base_loot_count",
            createIntEditBox(Config.INVASION.BASE_LOOT_COUNT.get(), 1, 10,
                value -> setConfigValue(Config.INVASION.BASE_LOOT_COUNT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_kubejs_support",
            "adaptive_nemesis.config.tooltip.enable_kubejs_support",
            createBooleanButton(Config.INVASION.ENABLE_KUBEJS_SUPPORT.get(),
                value -> setConfigValue(Config.INVASION.ENABLE_KUBEJS_SUPPORT, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_data_pack_support",
            "adaptive_nemesis.config.tooltip.enable_data_pack_support",
            createBooleanButton(Config.INVASION.ENABLE_DATA_PACK_SUPPORT.get(),
                value -> setConfigValue(Config.INVASION.ENABLE_DATA_PACK_SUPPORT, value)),
            widgetX);

        return currentY;
    }

    /**
     * 添加 debug 配置分组条目
     *
     * @param currentY 起始绘制 Y 坐标
     * @param widgetX 控件 X 坐标
     * @return 结束 Y 坐标
     */
    private int addDebugSection(int currentY, int widgetX) {
        currentY = LIST_TOP;
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.debug", 0xFFAAAAAA);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_debug_log",
            "adaptive_nemesis.config.tooltip.enable_debug_log",
            createBooleanButton(Config.ENABLE_DEBUG_LOG.get(),
                value -> setConfigValue(Config.ENABLE_DEBUG_LOG, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_verbose_debug",
            "adaptive_nemesis.config.tooltip.enable_verbose_debug",
            createBooleanButton(Config.ENABLE_VERBOSE_DEBUG.get(),
                value -> setConfigValue(Config.ENABLE_VERBOSE_DEBUG, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.debug_log_to_file",
            "adaptive_nemesis.config.tooltip.debug_log_to_file",
            createBooleanButton(Config.DEBUG_LOG_TO_FILE.get(),
                value -> setConfigValue(Config.DEBUG_LOG_TO_FILE, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.debug_log_file_path",
            "adaptive_nemesis.config.tooltip.debug_log_file_path",
            createStringEditBox("adaptive_nemesis.config.debug_log_file_path",
                () -> Config.DEBUG_LOG_FILE_PATH.get(),
                value -> setConfigValue(Config.DEBUG_LOG_FILE_PATH, value)),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.log_output_level",
            "adaptive_nemesis.config.tooltip.log_output_level",
            createLogLevelButton(Config.LOG_OUTPUT_LEVEL.get(),
                value -> setConfigValue(Config.LOG_OUTPUT_LEVEL, value)),
            widgetX);
        return currentY;
    }

    /** 添加分类条目 */
    private int addCategoryEntry(int y, String labelKey, int color) {
        ConfigEntry entry = new ConfigEntry(labelKey, null, color, true);
        entry.renderY = y;
        configEntries.add(entry);
        return y + CATEGORY_HEIGHT;
    }

    /** 添加配置条目 */
    private int addConfigEntry(int y, String labelKey, String tooltipKey,
                               AbstractWidget widget, int widgetX) {
        ConfigEntry entry = new ConfigEntry(labelKey, tooltipKey, 0, false);
        entry.widget = widget;
        entry.renderY = y;

        widget.setX(widgetX);
        widget.setY(y + 3);
        widget.setWidth(WIDGET_WIDTH);
        widget.setHeight(20);
        // 使用缓存好的提示组件，避免重复翻译
        widget.setTooltip(Tooltip.create(entry.tooltipComponent));
        this.addRenderableWidget(widget);
        this.configWidgets.add(widget);
        this.configEntries.add(entry);
        return y + ROW_HEIGHT;
    }

    /** 创建布尔值切换按钮 */
    private CycleButton<Boolean> createBooleanButton(boolean initialValue, Consumer<Boolean> onChange) {
        return CycleButton.booleanBuilder(
            Component.translatable("adaptive_nemesis.config.value.on"),
            Component.translatable("adaptive_nemesis.config.value.off")
        ).displayOnlyValue()
        .withInitialValue(initialValue)
        .create(0, 0, WIDGET_WIDTH, 20, Component.empty(),
            (button, value) -> onChange.accept(value));
    }

    /** 创建数值输入框 */
    private EditBox createDoubleEditBox(double initialValue, double min, double max, Consumer<Double> onChange) {
        EditBox box = new EditBox(this.minecraft.font, 0, 0, WIDGET_WIDTH, 20, Component.empty());
        box.setValue(String.valueOf(initialValue));
        box.setResponder(value -> {
            try {
                double val = Double.parseDouble(value);
                if (val >= min && val <= max) {
                    onChange.accept(val);
                }
            } catch (NumberFormatException ignored) {}
        });
        return box;
    }

    /** 创建整数输入框 */
    private EditBox createIntEditBox(int initialValue, int min, int max, Consumer<Integer> onChange) {
        EditBox box = new EditBox(this.minecraft.font, 0, 0, WIDGET_WIDTH, 20, Component.empty());
        box.setValue(String.valueOf(initialValue));
        box.setResponder(value -> {
            try {
                int val = Integer.parseInt(value);
                if (val >= min && val <= max) {
                    onChange.accept(val);
                }
            } catch (NumberFormatException ignored) {}
        });
        return box;
    }

    /** 创建可点击打开二级编辑界面的字符串按钮 */
    private AbstractWidget createStringEditBox(String labelKey, Supplier<String> valueGetter,
                                                Consumer<String> onChange) {
        Button button = Button.builder(
            truncatePreview(valueGetter.get()),
            btn -> {
                String currentValue = valueGetter.get();
                this.minecraft.setScreen(new LongTextEditScreen(this,
                    Component.translatable(labelKey), currentValue, newValue -> {
                        onChange.accept(newValue);
                        btn.setMessage(truncatePreview(newValue));
                    }));
            }
        ).bounds(0, 0, WIDGET_WIDTH, 20).build();
        return button;
    }

    /** 截断文本为按钮预览长度，超长部分替换为 ... */
    private static Component truncatePreview(String text) {
        if (text == null || text.isEmpty()) {
            return Component.translatable("adaptive_nemesis.config.click_to_edit");
        }
        String preview = text.length() > 16 ? text.substring(0, 16) + "..." : text;
        return Component.literal(preview);
    }

    /** 创建日志级别选择按钮 */
    private CycleButton<String> createLogLevelButton(String initialValue, Consumer<String> onChange) {
        return CycleButton.<String>builder(value -> Component.literal(value))
            .withValues(java.util.List.of("OFF", "ERROR", "WARN", "INFO", "DEBUG"))
            .displayOnlyValue()
            .withInitialValue(initialValue)
            .create(0, 0, WIDGET_WIDTH, 20, Component.empty(),
                (button, value) -> onChange.accept(value));
    }

    /** 禁用背景模糊效果（避免文字模糊） */
    @Override
    protected void renderBlurredBackground(float partialTick) {
        // 空实现 - 完全禁用模糊背景
    }

    @Override
    public void tick() {
        super.tick();
        // 更新入场动画时间
        if (openTime < OPEN_ANIMATION_DURATION) {
            openTime++;
        }
        // 记录上一 tick 的滚动位置，供帧间插值使用
        lastScroll = currentScroll;
        // 平滑滚动动画（在 tick 中按固定步长推进目标）
        currentScroll += (scrollOffset - currentScroll) * SCROLL_SMOOTHNESS;
        // 接近目标时直接收敛对齐，避免动画拖尾抖动
        if (Math.abs(currentScroll - scrollOffset) < 0.01f) {
            currentScroll = scrollOffset;
            lastScroll = scrollOffset;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 计算动画进度（0.0 - 1.0）
        float animationProgress = Math.min(1.0f, openTime / OPEN_ANIMATION_DURATION);
        float easeOut = 1.0f - (1.0f - animationProgress) * (1.0f - animationProgress);

        renderHeaderBar(guiGraphics, easeOut);
        renderMainPanel(guiGraphics, easeOut);
        renderFooterBar(guiGraphics);
        float smoothScroll = renderContent(guiGraphics, mouseX, mouseY, partialTick);
        renderScrollbar(guiGraphics, mouseX, mouseY, smoothScroll);
        renderDoneButton(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * 渲染纯色背景与顶部装饰条（带动画）
     *
     * @param guiGraphics 绘图上下文
     * @param easeOut 入场动画进度
     */
    private void renderHeaderBar(GuiGraphics guiGraphics, float easeOut) {
        // 渲染纯色背景（禁用模糊）
        guiGraphics.fill(0, 0, this.width, this.height, BG_COLOR);

        // 渲染顶部装饰条（带动画）
        int headerHeight = (int) (LIST_TOP * easeOut);
        if (headerHeight <= 0) {
            return;
        }
        // 顶部渐变条
        guiGraphics.fill(0, 0, this.width, 2, BORDER_LIGHT);
        guiGraphics.fill(0, 2, this.width, 3, BORDER_COLOR);
        // 标题区域背景
        guiGraphics.fill(0, 3, this.width, headerHeight - 2, 0xFF1C1C1C);

        // 标题文字（带淡入动画）
        int titleAlpha = (int) (255 * easeOut);
        int titleColor = (titleAlpha << 24) | (TEXT_HIGHLIGHT & 0xFFFFFF);
        int subtitleColor = (titleAlpha << 24) | (TEXT_SECONDARY & 0xFFFFFF);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, titleColor);
        guiGraphics.drawCenteredString(this.font,
            Component.translatable("adaptive_nemesis.config.subtitle"), this.width / 2, 34, subtitleColor);
    }

    /**
     * 渲染主面板背景（带入场动画）
     *
     * @param guiGraphics 绘图上下文
     * @param easeOut 入场动画进度
     */
    private void renderMainPanel(GuiGraphics guiGraphics, float easeOut) {
        int panelTop = LIST_TOP;
        int panelBottom = this.height - LIST_BOTTOM;
        int panelWidth = (int) ((this.width - PADDING * 2) * easeOut);
        int panelX = (this.width - panelWidth) / 2;

        if (panelWidth <= 0) {
            return;
        }
        // 面板边框
        guiGraphics.fill(panelX - 2, panelTop - 2, panelX + panelWidth + 2, panelBottom + 2, BORDER_COLOR);
        // 面板背景
        guiGraphics.fill(panelX, panelTop, panelX + panelWidth, panelBottom, PANEL_COLOR);
    }

    /**
     * 渲染底部信息栏与保存提示
     *
     * @param guiGraphics 绘图上下文
     */
    private void renderFooterBar(GuiGraphics guiGraphics) {
        int footerY = this.height - LIST_BOTTOM + 6;
        guiGraphics.fill(0, footerY, this.width, this.height, 0xFF1C1C1C);
        guiGraphics.fill(0, footerY - 1, this.width, footerY, BORDER_COLOR);

        if (configChanged) {
            // 闪烁的保存提示
            int flash = (int) ((Math.sin(System.currentTimeMillis() / 200.0) + 1) * 0.5 * 255);
            int saveColor = 0xFF000000 | (flash << 8);
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("adaptive_nemesis.config.saved"),
                this.width / 2, footerY + 10, saveColor);
        } else {
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("adaptive_nemesis.config.scroll_hint"),
                this.width / 2, footerY + 10, TEXT_SECONDARY);
        }
    }

    /**
     * 在裁剪区域内渲染配置条目与控件
     *
     * @param guiGraphics 绘图上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param partialTick 帧插值
     * @return 帧间插值后的平滑滚动值
     */
    private float renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 启用裁剪区域（避免控件超出边界）
        guiGraphics.enableScissor(PADDING, LIST_TOP, this.width - PADDING, this.height - LIST_BOTTOM);

        // 标准帧间插值：在上一 tick 与当前 tick 的滚动位置之间按 partialTick 线性插值，
        // 使滚动在任意帧率下都平滑，避免 20fps 逐 tick 跳变导致的文字闪烁
        float smoothScroll = Mth.lerp(partialTick, lastScroll, currentScroll);
        int scrollOffsetInt = (int) smoothScroll;

        // 同步更新控件位置
        updateWidgetPositions(scrollOffsetInt);

        // 渲染所有条目
        int currentY = LIST_TOP - scrollOffsetInt;
        for (ConfigEntry entry : configEntries) {
            if (entry.isCategory) {
                currentY = renderCategory(guiGraphics, currentY, entry, mouseX, mouseY);
            } else {
                currentY = renderConfigRow(guiGraphics, currentY, entry, mouseX, mouseY);
            }
        }

        // 在裁剪区域内手动渲染所有控件（避免超出边界）
        for (AbstractWidget widget : configWidgets) {
            if (widget.visible) {
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        // 禁用裁剪区域
        guiGraphics.disableScissor();
        return smoothScroll;
    }

    /**
     * 渲染"完成"按钮（在裁剪区域外，保证可点击）
     *
     * @param guiGraphics 绘图上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param partialTick 帧插值
     */
    private void renderDoneButton(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        for (var renderable : this.renderables) {
            if (renderable instanceof Button button) {
                button.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    /** 渲染分类标题 */
    private int renderCategory(GuiGraphics guiGraphics, int y, ConfigEntry entry, int mouseX, int mouseY) {
        if (y + CATEGORY_HEIGHT > LIST_TOP && y < this.height - LIST_BOTTOM) {
            boolean isHovered = mouseX >= PADDING + 5 && mouseX <= this.width - PADDING - 5
                && mouseY >= y + 2 && mouseY <= y + CATEGORY_HEIGHT - 2;

            // 分类背景（带悬停效果）
            int bgColor = isHovered ? CATEGORY_BG + 0x080808 : CATEGORY_BG;
            guiGraphics.fill(PADDING + 5, y + 2, this.width - PADDING - 5, y + CATEGORY_HEIGHT - 2, bgColor);

            // 分类左侧装饰线（带动画）
            int lineWidth = (int) (3 + Math.sin(System.currentTimeMillis() / 500.0) * 0.5);
            guiGraphics.fill(PADDING + 5, y + 2, PADDING + 5 + lineWidth, y + CATEGORY_HEIGHT - 2, entry.categoryColor);

            // 分类文字（使用缓存组件）
            guiGraphics.drawString(this.font, entry.labelComponent, PADDING + 15, y + 8, entry.categoryColor);
        }
        return y + CATEGORY_HEIGHT;
    }

    /** 渲染配置行标签 */
    private int renderConfigRow(GuiGraphics guiGraphics, int y, ConfigEntry entry, int mouseX, int mouseY) {
        if (y + ROW_HEIGHT > LIST_TOP && y < this.height - LIST_BOTTOM) {
            boolean isHovered = mouseX >= PADDING + 15 && mouseX <= this.width - PADDING - 15
                && mouseY >= y && mouseY <= y + ROW_HEIGHT;

            // 悬停高亮背景
            if (isHovered) {
                guiGraphics.fill(PADDING + 15, y, this.width - PADDING - 15, y + ROW_HEIGHT, ROW_HOVER_BG);
            }

            // 行分隔线（带透明度动画）
            int alpha = (int) (0x2A * Math.min(1.0f, openTime / 10.0f));
            int lineColor = (alpha << 24) | 0x2A2A2A;
            guiGraphics.fill(PADDING + 15, y + ROW_HEIGHT - 1, this.width - PADDING - 15, y + ROW_HEIGHT, lineColor);

            // 标签文字（使用缓存组件）
            guiGraphics.drawString(this.font, entry.labelComponent, PADDING + 22, y + 10, TEXT_PRIMARY);
        }
        return y + ROW_HEIGHT;
    }

    /** 渲染滚动条 */
    private void renderScrollbar(GuiGraphics guiGraphics, int mouseX, int mouseY, float smoothScroll) {
        int contentHeight = this.height - LIST_TOP - LIST_BOTTOM;
        if (totalContentHeight <= contentHeight) return;

        int scrollbarX = this.width - PADDING - 8;
        int scrollbarY = LIST_TOP;
        int scrollbarHeight = contentHeight;
        int scrollbarWidth = 5;

        // 滚动条背景
        guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, SCROLLBAR_BG);

        // 计算滑块位置和高度（使用帧间插值后的滚动值，与内容同步）
        float scrollPercent = smoothScroll / (totalContentHeight - contentHeight);
        int thumbHeight = Math.max(24, (int) ((float) contentHeight / totalContentHeight * contentHeight));
        int thumbY = scrollbarY + (int) (scrollPercent * (scrollbarHeight - thumbHeight));

        // 检查鼠标是否悬停在滑块上
        boolean isThumbHovered = mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth
            && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;

        // 滚动条滑块（带悬停效果）
        int thumbColor = isThumbHovered || isDraggingScrollbar ? SCROLLBAR_THUMB_HOVER : SCROLLBAR_THUMB;
        guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, thumbColor);

        // 滑块高光
        if (isThumbHovered || isDraggingScrollbar) {
            guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 1, thumbY + thumbHeight, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, totalContentHeight - (this.height - LIST_TOP - LIST_BOTTOM));
        scrollOffset = (int) Mth.clamp(scrollOffset - scrollY * 18, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查是否点击了滚动条
        if (button == 0) {
            int contentHeight = this.height - LIST_TOP - LIST_BOTTOM;
            if (totalContentHeight > contentHeight) {
                int scrollbarX = this.width - PADDING - 8;
                int scrollbarWidth = 5;
                if (mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth + 10) {
                    int scrollbarHeight = contentHeight;
                    int thumbHeight = Math.max(24, (int) ((float) contentHeight / totalContentHeight * contentHeight));
                    float scrollPercent = currentScroll / (totalContentHeight - contentHeight);
                    int thumbY = LIST_TOP + (int) (scrollPercent * (scrollbarHeight - thumbHeight));

                    if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                        isDraggingScrollbar = true;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 支持拖拽滚动条
        if (button == 0 && isDraggingScrollbar) {
            int contentHeight = this.height - LIST_TOP - LIST_BOTTOM;
            int scrollbarHeight = contentHeight;
            int thumbHeight = Math.max(24, (int) ((float) contentHeight / totalContentHeight * contentHeight));
            float dragPercent = (float) (mouseY - LIST_TOP - thumbHeight / 2.0) / (scrollbarHeight - thumbHeight);
            int maxScroll = totalContentHeight - contentHeight;
            scrollOffset = (int) Mth.clamp(dragPercent * maxScroll, 0, maxScroll);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** 更新控件位置和可见性（关键！避免超出边界仍可交互） */
    private void updateWidgetPositions(int scrollOffsetInt) {
        int currentY = LIST_TOP - scrollOffsetInt;
        int widgetIndex = 0;
        int widgetX = this.width - PADDING - WIDGET_WIDTH;

        for (ConfigEntry entry : configEntries) {
            if (entry.isCategory) {
                currentY += CATEGORY_HEIGHT;
            } else {
                if (widgetIndex < configWidgets.size() && entry.widget != null) {
                    AbstractWidget widget = configWidgets.get(widgetIndex);
                    widget.setX(widgetX);
                    widget.setY(currentY + 3);
                    // 只有当控件在可视区域内时才显示（关键！）
                    boolean visible = currentY + ROW_HEIGHT > LIST_TOP && currentY < this.height - LIST_BOTTOM;
                    widget.visible = visible;
                    widgetIndex++;
                }
                currentY += ROW_HEIGHT;
            }
        }
    }

    @Override
    public void onClose() {
        // ESC/取消路径：回滚本次修改的内存值，不落盘
        rollbackSnapshots();
        this.minecraft.setScreen(this.parent);
    }

    /**
     * 确认路径（完成按钮）：落盘本次修改后返回上级界面
     */
    private void confirmAndClose() {
        if (this.configChanged) {
            Config.saveToFile();
        }
        this.snapshots.clear();
        this.minecraft.setScreen(this.parent);
    }

    /**
     * 将本次界面中已写入内存的配置值恢复为打开界面时的快照值
     *
     * 保证 ESC/取消路径下内存配置与配置文件一致，
     * 避免后续任意 saveToFile 将被丢弃的修改写入文件
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void rollbackSnapshots() {
        for (Map.Entry<ModConfigSpec.ConfigValue<?>, Object> entry : snapshots.entrySet()) {
            ((ModConfigSpec.ConfigValue) entry.getKey()).set(entry.getValue());
        }
        this.snapshots.clear();
        this.configChanged = false;
    }

    /**
     * 仅在值与当前配置不同才写入内存，并记录打开界面时的快照
     *
     * @param spec 配置值
     * @param newValue 界面提交的新值
     * @param <T> 配置值类型
     */
    private <T> void setConfigValue(ModConfigSpec.ConfigValue<T> spec, T newValue) {
        if (java.util.Objects.equals(spec.get(), newValue)) {
            return;
        }
        this.snapshots.putIfAbsent(spec, spec.get());
        spec.set(newValue);
        this.configChanged = true;
    }
}
