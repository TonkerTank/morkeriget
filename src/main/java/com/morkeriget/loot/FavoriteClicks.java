package com.morkeriget.loot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Alt+click on loot in an inventory screen marks it as a favorite (see Favorites), and favorites get a gold star in the
// corner of their slot. The creative inventory is left out, since its slots aren't the ones the server knows. Client only
public final class FavoriteClicks {
    private static final int STAR_COLOR = 0xFFD700;
    // The click was used for a favorite, so letting go of the button mustn't do anything either
    private static boolean swallowRelease;

    private FavoriteClicks() {
    }

    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !Screen.hasAltDown() || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen instanceof CreativeModeInventoryScreen) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || Loot.getRarity(slot.getItem()) == null || !screen.getMenu().getCarried().isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new FavoritePayload(screen.getMenu().containerId, slot.index));
        swallowRelease = true;
        event.setCanceled(true);
    }

    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (swallowRelease && event.getButton() == 0) {
            swallowRelease = false;
            event.setCanceled(true);
        }
    }

    // Drawn over the item, where the count is in the other corner
    public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        if (event.getContainerScreen() instanceof CreativeModeInventoryScreen) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        for (Slot slot : event.getContainerScreen().getMenu().slots) {
            if (slot.isActive() && Favorites.is(slot.getItem())) {
                graphics.drawString(event.getContainerScreen().getMinecraft().font, "★", slot.x + 10, slot.y - 1, STAR_COLOR, true);
            }
        }
        graphics.pose().popPose();
    }
}
