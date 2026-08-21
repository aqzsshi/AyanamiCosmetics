package ru.ayanami.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Adds a cosmetics button next to the player in the survival inventory.
 */
@SideOnly(Side.CLIENT)
public class InventoryCosmeticsButton {

    public static final int BUTTON_ID = 87421;

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new InventoryCosmeticsButton());
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiInventory)) {
            return;
        }
        GuiInventory inventory = (GuiInventory) event.getGui();
        int left = inventory.getGuiLeft();
        int top = inventory.getGuiTop();

        // Near the player model (left side of inventory panel).
        int x = left + 6;
        int y = top + 8;
        event.getButtonList().add(new CosmeticsInventoryButton(BUTTON_ID, x, y));
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getButton() != null && event.getButton().id == BUTTON_ID) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new GuiCosmeticsMenu(event.getGui()));
        }
    }

    @SideOnly(Side.CLIENT)
    public static class CosmeticsInventoryButton extends GuiButton {

        public CosmeticsInventoryButton(int id, int x, int y) {
            super(id, x, y, 20, 20, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;

            int bg = this.hovered ? 0xFF3A4F78 : 0xFF2A2A32;
            int border = this.hovered ? 0xFF9BBCFF : 0xFF6F98FF;
            drawRect(this.x, this.y, this.x + this.width, this.y + this.height, border);
            drawRect(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, bg);

            // Simple "cosmetics" glyph (star-like).
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            String icon = "*";
            int tw = mc.fontRenderer.getStringWidth(icon);
            mc.fontRenderer.drawString(icon, this.x + (this.width - tw) / 2, this.y + 6, 0xFFE8F0FF, false);

            if (this.hovered) {
                // Tooltip drawn by parent is awkward here; small label under button area via screen later.
            }
        }
    }
}
