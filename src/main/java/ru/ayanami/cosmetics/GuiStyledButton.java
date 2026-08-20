package ru.ayanami.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiStyledButton extends GuiButton {

    public enum Style {
        PRIMARY,
        SECONDARY,
        DANGER,
        CHIP,
        CHIP_ACTIVE
    }

    private Style style;

    public GuiStyledButton(int id, int x, int y, int width, int height, String text, Style style) {
        super(id, x, y, width, height, text);
        this.style = style;
    }

    public GuiStyledButton(int id, int x, int y, int width, int height, String text, boolean primary) {
        this(id, x, y, width, height, text, primary ? Style.PRIMARY : Style.SECONDARY);
    }

    public void setStyle(Style style) {
        this.style = style;
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
        int textColor = 0xFFF2F4F8;

        if (!this.enabled) {
            bgTop = 0xFF2A2A2E;
            bgBottom = 0xFF222226;
            border = 0xFF3A3A40;
            textColor = 0xFF7A7A82;
        } else {
            switch (this.style) {
                case PRIMARY:
                    if (this.hovered) {
                        bgTop = 0xFF5B8CFF;
                        bgBottom = 0xFF3D6FE0;
                        border = 0xFF9BBCFF;
                    } else {
                        bgTop = 0xFF4A7CFF;
                        bgBottom = 0xFF355FD4;
                        border = 0xFF6F98FF;
                    }
                    break;
                case DANGER:
                    if (this.hovered) {
                        bgTop = 0xFFE25B5B;
                        bgBottom = 0xFFC04343;
                        border = 0xFFFF9A9A;
                    } else {
                        bgTop = 0xFFD14A4A;
                        bgBottom = 0xFFB03838;
                        border = 0xFFE87A7A;
                    }
                    break;
                case CHIP_ACTIVE:
                    bgTop = 0xFF3A4F78;
                    bgBottom = 0xFF2E3F62;
                    border = 0xFF7AA2FF;
                    textColor = 0xFFEAF1FF;
                    break;
                case CHIP:
                    if (this.hovered) {
                        bgTop = 0xFF33333A;
                        bgBottom = 0xFF2A2A30;
                        border = 0xFF5A5A66;
                    } else {
                        bgTop = 0xFF2A2A30;
                        bgBottom = 0xFF232328;
                        border = 0xFF3E3E46;
                    }
                    textColor = 0xFFC8CAD2;
                    break;
                case SECONDARY:
                default:
                    if (this.hovered) {
                        bgTop = 0xFF3A3A42;
                        bgBottom = 0xFF2F2F36;
                        border = 0xFF6A6A76;
                    } else {
                        bgTop = 0xFF2F2F36;
                        bgBottom = 0xFF26262C;
                        border = 0xFF484850;
                    }
                    break;
            }
        }

        // Soft "rounded" look via inset layers
        drawRect(this.x + 1, this.y, this.x + this.width - 1, this.y + this.height, border);
        drawRect(this.x, this.y + 1, this.x + this.width, this.y + this.height - 1, border);
        drawGradientRect(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, bgTop, bgBottom);
        drawRect(this.x + 2, this.y + 2, this.x + this.width - 2, this.y + 3, 0x22FFFFFF);

        int textWidth = fr.getStringWidth(this.displayString);
        fr.drawString(this.displayString, this.x + (this.width - textWidth) / 2, this.y + (this.height - 8) / 2, textColor, false);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
