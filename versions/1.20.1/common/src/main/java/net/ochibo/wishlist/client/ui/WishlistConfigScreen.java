package net.ochibo.wishlist.client.ui;

import net.ochibo.wishlist.client.config.WishlistClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.ochibo.wishlist.client.HudOverlayStyle;
import net.ochibo.wishlist.client.WishlistClient;

public final class WishlistConfigScreen extends Screen {
    private final Screen parent;

    public WishlistConfigScreen(Screen parent) {
        super(Component.translatable("screen.wishlist.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int buttonWidth = Math.min(240, Math.max(160, width - 40));
        int x = center - (buttonWidth + 20) / 2;
        int y = Math.max(42, height / 2 - 78);

        addRenderableWidget(new MaxItemsSlider(x, y, buttonWidth, 20));
        addReset(x + buttonWidth + 4, y, () -> WishlistClientConfig.setHudMaxItemsValue("5"),
                () -> "5".equals(WishlistClientConfig.hudMaxItemsValue()));

        y += 24;
        addRenderableWidget(new ScaleSlider(x, y, buttonWidth, 20));
        addReset(x + buttonWidth + 4, y,
                () -> WishlistClientConfig.setHudScalePercent(HudOverlayStyle.DEFAULT_SCALE_PERCENT),
                () -> WishlistClientConfig.hudScalePercent() == HudOverlayStyle.DEFAULT_SCALE_PERCENT);

        y += 24;
        addRenderableWidget(new BackgroundOpacitySlider(x, y, buttonWidth, 20));
        addReset(x + buttonWidth + 4, y, () -> WishlistClientConfig.setHudBackgroundOpacityPercent(
                HudOverlayStyle.DEFAULT_BACKGROUND_OPACITY_PERCENT),
                () -> WishlistClientConfig.hudBackgroundOpacityPercent()
                        == HudOverlayStyle.DEFAULT_BACKGROUND_OPACITY_PERCENT);

        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.config.position"), button ->
                Minecraft.getInstance().setScreen(new HudPositionScreen(this)))
                .bounds(x, y, buttonWidth, 20).build());
        addReset(x + buttonWidth + 4, y, () -> WishlistClientConfig.setHudPosition(0, 0),
                () -> WishlistClientConfig.hudPositionX() == 0 && WishlistClientConfig.hudPositionY() == 0);

        y += 24;
        addRenderableWidget(Button.builder(highlightLabel(), button -> {
            WishlistClientConfig.setHighlightAnimated(!WishlistClientConfig.highlightAnimated());
            button.setMessage(highlightLabel());
        }).bounds(x, y, buttonWidth, 20).build());
        addReset(x + buttonWidth + 4, y, () -> WishlistClientConfig.setHighlightAnimated(true),
                WishlistClientConfig::highlightAnimated);

        y += 30;
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.done"), button -> onClose())
                .bounds(center - 60, y, 120, 20).build());
    }

    private void addReset(int x, int y, Runnable reset, java.util.function.BooleanSupplier isDefault) {
        ResetButton button = new ResetButton(x, y + 2, () -> {
            reset.run();
            rebuildWidgets();
        }, isDefault);
        addRenderableWidget(button);
    }

    private Component highlightLabel() {
        return Component.translatable("screen.wishlist.config.highlight_animated",
                Component.translatable(WishlistClientConfig.highlightAnimated()
                        ? "screen.wishlist.on" : "screen.wishlist.off"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private static final class MaxItemsSlider extends AbstractSliderButton {
        private MaxItemsSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), initialValue());
            updateMessage();
        }

        private static double initialValue() {
            String current = WishlistClientConfig.hudMaxItemsValue();
            if ("all".equals(current)) return 1;
            try { return (Math.max(1, Math.min(10, Integer.parseInt(current))) - 1) / 10.0D; }
            catch (NumberFormatException ignored) { return 0.4D; }
        }

        private int index() { return Math.max(0, Math.min(10, (int) Math.round(value * 10))); }
        @Override protected void updateMessage() {
            setMessage(Component.translatable("screen.wishlist.config.max_items", index() == 10
                    ? Component.translatable("screen.wishlist.config.all")
                    : Component.literal(Integer.toString(index() + 1))));
        }
        @Override protected void applyValue() {
            WishlistClientConfig.setHudMaxItemsValue(index() == 10 ? "all" : Integer.toString(index() + 1));
        }
    }

    private static final class ResetButton extends AbstractButton {
        private final Runnable reset;
        private final java.util.function.BooleanSupplier isDefault;
        private ResetButton(int x, int y, Runnable reset,
                            java.util.function.BooleanSupplier isDefault) {
            super(x, y, 16, 16, Component.translatable("screen.wishlist.config.reset"));
            this.reset = reset;
            this.isDefault = isDefault;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(getMessage()));
        }
        @Override public void onPress() { reset.run(); }
        @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            active = !isDefault.getAsBoolean();
            graphics.blit(WishlistClient.WIDGETS, getX(), getY(), 16, 16,
                    128, !active ? 32 : isHoveredOrFocused() ? 16 : 0, 16, 16, 256, 256);
        }
    }

    private static final class ScaleSlider extends AbstractSliderButton {
        private ScaleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(),
                    (WishlistClientConfig.hudScalePercent() - 1) / 199.0D);
            updateMessage();
        }

        private int scalePercent() {
            return 1 + (int) Math.round(value * 199.0D);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.wishlist.config.scale", scalePercent()));
        }

        @Override
        protected void applyValue() {
            WishlistClientConfig.setHudScalePercent(scalePercent());
        }
    }

    private static final class BackgroundOpacitySlider extends AbstractSliderButton {
        private BackgroundOpacitySlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(),
                    WishlistClientConfig.hudBackgroundOpacityPercent() / 100.0D);
            updateMessage();
        }

        private int opacityPercent() {
            return (int) Math.round(value * 100.0D);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.wishlist.config.background_opacity", opacityPercent()));
        }

        @Override
        protected void applyValue() {
            WishlistClientConfig.setHudBackgroundOpacityPercent(opacityPercent());
        }
    }
}
