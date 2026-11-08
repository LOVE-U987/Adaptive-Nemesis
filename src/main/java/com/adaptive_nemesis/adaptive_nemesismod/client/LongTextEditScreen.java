package com.adaptive_nemesis.adaptive_nemesismod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 长文本编辑弹窗界面
 * <p>
 * 为配置文件中需要输入较长文本的字段（如实体黑名单、Boss伤害排除列表等）
 * 提供多行编辑区域：按换行符拆分行、垂直滚动、光标行高亮，
 * 替代原先单行 EditBox 的编辑体验。
 * 视觉风格与 {@link AdaptiveNemesisConfigScreen} 主配置界面保持一致。
 * </p>
 * <p>
 * 取消路径（ESC / 取消按钮）不触发保存回调，仅返回上级界面；
 * 保存路径（完成按钮）将编辑后的完整文本经 {@code onSave} 回调交回。
 * </p>
 *
 * @author Adaptive Nemesis Team
 * @version 1.2.0
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
    private static final int CURSOR_COLOR = 0xFFFFB020;
    private static final int CURSOR_LINE_BG = 0x2AFFB020;
    private static final int SCROLLBAR_TRACK_COLOR = 0xFF2A2A2A;
    private static final int SCROLLBAR_THUMB_COLOR = 0xFF666666;

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
    private static final int EDIT_TOP_GAP = 16;
    private static final int EDIT_BOTTOM_GAP = 30;
    private static final int LINE_EXTRA_SPACING = 2;
    private static final int CURSOR_BLINK_TICKS = 10;

    // 动画常量
    private static final float OPEN_ANIMATION_DURATION = 15.0f;

    /** 上级界面引用，关闭时返回 */
    private final Screen parent;
    /** 初始文本值 */
    private final String initialValue;
    /** 保存回调 */
    private final Consumer<String> onSave;
    /** 当前编辑的文本值（每次编辑后由行列表重建） */
    private String currentValue;
    /** 按换行符拆分的编辑行 */
    private final List<String> lines = new ArrayList<>();
    /** 光标所在行号（0 起） */
    private int cursorLine = 0;
    /** 光标所在列号（0 起，字符单位） */
    private int cursorCol = 0;
    /** 垂直滚动行号（编辑区顶部可见行） */
    private int scrollLine = 0;
    /** 界面打开时间（用于入场动画） */
    private float openTime = 0;
    /** 光标闪烁计时（tick） */
    private int blinkTimer = 0;

    // 编辑区几何（init 时计算）
    private int editX;
    private int editY;
    private int editWidth;
    private int editHeight;
    private int visibleLineCount;

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
        splitIntoLines(initialValue);
        // 光标置于文本末尾
        cursorLine = lines.size() - 1;
        cursorCol = lines.get(cursorLine).length();
    }

    /**
     * 将初始文本按换行符拆分为编辑行
     *
     * @param text 原始文本
     */
    private void splitIntoLines(String text) {
        lines.clear();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return;
        }
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i));
                start = i + 1;
            }
        }
        lines.add(text.substring(start));
    }

    /**
     * 由编辑行列表重建完整文本值
     */
    private void rebuildValue() {
        currentValue = String.join("\n", lines);
    }

    /**
     * 计算编辑区总行数
     *
     * @return 编辑行总数
     */
    private int lineCount() {
        return lines.size();
    }

    @Override
    protected void init() {
        // 编辑区尺寸：宽屏自适应
        int boxWidth = Math.min(this.width - PADDING * 2, 640);
        editX = (this.width - boxWidth) / 2;
        editY = HEADER_HEIGHT + EDIT_TOP_GAP;
        editWidth = boxWidth;
        editHeight = this.height - BOTTOM_BAR_HEIGHT - editY - EDIT_BOTTOM_GAP;
        int rowHeight = this.font.lineHeight + LINE_EXTRA_SPACING;
        visibleLineCount = Math.max(1, editHeight / rowHeight);

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
        blinkTimer++;
        if (blinkTimer >= CURSOR_BLINK_TICKS * 2) {
            blinkTimer = 0;
        }
    }

    // ==================== 键盘输入 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC：取消编辑，不触发保存回调
        if (keyCode == 256) {
            this.minecraft.setScreen(parent);
            return true;
        }
        if (handleEditingKey(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 处理编辑类按键
     *
     * @param keyCode 按键代码
     * @return 已处理返回 true
     */
    private boolean handleEditingKey(int keyCode) {
        switch (keyCode) {
            case 257, 335 -> insertText("\n");      // 回车（主键区/小键盘）
            case 259 -> deleteBackward();           // 退格
            case 261 -> deleteForward();            // Delete
            case 262 -> moveCursor(1, 0);           // 右
            case 263 -> moveCursor(-1, 0);          // 左
            case 264 -> moveCursor(0, 1);           // 下
            case 265 -> moveCursor(0, -1);          // 上
            case 266 -> scrollLines(visibleLineCount); // Page Up
            case 267 -> scrollLines(-visibleLineCount); // Page Down
            case 268 -> { cursorCol = 0; }          // Home
            case 269 -> { cursorCol = lines.get(cursorLine).length(); } // End
            default -> {
                return false;
            }
        }
        clampCursor();
        ensureCursorVisible();
        rebuildValue();
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint >= 32 && codePoint != 127) {
            insertText(String.valueOf(codePoint));
            clampCursor();
            ensureCursorVisible();
            rebuildValue();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    /**
     * 在光标处插入文本（字符或换行）
     *
     * @param text 待插入文本
     */
    private void insertText(String text) {
        String line = lines.get(cursorLine);
        if (text.equals("\n")) {
            // 换行：当前行拆分为两行
            String tail = line.substring(cursorCol);
            lines.set(cursorLine, line.substring(0, cursorCol));
            lines.add(cursorLine + 1, tail);
            cursorLine++;
            cursorCol = 0;
        } else {
            lines.set(cursorLine, line.substring(0, cursorCol) + text + line.substring(cursorCol));
            cursorCol += text.length();
        }
    }

    /**
     * 退格：删除光标前一个字符或合并到上一行
     */
    private void deleteBackward() {
        String line = lines.get(cursorLine);
        if (cursorCol > 0) {
            lines.set(cursorLine, line.substring(0, cursorCol - 1) + line.substring(cursorCol));
            cursorCol--;
        } else if (cursorLine > 0) {
            String previous = lines.get(cursorLine - 1);
            lines.set(cursorLine - 1, previous + line);
            lines.remove(cursorLine);
            cursorLine--;
            cursorCol = previous.length();
        }
    }

    /**
     * Delete：删除光标后一个字符或合并下一行
     */
    private void deleteForward() {
        String line = lines.get(cursorLine);
        if (cursorCol < line.length()) {
            lines.set(cursorLine, line.substring(0, cursorCol) + line.substring(cursorCol + 1));
        } else if (cursorLine < lineCount() - 1) {
            lines.set(cursorLine, line + lines.get(cursorLine + 1));
            lines.remove(cursorLine + 1);
        }
    }

    /**
     * 移动光标
     *
     * @param dx 水平位移（字符）
     * @param dy 垂直位移（行）
     */
    private void moveCursor(int dx, int dy) {
        if (dy != 0) {
            cursorLine = Math.max(0, Math.min(lineCount() - 1, cursorLine + dy));
        }
        if (dx != 0) {
            cursorCol += dx;
            if (cursorCol < 0) {
                // 左移越界：跳到上一行行尾
                if (cursorLine > 0) {
                    cursorLine--;
                    cursorCol = lines.get(cursorLine).length();
                } else {
                    cursorCol = 0;
                }
            } else if (cursorCol > lines.get(cursorLine).length()) {
                // 右移越界：跳到下一行行首
                if (cursorLine < lineCount() - 1) {
                    cursorLine++;
                    cursorCol = 0;
                } else {
                    cursorCol = lines.get(cursorLine).length();
                }
            }
        }
    }

    /**
     * 限制光标坐标在合法范围内
     */
    private void clampCursor() {
        cursorLine = Math.max(0, Math.min(lineCount() - 1, cursorLine));
        cursorCol = Math.max(0, Math.min(lines.get(cursorLine).length(), cursorCol));
    }

    /**
     * 保证光标行在可见区域内（自动调整垂直滚动）
     */
    private void ensureCursorVisible() {
        if (cursorLine < scrollLine) {
            scrollLine = cursorLine;
        } else if (cursorLine >= scrollLine + visibleLineCount) {
            scrollLine = cursorLine - visibleLineCount + 1;
        }
    }

    /**
     * 按行数滚动编辑区
     *
     * @param delta 滚动行数（正值向上翻页）
     */
    private void scrollLines(int delta) {
        scrollLine = Math.max(0, Math.min(Math.max(0, lineCount() - visibleLineCount), scrollLine + delta));
    }

    // ==================== 鼠标输入 ====================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollLines((int) (scrollY * 3));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideEditArea(mouseX, mouseY)) {
            int rowHeight = this.font.lineHeight + LINE_EXTRA_SPACING;
            int clickedLine = scrollLine + (int) ((mouseY - editY) / rowHeight);
            cursorLine = Math.max(0, Math.min(lineCount() - 1, clickedLine));
            String line = lines.get(cursorLine);
            int relativeX = (int) (mouseX - editX);
            cursorCol = 0;
            for (int i = 1; i <= line.length(); i++) {
                if (this.font.width(line.substring(0, i)) <= relativeX) {
                    cursorCol = i;
                } else {
                    break;
                }
            }
            blinkTimer = 0;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 判断坐标是否在编辑区内
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 在编辑区内返回 true
     */
    private boolean isInsideEditArea(double mouseX, double mouseY) {
        return mouseX >= editX && mouseX <= editX + editWidth
            && mouseY >= editY && mouseY <= editY + editHeight;
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        float easeOut = animationProgress();

        renderBackgroundAndHeader(guiGraphics, easeOut);
        renderPanel(guiGraphics, easeOut);
        renderEditBoxFrame(guiGraphics);
        renderEditText(guiGraphics);
        renderBottomBar(guiGraphics, easeOut);
        renderCharBadge(guiGraphics, easeOut);

        // 渲染控件（保存/取消按钮）
        if (easeOut > 0.1f) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * 计算入场动画进度（0.0 - 1.0，easeOut 曲线）
     *
     * @return 动画进度
     */
    private float animationProgress() {
        float progress = Math.min(1.0f, openTime / OPEN_ANIMATION_DURATION);
        return 1.0f - (1.0f - progress) * (1.0f - progress);
    }

    /**
     * 渲染纯色背景与顶部装饰栏（带动画淡入）
     *
     * @param guiGraphics 绘图上下文
     * @param easeOut 动画进度
     */
    private void renderBackgroundAndHeader(GuiGraphics guiGraphics, float easeOut) {
        if (easeOut > 0) {
            int bgAlpha = (int) (0xFF * easeOut);
            guiGraphics.fill(0, 0, this.width, this.height, (bgAlpha << 24) | 0x151515);
        }

        int headerH = (int) (HEADER_HEIGHT * easeOut);
        if (headerH <= 0) {
            return;
        }
        guiGraphics.fill(0, 0, this.width, 2, BORDER_LIGHT);
        guiGraphics.fill(0, 2, this.width, 3, BORDER_COLOR);
        guiGraphics.fill(0, 3, this.width, headerH, 0xFF1C1C1C);

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

    /**
     * 渲染主面板背景与边框（带淡入）
     *
     * @param guiGraphics 绘图上下文
     * @param easeOut 动画进度
     */
    private void renderPanel(GuiGraphics guiGraphics, float easeOut) {
        if (easeOut <= 0.1f) {
            return;
        }
        float panelProgress = (easeOut - 0.1f) / 0.9f;
        int panelTop = HEADER_HEIGHT + 5;
        int panelBottom = this.height - BOTTOM_BAR_HEIGHT;
        int panelLeft = PADDING - 8;
        int panelRight = this.width - PADDING + 8;

        int panelAlpha = (int) (0xDD * panelProgress);
        int borderAlpha = (int) (0xFF * panelProgress);

        guiGraphics.fill(panelLeft - 1, panelTop - 1, panelRight + 1, panelBottom + 1,
                (borderAlpha << 24) | (BORDER_COLOR & 0xFFFFFF));
        guiGraphics.fill(panelLeft, panelTop, panelRight, panelBottom,
                (panelAlpha << 24) | (PANEL_COLOR & 0xFFFFFF));
    }

    /**
     * 渲染编辑框背景与边框（聚焦时橙色高亮）
     *
     * @param guiGraphics 绘图上下文
     */
    private void renderEditBoxFrame(GuiGraphics guiGraphics) {
        boolean focused = true;
        int borderCol = focused ? INPUT_BORDER_FOCUS : INPUT_BORDER;

        // 输入框背景
        guiGraphics.fill(editX, editY, editX + editWidth, editY + editHeight, INPUT_BG);

        // 边框（四周 1 像素）
        guiGraphics.fill(editX - 1, editY - 1, editX + editWidth + 1, editY, borderCol);
        guiGraphics.fill(editX - 1, editY + editHeight, editX + editWidth + 1, editY + editHeight + 1, borderCol);
        guiGraphics.fill(editX - 1, editY, editX, editY + editHeight, borderCol);
        guiGraphics.fill(editX + editWidth, editY, editX + editWidth + 1, editY + editHeight, borderCol);
    }

    /**
     * 渲染编辑文本：光标行高亮、可见行文本、光标与滚动条
     *
     * @param guiGraphics 绘图上下文
     */
    private void renderEditText(GuiGraphics guiGraphics) {
        int rowHeight = this.font.lineHeight + LINE_EXTRA_SPACING;
        int lastLine = Math.min(lineCount(), scrollLine + visibleLineCount);

        guiGraphics.enableScissor(editX, editY, editX + editWidth, editY + editHeight);
        for (int i = scrollLine; i < lastLine; i++) {
            int textY = editY + (i - scrollLine) * rowHeight;
            boolean isCursorLine = i == cursorLine;

            // 光标行高亮背景
            if (isCursorLine) {
                guiGraphics.fill(editX, textY - 1, editX + editWidth, textY + rowHeight - 1, CURSOR_LINE_BG);
            }
            String line = lines.get(i);
            if (!line.isEmpty()) {
                guiGraphics.drawString(this.font, line, editX + 4, textY, TEXT_PRIMARY);
            }

            // 光标（闪烁）
            if (isCursorLine && blinkTimer < CURSOR_BLINK_TICKS) {
                int cursorX = editX + 4 + this.font.width(line.substring(0, cursorCol));
                guiGraphics.fill(cursorX, textY - 1, cursorX + 1, textY + this.font.lineHeight, CURSOR_COLOR);
            }
        }
        guiGraphics.disableScissor();

        renderScrollbar(guiGraphics);
    }

    /**
     * 渲染编辑区右侧垂直滚动条（内容不超出时隐藏）
     *
     * @param guiGraphics 绘图上下文
     */
    private void renderScrollbar(GuiGraphics guiGraphics) {
        if (lineCount() <= visibleLineCount) {
            return;
        }
        int trackHeight = editHeight;
        int thumbHeight = Math.max(16, trackHeight * visibleLineCount / lineCount());
        int maxScroll = Math.max(1, lineCount() - visibleLineCount);
        int thumbY = editY + (int) ((float) scrollLine / maxScroll * (trackHeight - thumbHeight));
        int scrollbarX = editX + editWidth - 3;

        guiGraphics.fill(scrollbarX, editY, scrollbarX + 2, editY + trackHeight, SCROLLBAR_TRACK_COLOR);
        guiGraphics.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbHeight, SCROLLBAR_THUMB_COLOR);
    }

    /**
     * 渲染底部操作栏（带动画上移）
     *
     * @param guiGraphics 绘图上下文
     * @param easeOut 动画进度
     */
    private void renderBottomBar(GuiGraphics guiGraphics, float easeOut) {
        if (easeOut <= 0.3f) {
            return;
        }
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

    /**
     * 渲染字符数徽章（编辑框下方居中）
     *
     * @param guiGraphics 绘图上下文
     * @param easeOut 动画进度
     */
    private void renderCharBadge(GuiGraphics guiGraphics, float easeOut) {
        if (easeOut <= 0.4f) {
            return;
        }
        float badgeProgress = (easeOut - 0.4f) / 0.6f;
        int badgeAlpha = (int) (0xFF * badgeProgress);

        String countStr = String.valueOf(currentValue.length());
        int countWidth = this.font.width(countStr);
        String unitStr = Component.translatable("adaptive_nemesis.config.edit.char_count").getString();
        int unitWidth = this.font.width(unitStr);
        int totalWidth = countWidth + 2 + unitWidth;

        int badgeX = (this.width - totalWidth - 16) / 2;
        int badgeY = editY + editHeight + 6;
        int badgeW = totalWidth + 16;
        int badgeH = 14;

        int bgAlpha = (int) (BADGE_BG >>> 24) & 0xFF;
        int bgColor = (badgeAlpha * bgAlpha / 0xFF << 24) | (BADGE_BG & 0xFFFFFF);
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, bgColor);

        int bAlpha = (int) (0xFF * badgeProgress);
        int borderColor = (bAlpha << 24) | (BADGE_BORDER & 0xFFFFFF);
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, borderColor);
        guiGraphics.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, borderColor);
        guiGraphics.fill(badgeX, badgeY, badgeX + 1, badgeY + badgeH, borderColor);
        guiGraphics.fill(badgeX + badgeW - 1, badgeY, badgeX + badgeW, badgeY + badgeH, borderColor);

        int textColor = (bAlpha << 24) | (BADGE_TEXT & 0xFFFFFF);
        guiGraphics.drawString(this.font, countStr + " " + unitStr,
                badgeX + 8, badgeY + 3, textColor);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // 空实现 - 禁用模糊背景（与主界面保持一致）
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
