package net.ochibo.wishlist.client.ui;

import net.ochibo.wishlist.client.config.WishlistClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
        int x = center - buttonWidth / 2;
        int y = Math.max(42, height / 2 - 86);

        addRenderableWidget(Button.builder(hudEnabledLabel(), button -> {
            WishlistClientConfig.setHudEnabled(!WishlistClientConfig.hudEnabled());
            button.setMessage(hudEnabledLabel());
        }).bounds(x, y, buttonWidth, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(missingOnlyLabel(), button -> {
            WishlistClientConfig.setMissingOnly(!WishlistClientConfig.missingOnly());
            button.setMessage(missingOnlyLabel());
        }).bounds(x, y, buttonWidth, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(maxItemsLabel(), button -> {
            WishlistClientConfig.setHudMaxItemsValue(nextMaxItemsValue(WishlistClientConfig.hudMaxItemsValue()));
            button.setMessage(maxItemsLabel());
        }).bounds(x, y, buttonWidth, 20).build());

        y += 24;
        addRenderableWidget(new ScaleSlider(x, y, buttonWidth, 20));

        y += 24;
        addRenderableWidget(new BackgroundOpacitySlider(x, y, buttonWidth, 20));

        y += 32;
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.done"), button -> onClose())
                .bounds(center - 60, y, 120, 20).build());
    }

    private Component hudEnabledLabel() {
        return Component.translatable("screen.wishlist.config.hud",
                Component.translatable(WishlistClientConfig.hudEnabled()
                        ? "options.on"
                        : "options.off"));
    }

    private Component missingOnlyLabel() {
        return Component.translatable("screen.wishlist.config.missing_only",
                Component.translatable(WishlistClientConfig.missingOnly()
                        ? "options.on"
                        : "options.off"));
    }

    private Component maxItemsLabel() {
        String value = WishlistClientConfig.hudMaxItemsValue();
        Component display = "all".equals(value)
                ? Component.translatable("screen.wishlist.config.all")
                : Component.literal(value);
        return Component.translatable("screen.wishlist.config.max_items", display);
    }

    private static String nextMaxItemsValue(String current) {
        if ("all".equals(current)) return "1";
        try {
            int value = Integer.parseInt(current);
            return value >= 10 ? "all" : Integer.toString(Math.max(1, value + 1));
        } catch (NumberFormatException ignored) {
            return "5";
        }
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
