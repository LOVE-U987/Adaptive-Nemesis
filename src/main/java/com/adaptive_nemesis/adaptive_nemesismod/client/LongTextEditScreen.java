package com.adaptive_nemesis.adaptive_nemesismod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 长文本编辑弹窗界面
 * <p>
 * 为配置文件中需要输入较长文本的字段（如实体黑名单、Boss伤害排除列表等）
 * 提供更大的编辑区域，替代原先窄小的 EditBox 输入体验。
 * 视觉风格与 {@link AdaptiveNemesisConfigScreen} 主配置界面保持一致，
 * 并额外提供淡入动画、自定义主题边框、字符数徽章等视觉增强。
 * </p>
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class LongTextEditScreen extends Screen {

    // ==================== 颜色常量 - 与主配置界面保持一致 ====================
    private static final int BG_COLOR = 0xFF151515;
    private static final int PANEL_COLOR = 0xDD111111;
    private static final int BORDER_COLOR = 0xFF333333;
    private static final int BORDER_LIGHT = 0xFF4A4A4A;
    private static final int TEXT_PRIMARY = 0xFFE8E8E8;
    private static final int TEXT_SECONDARY = 0xFF999999;
    private static final int TEXT_HIGHLIGHT = 0xFFFFB020;
    private static final int CATEGORY_BG = 0xFF1E1E1E;

    // 输入框主题色
    private static final int INPUT_BORDER = 0xFF555555;
    private static final int INPUT_BORDER_FOCUS = 0xFFFFB020;
    private static final int INPUT_BG = 0xFF0D0D0D;

    // 徽章颜色
    private static final int BADGE_BG = 0xFF1A1A1A;
    private static final int BADGE_BORDER = 0xFF444444;
    private static final int BADGE_TEXT = 0xFFAAAAAA;

    // 布局常量
    private static final int HEADER_HEIGHT = 48;
    private static final int PADDING = 40;
    private static final int BOTTOM_BAR_HEIGHT = 54;

    // 动画常量
    private static final float OPEN_ANIMATION_DURATION = 15.0f;

    /** 上级界面引用，关闭时返回 */
    private final Screen parent;
    /** 初始文本值 */
    private final String initialValue;
    /** 保存回调 */
    private final Consumer<String> onSave;
    /** 当前编辑的文本值 */
    private String currentValue;
    /** 大文本输入框 */
    private EditBox textField;
    /** 界面打开时间（用于入场动画） */
    private float openTime = 0;

    /**
     * 构造长文本编辑界面
     *
     * @param parent       父界面，关闭后返回
     * @param title        界面标题
     * @param initialValue 初始文本值
     * @param onSave       保存回调，接收编辑后的文本
     */
    public LongTextEditScreen(Screen parent, Component title, String initialValue, Consumer<String> onSave) {
        super(title);
        this.parent = parent;
        this.initialValue = initialValue;
        this.onSave = onSave;
        this.currentValue = initialValue;
    }

    @Override
    protected void init() {
        // 文本框尺寸：宽屏自适应
        int boxWidth = Math.min(this.width - PADDING * 2, 640);
        int boxX = (this.width - boxWidth) / 2;
        int boxY = HEADER_HEIGHT + 24;

        textField = new EditBox(this.font, boxX, boxY, boxWidth, 22, Component.empty());
        textField.setMaxLength(Integer.MAX_VALUE);
        textField.setValue(initialValue);
        textField.setResponder(value -> this.currentValue = value);
        textField.setFocused(true);
        // 去掉原版边框，由我们自定义渲染
        textField.setBordered(false);
        textField.setTextColor(TEXT_PRIMARY);
        this.addRenderableWidget(textField);

        // 保存按钮（使用主题色）
        this.addRenderableWidget(new ThemeButton(
                this.width / 2 - 110, this.height - BOTTOM_BAR_HEIGHT + 14,
                100, 22,
                CommonComponents.GUI_DONE,
                button -> {
                    if (onSave != null) {
                        onSave.accept(currentValue);
                    }
                    this.minecraft.setScreen(parent);
                },
                true  // 强调色
        ));

        // 取消按钮
        this.addRenderableWidget(new ThemeButton(
                this.width / 2 + 10, this.height - BOTTOM_BAR_HEIGHT + 14,
                100, 22,
                CommonComponents.GUI_CANCEL,
                button -> this.minecraft.setScreen(parent),
                false // 普通色
        ));
    }

    @Override
    public void tick() {
        super.tick();
        if (openTime < OPEN_ANIMATION_DURATION) {
            openTime++;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        float animationProgress = Math.min(1.0f, openTime / OPEN_ANIMATION_DURATION);
        float easeOut = 1.0f - (1.0f - animationProgress) * (1.0f - animationProgress);

        // 1. 纯色背景（带动画淡入）
        if (easeOut > 0) {
            int bgAlpha = (int) (0xFF * easeOut);
            guiGraphics.fill(0, 0, this.width, this.height, (bgAlpha << 24) | 0x151515);
        }

        // 2. 顶部装饰栏（带动画下拉）
        int headerH = (int) (HEADER_HEIGHT * easeOut);
        if (headerH > 0) {
            guiGraphics.fill(0, 0, this.width, 2, BORDER_LIGHT);
            guiGraphics.fill(0, 2, this.width, 3, BORDER_COLOR);
            guiGraphics.fill(0, 3, this.width, headerH, 0xFF1C1C1C);

            // 标题（带动画淡入）
            int titleAlpha = (int) (255 * easeOut);
            int titleColor = (titleAlpha << 24) | (TEXT_HIGHLIGHT & 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, titleColor);

            // 标题下方的橙色装饰线（淡入展开）
            if (easeOut > 0.5f) {
                float lineProgress = (easeOut - 0.5f) * 2.0f;
                int lineWidth = (int) (60 * lineProgress);
                int lineX = (this.width - lineWidth) / 2;
                int lineAlpha = (int) (180 * lineProgress);
                int lineColor = (lineAlpha << 24) | (TEXT_HIGHLIGHT & 0xFFFFFF);
                guiGraphics.fill(lineX, HEADER_HEIGHT - 4, lineX + lineWidth, HEADER_HEIGHT - 2, lineColor);
            }
        }

        // 3. 主面板（淡入和缩放）
        if (easeOut > 0.1f) {
            float panelProgress = (easeOut - 0.1f) / 0.9f;
            int panelTop = HEADER_HEIGHT + 5;
            int panelBottom = this.height - BOTTOM_BAR_HEIGHT;
            int panelLeft = PADDING - 8;
            int panelRight = this.width - PADDING + 8;

            int panelAlpha = (int) (0xDD * panelProgress);
            int borderAlpha = (int) (0xFF * panelProgress);

            // 面板边框
            guiGraphics.fill(panelLeft - 1, panelTop - 1, panelRight + 1, panelBottom + 1,
                    (borderAlpha << 24) | (BORDER_COLOR & 0xFFFFFF));
            // 面板背景
            guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom,
                    (panelAlpha << 24) | (PANEL_COLOR & 0xFFFFFF));

            // 4. 自定义输入框背景和边框（在EditBox下方绘制）
            if (textField != null) {
                int bx = textField.getX();
                int by = textField.getY();
                int bw = textField.getWidth();
                int bh = textField.getHeight();

                // 输入框边框（聚焦时高亮）
                boolean isFocused = textField.isFocused();
                int borderCol = isFocused ? INPUT_BORDER_FOCUS : INPUT_BORDER;
                int borderGlow = isFocused ? 0x40FFB020 : 0x00000000;

                // 外发光效果（聚焦时）
                if (isFocused) {
                    guiGraphics.fill(bx - 2, by - 2, bx + bw + 2, by + bh + 2, borderGlow);
                    // 第二层发光
                    guiGraphics.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0x20FFB020);
                }

                // 输入框背景
                guiGraphics.fill(bx, by, bx + bw, by + bh, INPUT_BG);

                // 底部高光线（聚焦时橙色，普通时灰色）
                int accentY = by + bh;
                guiGraphics.fill(bx, accentY - 1, bx + bw, accentY, borderCol);

                // 左侧装饰细线（聚焦时显示）
                if (isFocused) {
                    guiGraphics.fill(bx, by, bx + 1, accentY, INPUT_BORDER_FOCUS);
                }
            }
        }

        // 5. 底部操作栏（带动画上移）
        if (easeOut > 0.3f) {
            float barProgress = (easeOut - 0.3f) / 0.7f;
            int barY = this.height - (int) (BOTTOM_BAR_HEIGHT * barProgress);
            if (barY < this.height - BOTTOM_BAR_HEIGHT) {
                barY = this.height - BOTTOM_BAR_HEIGHT;
            }

            int barAlpha = (int) (0xFF * barProgress);
            guiGraphics.fill(0, barY, this.width, barY + 1,
                    (barAlpha << 24) | (BORDER_COLOR & 0xFFFFFF));
            guiGraphics.fill(0, barY + 1, this.width, this.height,
                    (barAlpha << 24) | 0x1C1C1C);
        }

        // 6. 字符数徽章（在输入框下方、面板内）
        if (textField != null && easeOut > 0.4f) {
            float badgeProgress = (easeOut - 0.4f) / 0.6f;
            int badgeAlpha = (int) (0xFF * badgeProgress);

            String countStr = String.valueOf(currentValue.length());
            int countWidth = this.font.width(countStr);
            String unitStr = Component.translatable("adaptive_nemesis.config.edit.char_count").getString();
            int unitWidth = this.font.width(unitStr);
            int totalWidth = countWidth + 2 + unitWidth;

            int badgeX = (this.width - totalWidth - 16) / 2;
            int badgeY = textField.getY() + 30;
            int badgeW = totalWidth + 16;
            int badgeH = 14;

            // 徽章背景
            int bgAlpha = (int) (BADGE_BG >>> 24) & 0xFF;
            int bgColor = (badgeAlpha * bgAlpha / 0xFF << 24) | (BADGE_BG & 0xFFFFFF);
            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, bgColor);

            // 徽章边框
            int bAlpha = (int) (0xFF * badgeProgress);
            int borderColor = (bAlpha << 24) | (BADGE_BORDER & 0xFFFFFF);
            guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, borderColor);
            guiGraphics.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, borderColor);
            guiGraphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, borderColor);
            guiGraphics.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, borderColor);

            // 徽章文字
            int textColor = (bAlpha << 24) | (BADGE_TEXT & 0xFFFFFF);
            guiGraphics.drawString(this.font, countStr + " " + unitStr,
                    badgeX + 8, badgeY + 3, textColor);
        }

        // 7. 渲染控件（EditBox + 自定义按钮）
        if (easeOut > 0.1f) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 主题按钮控件
     * <p>
     * 继承 {@link Button} 使用暗色游戏风格替换原版按钮渲染，
     * 通过 {@code isAccent} 区分强调按钮（橙色）和普通按钮（灰色）。
     * </p>
     */
    private static class ThemeButton extends Button {

        private static final int NORMAL_BG = 0xFF222222;
        private static final int NORMAL_HOVER = 0xFF333333;
        private static final int ACCENT_BG = 0xFF3A2A0A;
        private static final int ACCENT_HOVER = 0xFF4A3A1A;
        private static final int ACCENT_BORDER = 0xFFFFB020;
        private static final int DISABLED_TEXT = 0xFF555555;

        private final boolean isAccent;

        /**
         * 构造主题按钮
         *
         * @param x        X坐标
         * @param y        Y坐标
         * @param width    宽度
         * @param height   高度
         * @param message  按钮文字
         * @param onPress  点击回调
         * @param isAccent 是否为强调按钮（橙色主题）
         */
        public ThemeButton(int x, int y, int width, int height,
                           Component message, Button.OnPress onPress, boolean isAccent) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.isAccent = isAccent;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = this.isHoveredOrFocused();
            int bgColor, borderColor;

            if (isAccent) {
                bgColor = hovered ? ACCENT_HOVER : ACCENT_BG;
                borderColor = hovered ? ACCENT_BORDER : BORDER_COLOR;
            } else {
                bgColor = hovered ? NORMAL_HOVER : NORMAL_BG;
                borderColor = hovered ? BORDER_LIGHT : BORDER_COLOR;
            }

            // 按钮背景
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
            // 按钮边框
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, borderColor);
            guiGraphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), borderColor);
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), borderColor);
            guiGraphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), borderColor);

            // 强调按钮左侧装饰线
            if (isAccent) {
                guiGraphics.fill(getX() + 1, getY() + 2, getX() + 2, getY() + getHeight() - 2, ACCENT_BORDER);
            }

            // 按钮文字
            int textColor = this.active ? TEXT_PRIMARY : DISABLED_TEXT;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            guiGraphics.drawCenteredString(mc.font, this.getMessage(),
                    getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
        }

        @Override
        public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationOutput) {
            this.defaultButtonNarrationText(narrationOutput);
        }
    }
}
