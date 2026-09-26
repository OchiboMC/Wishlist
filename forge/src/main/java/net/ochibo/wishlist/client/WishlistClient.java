package net.ochibo.wishlist.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.ResourceLocation;
import net.ochibo.wishlist.WishlistMod;
import net.ochibo.wishlist.client.integration.RecipeOverlayEvents;
import net.ochibo.wishlist.client.integration.RecipeViewerRouter;
import net.ochibo.wishlist.client.integration.emi.EmiRecipeOverlayFallback;
import net.ochibo.wishlist.client.ui.WishlistConfigScreen;
import net.ochibo.wishlist.client.ui.WishlistScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
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

    public static final ResourceLocation WIDGETS = WishlistMod.id("textures/gui/widgets.png");

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
    }

    private static final class ClientEvents {
        @SubscribeEvent
        public void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            while (OPEN.consumeClick()) mc.setScreen(new WishlistScreen(mc.screen));
        }

        @SubscribeEvent
        public void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientWorkspaceController.get().invalidate();
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
