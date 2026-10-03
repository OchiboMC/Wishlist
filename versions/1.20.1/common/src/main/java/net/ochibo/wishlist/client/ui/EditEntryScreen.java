package net.ochibo.wishlist.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.ochibo.wishlist.client.ClientWorkspaceController;
import net.ochibo.wishlist.client.WishlistClient;
import net.ochibo.wishlist.core.model.Wishlist;
import net.ochibo.wishlist.core.model.WishlistEntry;
import net.ochibo.wishlist.core.tree.RecipeNode;

import java.util.UUID;

/** Edits a root target or a material branch with the same two count controls. */
public final class EditEntryScreen extends Screen {
    private final Screen parent;
    private final UUID wishlistId;
    private final UUID targetId;
    private final boolean branch;
    private WishlistEntry entry;
    private RecipeNode node;
    private long branchRequired;
    private CountControl desired;
    private CountControl crafted;

    public EditEntryScreen(Screen parent, UUID wishlistId, UUID entryId) {
        this(parent, wishlistId, entryId, false);
    }

    public static EditEntryScreen forNode(Screen parent, UUID wishlistId, UUID nodeId) {
        return new EditEntryScreen(parent, wishlistId, nodeId, true);
    }

    private EditEntryScreen(Screen parent, UUID wishlistId, UUID targetId, boolean branch) {
        super(Component.translatable("screen.wishlist.edit_entry_title"));
        this.parent = parent;
        this.wishlistId = wishlistId;
        this.targetId = targetId;
        this.branch = branch;
    }

    @Override
    protected void init() {
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        Wishlist wishlist = controller.workspace().find(wishlistId).orElse(null);
        if (wishlist == null) { onClose(); return; }
        if (branch) {
            node = controller.findNode(wishlistId, targetId).orElse(null);
            if (node == null) { onClose(); return; }
            var quantity = controller.materialCalculation().nodeQuantities().get(targetId);
            branchRequired = quantity == null ? 0 : quantity.required();
            crafted = new CountControl("screen.wishlist.crafted_count", node.craftedCount());
        } else {
            entry = wishlist.entries().stream().filter(e -> e.id().equals(targetId)).findFirst().orElse(null);
            if (entry == null) { onClose(); return; }
            desired = new CountControl("screen.wishlist.desired_count", entry.requestedCount());
            crafted = new CountControl("screen.wishlist.crafted_count", entry.craftedCount());
        }
        buildControls();
    }

    private void buildControls() {
        if (desired != null) {
            desired.value = desired.current();
        }
        if (crafted != null) {
            crafted.value = crafted.current();
        }
        clearWidgets();
        int cx = width / 2;
        if (!branch) desired.add(cx - 96, height / 2 - 28, 0, true);
        crafted.add(cx - 96, height / 2 + 10, branch ? branchRequired : desired.current(), branch || !entry.completed());
        int y = height / 2 + 50;
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.save"), b -> save())
                .bounds(cx - 104, y, 66, 20).build());
        if (!branch) addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.delete"), b ->
                        Minecraft.getInstance().setScreen(new DeleteEntryScreen(this, wishlistId, targetId,
                                UiText.itemName(entry.outputItemId()))))
                .bounds(cx - 33, y, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.wishlist.back"), b -> onClose())
                .bounds(cx + 38, y, 66, 20).build());
    }

    private void save() {
        long requested = branch ? branchRequired : desired.current();
        long completed = Math.min(crafted.current(), requested);
        ClientWorkspaceController controller = ClientWorkspaceController.get();
        if (branch) controller.setNodeCraftedCount(wishlistId, targetId, completed);
        else controller.setEntryProgress(wishlistId, targetId, requested, completed);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 76, 0xFFFFFF);
        if (entry != null) graphics.drawCenteredString(font, UiText.itemName(entry.outputItemId()),
                width / 2, height / 2 - 59, 0xBFBFBF);
        if (node != null) graphics.drawCenteredString(font, UiText.itemName(node.selectedCandidateItemId() == null
                ? node.ingredient().candidates().get(0) : node.selectedCandidateItemId()),
                width / 2, height / 2 - 59, 0xBFBFBF);
        graphics.drawString(font, branch
                        ? Component.translatable("screen.wishlist.required_count_value", branchRequired)
                        : Component.translatable("screen.wishlist.desired_count"),
                width / 2 - 96, height / 2 - 41, 0xE0E0E0, false);
        graphics.drawString(font, Component.translatable("screen.wishlist.crafted_count"), width / 2 - 96,
                height / 2 - 3, 0xE0E0E0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }

    private final class CountControl {
        private final String label;
        private long value;
        private boolean direct;
        private EditBox box;
        private CountSlider slider;

        private CountControl(String label, long initial) {
            this.label = label;
            this.value = initial;
            this.direct = initial > 64;
        }

        private long current() {
            if (!direct || box == null) return value;
            if (box.getValue().isEmpty()) return 0;
            try { return Math.max(0, Long.parseLong(box.getValue())); }
            catch (NumberFormatException ignored) { return value; }
        }

        private void onChanged() {
            if (this != desired || crafted == null) return;
            if (crafted.slider != null && crafted.slider.active)
                crafted.slider.setMaximum(Math.min(64, current()));
            if (crafted.direct && crafted.box != null && crafted.current() > current())
                crafted.box.setValue(Long.toString(current()));
        }

        private void add(int x, int y, long requested, boolean enabled) {
            long max = label.equals("screen.wishlist.desired_count") ? 64 : Math.min(64, requested);
            ModeButton toggle = new ModeButton(x, y + 2, direct ? 112 : 96, () -> {
                value = current();
                if (direct) value = Math.min(value, max);
                else box = null;
                direct = !direct;
                buildControls();
            });
            toggle.active = enabled;
            toggle.setTooltip(Tooltip.create(Component.translatable(direct
                    ? "screen.wishlist.slider_input" : "screen.wishlist.direct_input")));
            addRenderableWidget(toggle);
            if (direct) {
                slider = null;
                box = new EditBox(font, x + 22, y, 168, 20, Component.translatable(label));
                box.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
                box.setMaxLength(18);
                box.setValue(Long.toString(Math.min(value, label.equals("screen.wishlist.desired_count") ? Long.MAX_VALUE : requested)));
                box.setEditable(enabled);
                box.setResponder(text -> {
                    if (this == desired) onChanged();
                    else if (enabled && current() > (branch ? branchRequired : desired.current()))
                        box.setValue(Long.toString(branch ? branchRequired : desired.current()));
                });
                addRenderableWidget(box);
            } else {
                box = null;
                if (enabled) value = Math.min(value, max);
                slider = new CountSlider(x + 22, y, 168, max, Math.min(value, max), label, this);
                slider.active = enabled;
                if (!enabled) slider.setMessage(Component.translatable(label).append(": " + value));
                addRenderableWidget(slider);
            }
        }
    }

    private static final class CountSlider extends AbstractSliderButton {
        private long max;
        private final String label;
        private final CountControl control;

        private CountSlider(int x, int y, int width, long max, long initial, String label, CountControl control) {
            super(x, y, width, 20, Component.empty(), max == 0 ? 0 : (double) initial / max);
            this.max = max;
            this.label = label;
            this.control = control;
            updateMessage();
        }

        private long count() { return Math.round(value * max); }
        private void setMaximum(long maximum) {
            long current = count();
            max = maximum;
            control.value = Math.min(current, max);
            value = max == 0 ? 0 : (double) control.value / max;
            updateMessage();
        }
        @Override protected void updateMessage() { setMessage(Component.translatable(label).append(": " + count())); }
        @Override protected void applyValue() {
            control.value = count();
            control.onChanged();
        }
    }

    private static final class ModeButton extends AbstractButton {
        private final int u;
        private final Runnable action;

        private ModeButton(int x, int y, int u, Runnable action) {
            super(x, y, 16, 16, Component.empty());
            this.u = u;
            this.action = action;
        }

        @Override public void onPress() { if (active) action.run(); }
        @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blit(WishlistClient.WIDGETS, getX(), getY(), 16, 16, u,
                    !active ? 32 : isHoveredOrFocused() ? 16 : 0, 16, 16, 256, 256);
        }
    }
}
