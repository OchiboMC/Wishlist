package net.ochibo.wishlist.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.ochibo.wishlist.WishlistMod;
import net.ochibo.wishlist.client.integration.RecipeOverlayEvents;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.ochibo.wishlist.client.integration.HoveredItemAddShortcut;
import net.ochibo.wishlist.client.mixin.ContainerScreenAccessor;
import net.ochibo.wishlist.client.ui.WishlistScreen;
import net.ochibo.wishlist.internal.recipe.ClientRecipeExpansion;
import org.lwjgl.glfw.GLFW;

public final class WishlistClient {
    public static final ResourceLocation WIDGETS = WishlistMod.id("textures/gui/widgets.png");

    public static net.minecraft.network.chat.Component highlightKeyName() {
        return HIGHLIGHT_CONTAINER.getTranslatedKeyMessage();
    }

    public static net.minecraft.network.chat.Component addHoveredKeyName() {
        return ADD_HOVERED.getTranslatedKeyMessage();
    }

    private static final KeyMapping OPEN = new KeyMapping(
            "key.wishlist.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.wishlist");
    private static final KeyMapping HIGHLIGHT_CONTAINER = new KeyMapping(
            "key.wishlist.highlight_container", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.wishlist");
    private static final KeyMapping ADD_HOVERED = new KeyMapping(
            "key.wishlist.add_hovered", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_T, "key.categories.wishlist");
    private static final ContainerHighlightOverlay HIGHLIGHT = new ContainerHighlightOverlay(HIGHLIGHT_CONTAINER);

    private WishlistClient() {}

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN);
        KeyBindingHelper.registerKeyBinding(HIGHLIGHT_CONTAINER);
        KeyBindingHelper.registerKeyBinding(ADD_HOVERED);
        ClientRecipeExpansion.registerHandlers();

        HudRenderCallback.EVENT.register((graphics, partialTick) -> WishlistHudOverlay.render(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(WishlistClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientWorkspaceController.get().invalidate();
            ShiftCraftTracker.clear();
            RecipeViewerRouter.clearSelectionReturnScreen();
            RecipeViewerRouter.clearIconReturnScreen();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> ClientWorkspaceController.get().refreshResources()));

        HIGHLIGHT.register();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenEvents.beforeRender(screen).register((current, graphics, mouseX, mouseY, delta) ->
                    RecipeOverlayEvents.renderPre(current));
            ScreenEvents.afterRender(screen).register((current, graphics, mouseX, mouseY, delta) -> {
                RecipeOverlayEvents.render(current, graphics, mouseX, mouseY);
                WishlistContainerButton.render(current, graphics, mouseX, mouseY);
                if (WishlistMod.isLoaded("emi")) renderEmiFallback(current, graphics, mouseX, mouseY, delta);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((current, mouseX, mouseY, button) -> {
                if (WishlistContainerButton.mousePressed(current, mouseX, mouseY, button)) return false;
                if (RecipeOverlayEvents.mousePressed(current, mouseX, mouseY, button)) return false;
                if (WishlistMod.isLoaded("emi") && clickEmiFallback(current, mouseX, mouseY, button)) return false;
                if (current instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
                    ShiftCraftTracker.mousePressed(current,
                            ((ContainerScreenAccessor) current).wishlist$getHoveredSlot(), button);
                }
                return true;
            });
            ScreenKeyboardEvents.allowKeyPress(screen).register((current, keyCode, scanCode, modifiers) -> {
                if (HIGHLIGHT.keyPressed(current, keyCode, scanCode)) return false;
                return !addHovered(current, keyCode, scanCode);
            });
            ScreenKeyboardEvents.allowKeyRelease(screen).register((current, keyCode, scanCode, modifiers) ->
                    !HIGHLIGHT.keyReleased(keyCode, scanCode));
        });
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        ShiftCraftTracker.tick();
        while (OPEN.consumeClick()) mc.setScreen(new WishlistScreen(mc.screen));
    }

    private static boolean addHovered(net.minecraft.client.gui.screens.Screen screen, int keyCode, int scanCode) {
        if (!ADD_HOVERED.matches(keyCode, scanCode)) return false;
        var hovered = RecipeViewerRouter.hoveredViewerItem(screen);
        if (hovered.isEmpty() && screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
            var slot = ((ContainerScreenAccessor) screen).wishlist$getHoveredSlot();
            if (slot != null && slot.isActive()) hovered = slot.getItem();
        }
        return HoveredItemAddShortcut.handle(screen, hovered,
                net.minecraft.client.gui.screens.Screen.hasShiftDown());
    }

    public static ContainerHighlightOverlay highlight() {
        return HIGHLIGHT;
    }

    private static void renderEmiFallback(net.minecraft.client.gui.screens.Screen screen,
                                          net.minecraft.client.gui.GuiGraphics graphics,
                                          int mouseX, int mouseY, float delta) {
        net.ochibo.wishlist.client.integration.emi.EmiRecipeOverlayFallback.render(
                screen, graphics, mouseX, mouseY, delta);
    }

    private static boolean clickEmiFallback(net.minecraft.client.gui.screens.Screen screen,
                                            double mouseX, double mouseY, int button) {
        return net.ochibo.wishlist.client.integration.emi.EmiRecipeOverlayFallback.mousePressed(
                screen, mouseX, mouseY, button);
    }
}
