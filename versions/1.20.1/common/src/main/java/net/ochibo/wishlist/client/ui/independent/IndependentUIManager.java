package net.ochibo.wishlist.client.ui.independent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Client-thread registry. Screen opening/closing never clears or recreates these instances. */
public final class IndependentUIManager {
    private static final IndependentUIManager INSTANCE = new IndependentUIManager();
    private final List<IndependentUI> layers = new ArrayList<>();

    IndependentUIManager() {}

    public static IndependentUIManager getInstance() { return INSTANCE; }

    /** Register once by instance identity. Later registrations draw above earlier ones. */
    public void register(IndependentUI ui) {
        Objects.requireNonNull(ui, "ui");
        if (layers.stream().noneMatch(existing -> existing == ui)) layers.add(ui);
    }

    /** The owner controls removal, including cleanup when its own world/session lifetime ends. */
    public void unregister(IndependentUI ui) {
        layers.removeIf(existing -> existing == ui);
    }

    public boolean hasVisibleUi() { return layers.stream().anyMatch(IndependentUI::isVisible); }

    void drawVisible(Consumer<IndependentUI> draw) {
        // Use a frame snapshot so callbacks may register/remove/hide UI safely for the next frame.
        List<IndependentUI> frame = layers.stream().filter(IndependentUI::isVisible).toList();
        frame.forEach(draw);
    }
}
