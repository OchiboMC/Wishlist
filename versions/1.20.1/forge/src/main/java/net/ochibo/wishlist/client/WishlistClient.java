package net.ochibo.wishlist.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.ResourceLocation;
import net.ochibo.wishlist.WishlistMod;
import net.ochibo.wishlist.client.integration.RecipeOverlayEvents;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.ochibo.wishlist.client.integration.HoveredItemAddShortcut;
import net.ochibo.wishlist.client.integration.emi.EmiRecipeOverlayFallback;
import net.ochibo.wishlist.client.ui.WishlistConfigScreen;
import net.ochibo.wishlist.client.ui.WishlistScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import net.ochibo.wishlist.internal.recipe.ClientRecipeExpansion;

public final class WishlistClient {
    private static final KeyMapping OPEN = new KeyMapping(
            "key.wishlist.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.wishlist");
    private static final KeyMapping HIGHLIGHT_CONTAINER = new KeyMapping(
            "key.wishlist.highlight_container", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.wishlist");
    private static final KeyMapping ADD_HOVERED = new KeyMapping(
            "key.wishlist.add_hovered", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_T, "key.categories.wishlist");

    public static final ResourceLocation WIDGETS = WishlistMod.id("textures/gui/widgets.png");

    public static net.minecraft.network.chat.Component highlightKeyName() {
        return HIGHLIGHT_CONTAINER.getTranslatedKeyMessage();
    }

    public static net.minecraft.network.chat.Component addHoveredKeyName() {
        return ADD_HOVERED.getTranslatedKeyMessage();
    }

    private WishlistClient() {}

    public static void register(IEventBus modBus) {
        MinecraftForge.registerConfigScreen(WishlistConfigScreen::new);
        modBus.addListener(WishlistClient::clientSetup);
        modBus.addListener(WishlistClient::registerKeys);
        modBus.addListener(WishlistHudOverlay::register);
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
        MinecraftForge.EVENT_BUS.register(new RecipeOverlayEvents());
        MinecraftForge.EVENT_BUS.register(new ContainerHighlightOverlay(HIGHLIGHT_CONTAINER));
        MinecraftForge.EVENT_BUS.register(new WishlistContainerButton());
        MinecraftForge.EVENT_BUS.register(new IndependentUiEvents());
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientRecipeExpansion.registerHandlers();
            if (WishlistMod.isLoaded("emi")) MinecraftForge.EVENT_BUS.register(new EmiRecipeOverlayFallback());
        });
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
        event.register(HIGHLIGHT_CONTAINER);
        event.register(ADD_HOVERED);
    }

    private static final class ClientEvents {
        @SubscribeEvent
        public void addHovered(ScreenEvent.KeyPressed.Pre event) {
            if (!ADD_HOVERED.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) return;
            var screen = event.getScreen();
            var hovered = RecipeViewerRouter.hoveredViewerItem(screen);
            if (hovered.isEmpty() && screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container) {
                var slot = container.getSlotUnderMouse();
                if (slot != null && slot.isActive()) hovered = slot.getItem();
            }
            if (HoveredItemAddShortcut.handle(screen, hovered,
                    net.minecraft.client.gui.screens.Screen.hasShiftDown())) event.setCanceled(true);
        }

        @SubscribeEvent
        public void itemCrafted(PlayerEvent.ItemCraftedEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || event.getEntity() != mc.player || !event.getEntity().level().isClientSide()) return;
            ShiftCraftTracker.crafted(event.getCrafting(), event.getCrafting().getCount());
        }

        @SubscribeEvent
        public void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
            if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
                ShiftCraftTracker.mousePressed(screen, screen.getSlotUnderMouse(), event.getButton());
            }
        }

        @SubscribeEvent
        public void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            ShiftCraftTracker.tick();
            while (OPEN.consumeClick()) mc.setScreen(new WishlistScreen(mc.screen));
        }

        @SubscribeEvent
        public void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientWorkspaceController.get().invalidate();
            ShiftCraftTracker.clear();
            RecipeViewerRouter.clearSelectionReturnScreen();
        }

        @SubscribeEvent
        public void tagsUpdated(TagsUpdatedEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            mc.tell(() -> ClientWorkspaceController.get().refreshResources());
        }
    }
}
