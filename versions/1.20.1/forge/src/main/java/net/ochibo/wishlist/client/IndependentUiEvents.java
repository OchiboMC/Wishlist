package net.ochibo.wishlist.client;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.ochibo.wishlist.client.ui.independent.IndependentUIRenderer;

public final class IndependentUiEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) IndependentUIRenderer.render(event.renderTickTime);
    }
}
