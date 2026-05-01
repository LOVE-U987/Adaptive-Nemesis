---
name: "neoforge-1.21.1-config-screen"
description: "提供NeoForge 1.21.1配置界面开发的完整指南和常见问题解决方案。Invoke when developing config GUI screens for NeoForge 1.21.1 mods, especially when encountering rendering issues, blur problems, or scroll layout challenges."
---

# NeoForge 1.21.1 配置界面开发指南

## 概述

本 Skill 记录 NeoForge 1.21.1 环境下开发 Minecraft 模组配置界面的完整经验，包括：
- 基础配置界面架构
- 常见问题及解决方案
- 渲染优化技巧
- 滚动列表实现
- 控件裁剪处理

## 基础架构

### 1. 主配置屏幕类

```java
public class ModConfigScreen extends Screen {
    private final Screen parent;
    private final List<AbstractWidget> configWidgets = new ArrayList<>();
    private int scrollOffset = 0;
    
    // 布局常量
    private static final int LIST_TOP = 40;
    private static final int LIST_BOTTOM = 40;
    private static final int PADDING = 20;
    private static final int ROW_HEIGHT = 25;
    private static final int CATEGORY_HEIGHT = 20;
    
    public ModConfigScreen(Screen parent) {
        super(Component.literal("配置标题"));
        this.parent = parent;
    }
}
```

### 2. 初始化控件

```java
@Override
protected void init() {
    this.configWidgets.clear();
    int currentY = LIST_TOP;
    int widgetX = this.width - PADDING - 110;
    
    // 添加配置行
    currentY = addConfigRow(currentY, "配置项名称", "悬停提示",
        createBooleanButton(true, value -> { /* 处理变化 */ }),
        widgetX);
    
    // 添加返回按钮
    this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
        .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
        .build());
}
```

### 3. 创建控件工厂方法

```java
private CycleButton<Boolean> createBooleanButton(boolean initialValue, Consumer<Boolean> onChange) {
    return CycleButton.booleanBuilder(
        Component.literal("是"),
        Component.literal("否")
    ).displayOnlyValue()
    .withInitialValue(initialValue)
    .create(0, 0, 100, 20, Component.empty(),
        (button, value) -> onChange.accept(value));
}

private EditBox createDoubleEditBox(double initialValue, double min, double max, Consumer<Double> onChange) {
    EditBox box = new EditBox(this.minecraft.font, 0, 0, 100, 20, Component.empty());
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
```

## 关键问题及解决方案

### 问题 1：背景模糊导致文字模糊

**现象**：配置界面打开后，所有文字都模糊不清，像被糊住一样。

**原因**：Minecraft 1.21 的 `Screen.renderBackground()` 会自动调用 `renderBlurredBackground()`，对游戏世界截图并应用高斯模糊着色器。这个后处理效果会影响后续渲染的所有内容。

**解决方案**：
```java
@Override
protected void renderBlurredBackground(float partialTick) {
    // 空实现 - 完全禁用模糊背景
}
```

**注意**：这是 Epic Fight 模组使用的相同技术，可以完全禁用背景模糊效果。

### 问题 2：按钮/控件超出边界不裁剪

**现象**：滚动时，按钮突然出现在列表边界外，而不是像文字那样被遮挡。

**原因**：调用 `super.render()` 会渲染所有按钮，但这个调用在裁剪区域之外，导致按钮不受裁剪影响。

**解决方案**：手动控制按钮渲染顺序

```java
@Override
public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // 1. 渲染背景
    guiGraphics.fill(0, 0, this.width, this.height, 0xFF2C2C2C);
    
    // 2. 启用裁剪区域
    guiGraphics.enableScissor(PADDING, LIST_TOP, this.width - PADDING, this.height - LIST_BOTTOM);
    
    // 3. 在裁剪区域内渲染文字
    renderConfigRows(guiGraphics);
    
    // 4. 在裁剪区域内手动渲染按钮（关键！）
    for (AbstractWidget widget : configWidgets) {
        if (widget.visible) {
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
    
    // 5. 禁用裁剪区域
    guiGraphics.disableScissor();
    
    // 6. 渲染不需要裁剪的控件（如完成按钮）
    for (var renderable : this.renderables) {
        if (renderable instanceof Button button) {
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
}
```

### 问题 3：控件可见性控制

**现象**：超出边界的控件仍然可以交互（点击）。

**解决方案**：根据位置动态设置 `visible` 属性

```java
private void updateWidget(int index, int y, int x) {
    AbstractWidget widget = configWidgets.get(index);
    widget.setX(x);
    widget.setY(y);
    // 只有当控件在可视区域内时才显示
    boolean visible = y + ROW_HEIGHT > LIST_TOP && y < this.height - LIST_BOTTOM;
    widget.visible = visible;
}
```

### 问题 4：鼠标滚轮事件处理

**现象**：滚轮滚动不流畅或无效。

**解决方案**：
```java
@Override
public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    int maxScroll = Math.max(0, totalContentHeight - (this.height - LIST_TOP - LIST_BOTTOM));
    scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 10));
    updateWidgetPositions();
    return true;
}
```

### 问题 5：悬停提示（Tooltip）

**实现方式**：
```java
widget.setTooltip(Tooltip.create(Component.literal("提示文字")));
```

**注意**：Minecraft 原生支持淡入淡出动画效果，无需额外实现。

## 完整渲染流程

```java
@Override
public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // 1. 纯色背景（禁用模糊）
    guiGraphics.fill(0, 0, this.width, this.height, 0xFF2C2C2C);
    
    // 2. 列表区域背景
    guiGraphics.fill(PADDING, LIST_TOP, this.width - PADDING, this.height - LIST_BOTTOM, 0xCC000000);
    
    // 3. 标题和提示（在裁剪区域外）
    guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    
    // 4. 启用裁剪
    guiGraphics.enableScissor(PADDING, LIST_TOP, this.width - PADDING, this.height - LIST_BOTTOM);
    
    // 5. 渲染文字和按钮（在裁剪区域内）
    renderConfigRows(guiGraphics);
    renderConfigWidgets(guiGraphics, mouseX, mouseY, partialTick);
    
    // 6. 禁用裁剪
    guiGraphics.disableScissor();
    
    // 7. 渲染固定控件（如完成按钮）
    renderFixedWidgets(guiGraphics, mouseX, mouseY, partialTick);
}
```

## 已知限制

1. **ContainerObjectSelectionList 问题**：NeoForge 1.21.1 中该类存在渲染兼容性问题，建议使用自定义布局
2. **renderBackground() 副作用**：会自动触发模糊效果，建议使用 `guiGraphics.fill()` 替代
3. **super.render() 行为**：会渲染所有已注册的控件，但不受裁剪区域影响

## 最佳实践

1. **始终禁用模糊背景**：重写 `renderBlurredBackground()` 为空实现
2. **手动控制渲染顺序**：不要依赖 `super.render()`，手动渲染所有控件
3. **使用裁剪区域**：`guiGraphics.enableScissor()` 和 `guiGraphics.disableScissor()`
4. **动态可见性**：根据控件位置动态设置 `visible` 属性
5. **纯色背景**：使用 `guiGraphics.fill()` 而不是 `renderBackground()`

## 参考实现

完整的配置界面实现可以参考：
- Epic Fight 模组的 `TPSSettingScreen.java`
- 本项目的 `AdaptiveNemesisConfigScreen.java`
