package ru.ayanami.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Left-protruding wardrobe tab next to the player in GuiInventory (Essential-like plate).
 */
@SideOnly(Side.CLIENT)
public class InventoryCosmeticsButton {

    public static final int BUTTON_ID = 87421;
    private static final ResourceLocation TAB_TEX =
            new ResourceLocation(TweakOS.MODID, "textures/gui/inventory_tab.png");

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

        // Plate sticks out to the LEFT of the inventory / player area.
        int x = left - 24;
        int y = top + 6;
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
            super(id, x, y, 24, 24, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            this.hovered = mouseX >= this.x && mouseY >= this.y
                    && mouseX < this.x + this.width && mouseY < this.y + this.height;

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            mc.getTextureManager().bindTexture(TAB_TEX);
            // Stretch 24x24 texture
            drawModalRectWithCustomSizedTexture(this.x, this.y, 0, 0, 24, 24, 24, 24);

            if (this.hovered) {
                drawRect(this.x, this.y, this.x + this.width, this.y + this.height, 0x33FFFFFF);
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
