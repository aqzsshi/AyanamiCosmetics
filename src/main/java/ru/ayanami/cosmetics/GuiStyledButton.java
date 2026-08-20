package ru.ayanami.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiStyledButton extends GuiButton {

    private final int accentColor;
    private final boolean primary;

    public GuiStyledButton(int id, int x, int y, int width, int height, String text, boolean primary) {
        super(id, x, y, width, height, text);
        this.primary = primary;
        this.accentColor = primary ? 0x5EB7D4 : 0x3A4654;
    }

    public GuiStyledButton(int id, int x, int y, int width, int height, String text) {
        this(id, x, y, width, height, text, false);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
        FontRenderer fr = mc.fontRenderer;

        int bgTop;
        int bgBottom;
        int border;

        if (!this.enabled) {
            bgTop = 0xFF2A3038;
            bgBottom = 0xFF232830;
            border = 0xFF3E4752;
        } else if (this.hovered) {
            if (this.primary) {
                bgTop = 0xFF7AD0EA;
                bgBottom = 0xFF4AA8C8;
                border = 0xFFB8ECFA;
            } else {
                bgTop = 0xFF4A5666;
                bgBottom = 0xFF36404C;
                border = 0xFF7EC8E3;
            }
        } else {
            if (this.primary) {
                bgTop = 0xFF5EB7D4;
                bgBottom = 0xFF3A91B0;
                border = 0xFF8FD6EC;
            } else {
                bgTop = 0xFF3A4654;
                bgBottom = 0xFF2C343E;
                border = 0xFF556170;
            }
        }

        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, border);
        drawGradientRect(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, bgTop, bgBottom);

        // Soft top highlight
        drawRect(this.x + 2, this.y + 2, this.x + this.width - 2, this.y + 3, 0x33FFFFFF);

        int textColor = this.enabled ? 0xFFF2F7FA : 0xFF8A939C;
        int textWidth = fr.getStringWidth(this.displayString);
        fr.drawString(this.displayString, this.x + (this.width - textWidth) / 2, this.y + (this.height - 8) / 2, textColor, false);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
