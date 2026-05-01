package com.adaptive_nemesis.adaptive_nemesismod.client;

import com.adaptive_nemesis.adaptive_nemesismod.Config;

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
import java.util.function.Consumer;

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
    /** 当前实际滚动位置（平滑插值） */
    private float currentScroll = 0;
    /** 内容总高度 */
    private int totalContentHeight = 0;
    /** 界面打开时间（用于入场动画） */
    private float openTime = 0;
    /** 是否正在拖拽滚动条 */
    private boolean isDraggingScrollbar = false;

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
     */
    private static class ConfigEntry {
        final String labelKey;
        final String tooltipKey;
        final int categoryColor;
        final boolean isCategory;
        AbstractWidget widget;
        int renderY;

        ConfigEntry(String labelKey, String tooltipKey, int categoryColor, boolean isCategory) {
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
            this.categoryColor = categoryColor;
            this.isCategory = isCategory;
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
        this.configWidgets.clear();
        this.configEntries.clear();
        this.scrollOffset = 0;
        this.currentScroll = 0;
        this.openTime = 0;
        this.isDraggingScrollbar = false;

        int currentY = LIST_TOP;
        int widgetX = this.width - PADDING - WIDGET_WIDTH;

        // ===== 基础难度 =====
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.basic", 0xFFFFAA00);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.difficulty_base_multiplier",
            "adaptive_nemesis.config.tooltip.difficulty_base_multiplier",
            createDoubleEditBox(Config.DIFFICULTY_BASE_MULTIPLIER.get(), 0.1, 20.0,
                value -> { Config.DIFFICULTY_BASE_MULTIPLIER.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.area_sync_range",
            "adaptive_nemesis.config.tooltip.area_sync_range",
            createIntEditBox(Config.AREA_SYNC_RANGE.get(), 1, 32,
                value -> { Config.AREA_SYNC_RANGE.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_enemy_bonus_cap",
            "adaptive_nemesis.config.tooltip.enable_enemy_bonus_cap",
            createBooleanButton(Config.ENABLE_ENEMY_BONUS_CAP.get(),
                value -> { Config.ENABLE_ENEMY_BONUS_CAP.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_health_multiplier",
            "adaptive_nemesis.config.tooltip.max_health_multiplier",
            createDoubleEditBox(Config.MAX_HEALTH_MULTIPLIER.get(), 1.0, 50.0,
                value -> { Config.MAX_HEALTH_MULTIPLIER.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_damage_multiplier",
            "adaptive_nemesis.config.tooltip.max_damage_multiplier",
            createDoubleEditBox(Config.MAX_DAMAGE_MULTIPLIER.get(), 1.0, 50.0,
                value -> { Config.MAX_DAMAGE_MULTIPLIER.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.max_armor_multiplier",
            "adaptive_nemesis.config.tooltip.max_armor_multiplier",
            createDoubleEditBox(Config.MAX_ARMOR_MULTIPLIER.get(), 1.0, 50.0,
                value -> { Config.MAX_ARMOR_MULTIPLIER.set(value); markChanged(); }),
            widgetX);

        // ===== 真实伤害 =====
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.true_damage", 0xFFFF5555);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_true_damage",
            "adaptive_nemesis.config.tooltip.enable_true_damage",
            createBooleanButton(Config.ENABLE_TRUE_DAMAGE.get(),
                value -> { Config.ENABLE_TRUE_DAMAGE.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.low_armor_threshold",
            "adaptive_nemesis.config.tooltip.low_armor_threshold",
            createIntEditBox(Config.LOW_ARMOR_THRESHOLD.get(), 1, 50,
                value -> { Config.LOW_ARMOR_THRESHOLD.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.low_armor_true_damage_percent",
            "adaptive_nemesis.config.tooltip.low_armor_true_damage_percent",
            createDoubleEditBox(Config.LOW_ARMOR_TRUE_DAMAGE_PERCENT.get(), 0.0, 100.0,
                value -> { Config.LOW_ARMOR_TRUE_DAMAGE_PERCENT.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.turtle_true_damage_percent",
            "adaptive_nemesis.config.tooltip.turtle_true_damage_percent",
            createDoubleEditBox(Config.TURTLE_TRUE_DAMAGE_PERCENT.get(), 0.0, 100.0,
                value -> { Config.TURTLE_TRUE_DAMAGE_PERCENT.set(value); markChanged(); }),
            widgetX);

        // ===== Boss机制 =====
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.boss", 0xFFAA55FF);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_boss_damage_cap",
            "adaptive_nemesis.config.tooltip.enable_boss_damage_cap",
            createBooleanButton(Config.ENABLE_BOSS_DAMAGE_CAP.get(),
                value -> { Config.ENABLE_BOSS_DAMAGE_CAP.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.boss_health_multiplier",
            "adaptive_nemesis.config.tooltip.boss_health_multiplier",
            createDoubleEditBox(Config.BOSS_HEALTH_MULTIPLIER.get(), 1.0, 20.0,
                value -> { Config.BOSS_HEALTH_MULTIPLIER.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.boss_damage_multiplier",
            "adaptive_nemesis.config.tooltip.boss_damage_multiplier",
            createDoubleEditBox(Config.BOSS_DAMAGE_MULTIPLIER.get(), 1.0, 20.0,
                value -> { Config.BOSS_DAMAGE_MULTIPLIER.set(value); markChanged(); }),
            widgetX);

        // ===== 新手保护 =====
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.newbie", 0xFF55FF55);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_newbie_protection",
            "adaptive_nemesis.config.tooltip.enable_newbie_protection",
            createBooleanButton(Config.ENABLE_NEWBIE_PROTECTION.get(),
                value -> { Config.ENABLE_NEWBIE_PROTECTION.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.newbie_protection_duration",
            "adaptive_nemesis.config.tooltip.newbie_protection_duration",
            createIntEditBox(Config.NEWBIE_PROTECTION_DURATION.get(), 0, 120,
                value -> { Config.NEWBIE_PROTECTION_DURATION.set(value); markChanged(); }),
            widgetX);

        // ===== 随机分布 =====
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.random", 0xFF55AAFF);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_random_distribution",
            "adaptive_nemesis.config.tooltip.enable_random_distribution",
            createBooleanButton(Config.ENABLE_RANDOM_DISTRIBUTION.get(),
                value -> { Config.ENABLE_RANDOM_DISTRIBUTION.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.fix_speed_bonus",
            "adaptive_nemesis.config.tooltip.fix_speed_bonus",
            createBooleanButton(Config.FIX_SPEED_BONUS_TO_ZERO.get(),
                value -> { Config.FIX_SPEED_BONUS_TO_ZERO.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.random_min_factor",
            "adaptive_nemesis.config.tooltip.random_min_factor",
            createDoubleEditBox(Config.RANDOM_MIN_FACTOR.get(), 0.1, 1.0,
                value -> { Config.RANDOM_MIN_FACTOR.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.random_max_factor",
            "adaptive_nemesis.config.tooltip.random_max_factor",
            createDoubleEditBox(Config.RANDOM_MAX_FACTOR.get(), 1.0, 2.0,
                value -> { Config.RANDOM_MAX_FACTOR.set(value); markChanged(); }),
            widgetX);

        // ===== 权重配置 =====
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.weights", 0xFFFFAA55);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.defense_weight",
            "adaptive_nemesis.config.tooltip.defense_weight",
            createDoubleEditBox(Config.DEFENSE_WEIGHT.get(), 0.0, 5.0,
                value -> { Config.DEFENSE_WEIGHT.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.damage_weight",
            "adaptive_nemesis.config.tooltip.damage_weight",
            createDoubleEditBox(Config.DAMAGE_WEIGHT.get(), 0.0, 5.0,
                value -> { Config.DAMAGE_WEIGHT.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.apotheosis_weight",
            "adaptive_nemesis.config.tooltip.apotheosis_weight",
            createDoubleEditBox(Config.APOTHEOSIS_WEIGHT.get(), 0.0, 5.0,
                value -> { Config.APOTHEOSIS_WEIGHT.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.irons_spells_weight",
            "adaptive_nemesis.config.tooltip.irons_spells_weight",
            createDoubleEditBox(Config.IRONS_SPELLS_WEIGHT.get(), 0.0, 5.0,
                value -> { Config.IRONS_SPELLS_WEIGHT.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.epic_fight_weight",
            "adaptive_nemesis.config.tooltip.epic_fight_weight",
            createDoubleEditBox(Config.EPIC_FIGHT_WEIGHT.get(), 0.0, 5.0,
                value -> { Config.EPIC_FIGHT_WEIGHT.set(value); markChanged(); }),
            widgetX);

        // ===== 调试选项 =====
        currentY = addCategoryEntry(currentY, "adaptive_nemesis.config.category.debug", 0xFFAAAAAA);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_debug_log",
            "adaptive_nemesis.config.tooltip.enable_debug_log",
            createBooleanButton(Config.ENABLE_DEBUG_LOG.get(),
                value -> { Config.ENABLE_DEBUG_LOG.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.enable_verbose_debug",
            "adaptive_nemesis.config.tooltip.enable_verbose_debug",
            createBooleanButton(Config.ENABLE_VERBOSE_DEBUG.get(),
                value -> { Config.ENABLE_VERBOSE_DEBUG.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.debug_log_to_file",
            "adaptive_nemesis.config.tooltip.debug_log_to_file",
            createBooleanButton(Config.DEBUG_LOG_TO_FILE.get(),
                value -> { Config.DEBUG_LOG_TO_FILE.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.debug_log_file_path",
            "adaptive_nemesis.config.tooltip.debug_log_file_path",
            createStringEditBox(Config.DEBUG_LOG_FILE_PATH.get(),
                value -> { Config.DEBUG_LOG_FILE_PATH.set(value); markChanged(); }),
            widgetX);
        currentY = addConfigEntry(currentY, "adaptive_nemesis.config.log_output_level",
            "adaptive_nemesis.config.tooltip.log_output_level",
            createLogLevelButton(Config.LOG_OUTPUT_LEVEL.get(),
                value -> { Config.LOG_OUTPUT_LEVEL.set(value); markChanged(); }),
            widgetX);

        this.totalContentHeight = currentY - LIST_TOP;

        // 添加返回按钮
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
            .bounds(this.width / 2 - 100, this.height - 36, 200, 24)
            .build());
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
        widget.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        this.addRenderableWidget(widget);
        this.configWidgets.add(widget);
        this.configEntries.add(entry);
        return y + ROW_HEIGHT;
    }

    /** 标记配置已修改 */
    private void markChanged() {
        this.configChanged = true;
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

    /** 创建字符串输入框 */
    private EditBox createStringEditBox(String initialValue, Consumer<String> onChange) {
        EditBox box = new EditBox(this.minecraft.font, 0, 0, WIDGET_WIDTH, 20, Component.empty());
        box.setValue(initialValue);
        box.setResponder(value -> {
            if (!value.isEmpty()) {
                onChange.accept(value);
            }
        });
        return box;
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
        // 平滑滚动动画
        currentScroll += (scrollOffset - currentScroll) * SCROLL_SMOOTHNESS;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 计算动画进度（0.0 - 1.0）
        float animationProgress = Math.min(1.0f, openTime / OPEN_ANIMATION_DURATION);
        float easeOut = 1.0f - (1.0f - animationProgress) * (1.0f - animationProgress);

        // 1. 渲染纯色背景（禁用模糊）
        guiGraphics.fill(0, 0, this.width, this.height, BG_COLOR);

        // 2. 渲染顶部装饰条（带动画）
        int headerHeight = (int) (LIST_TOP * easeOut);
        if (headerHeight > 0) {
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

        // 3. 渲染主面板背景（带入场动画）
        int panelTop = LIST_TOP;
        int panelBottom = this.height - LIST_BOTTOM;
        int panelWidth = (int) ((this.width - PADDING * 2) * easeOut);
        int panelX = (this.width - panelWidth) / 2;

        if (panelWidth > 0) {
            // 面板边框
            guiGraphics.fill(panelX - 2, panelTop - 2, panelX + panelWidth + 2, panelBottom + 2, BORDER_COLOR);
            // 面板背景
            guiGraphics.fill(panelX, panelTop, panelX + panelWidth, panelBottom, PANEL_COLOR);
        }

        // 4. 渲染底部信息栏
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

        // 5. 启用裁剪区域（关键！避免控件超出边界）
        guiGraphics.enableScissor(PADDING, LIST_TOP, this.width - PADDING, this.height - LIST_BOTTOM);

        // 6. 渲染配置内容（使用平滑滚动位置）
        float smoothScroll = currentScroll + (scrollOffset - currentScroll) * partialTick * SCROLL_SMOOTHNESS;
        int scrollOffsetInt = (int) smoothScroll;

        // 同步更新控件位置
        updateWidgetPositions(scrollOffsetInt);

        // 渲染所有条目
        int currentY = LIST_TOP - scrollOffsetInt;
        for (ConfigEntry entry : configEntries) {
            if (entry.isCategory) {
                currentY = renderCategory(guiGraphics, currentY, entry.labelKey, entry.categoryColor, mouseX, mouseY);
            } else {
                currentY = renderConfigRow(guiGraphics, currentY, entry, mouseX, mouseY);
            }
        }

        // 7. 在裁剪区域内手动渲染所有控件（关键！避免超出边界）
        for (AbstractWidget widget : configWidgets) {
            if (widget.visible) {
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        // 8. 禁用裁剪区域
        guiGraphics.disableScissor();

        // 9. 渲染滚动条
        renderScrollbar(guiGraphics, mouseX, mouseY);

        // 10. 渲染"完成"按钮（在裁剪区域外）
        for (var renderable : this.renderables) {
            if (renderable instanceof Button button) {
                button.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    /** 渲染分类标题 */
    private int renderCategory(GuiGraphics guiGraphics, int y, String labelKey, int color, int mouseX, int mouseY) {
        if (y + CATEGORY_HEIGHT > LIST_TOP && y < this.height - LIST_BOTTOM) {
            boolean isHovered = mouseX >= PADDING + 5 && mouseX <= this.width - PADDING - 5
                && mouseY >= y + 2 && mouseY <= y + CATEGORY_HEIGHT - 2;

            // 分类背景（带悬停效果）
            int bgColor = isHovered ? CATEGORY_BG + 0x080808 : CATEGORY_BG;
            guiGraphics.fill(PADDING + 5, y + 2, this.width - PADDING - 5, y + CATEGORY_HEIGHT - 2, bgColor);

            // 分类左侧装饰线（带动画）
            int lineWidth = (int) (3 + Math.sin(System.currentTimeMillis() / 500.0) * 0.5);
            guiGraphics.fill(PADDING + 5, y + 2, PADDING + 5 + lineWidth, y + CATEGORY_HEIGHT - 2, color);

            // 分类文字
            guiGraphics.drawString(this.font, Component.translatable(labelKey), PADDING + 15, y + 8, color);
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

            // 标签文字
            guiGraphics.drawString(this.font, Component.translatable(entry.labelKey), PADDING + 22, y + 10, TEXT_PRIMARY);
        }
        return y + ROW_HEIGHT;
    }

    /** 渲染滚动条 */
    private void renderScrollbar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int contentHeight = this.height - LIST_TOP - LIST_BOTTOM;
        if (totalContentHeight <= contentHeight) return;

        int scrollbarX = this.width - PADDING - 8;
        int scrollbarY = LIST_TOP;
        int scrollbarHeight = contentHeight;
        int scrollbarWidth = 5;

        // 滚动条背景
        guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, SCROLLBAR_BG);

        // 计算滑块位置和高度
        float scrollPercent = currentScroll / (totalContentHeight - contentHeight);
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
        this.minecraft.setScreen(this.parent);
    }
}
