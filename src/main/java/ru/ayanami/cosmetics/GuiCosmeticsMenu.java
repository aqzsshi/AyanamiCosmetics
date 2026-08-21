package ru.ayanami.cosmetics;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;
import ru.ayanami.cosmetics.catalog.CatalogManager;
import ru.ayanami.cosmetics.catalog.ModelApplier;
import ru.ayanami.cosmetics.update.UpdateManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Essential-like cosmetics menu: categories, cards, 3D player preview.
 */
@SideOnly(Side.CLIENT)
public class GuiCosmeticsMenu extends GuiScreen {

    private static final int ID_CLOSE = 1;
    private static final int ID_APPLY = 2;
    private static final int ID_UPDATE = 3;
    private static final int ID_ADVANCED = 4;
    private static final int ID_CAT_BASE = 100;

    private final GuiScreen parent;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private String selectedCategory = "all";
    private CatalogManager.CatalogEntry selected;
    private List<CatalogManager.CatalogEntry> visible = new ArrayList<CatalogManager.CatalogEntry>();
    private int scroll;

    private float rotateYaw = 180.0F;
    private float rotatePitch = 0.0F;
    private boolean dragging;
    private int lastMouseX;

    public GuiCosmeticsMenu(GuiScreen parent) {
        this.parent = parent;
    }

    private static String tr(String key, String fb) {
        String v = I18n.format(key);
        return v == null || v.equals(key) ? fb : v;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        CatalogManager.reload();

        this.panelW = Math.min(520, this.width - 16);
        this.panelH = Math.min(280, this.height - 16);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        List<String> cats = CatalogManager.listCategories();
        int cx = this.panelX + 10;
        int cy = this.panelY + 28;
        for (int i = 0; i < cats.size() && i < 8; i++) {
            String cat = cats.get(i);
            boolean active = cat.equalsIgnoreCase(this.selectedCategory);
            GuiStyledButton b = new GuiStyledButton(
                    ID_CAT_BASE + i,
                    cx,
                    cy + i * 18,
                    70,
                    16,
                    cat,
                    active ? GuiStyledButton.Style.CHIP_ACTIVE : GuiStyledButton.Style.CHIP
            );
            this.buttonList.add(b);
        }

        int by = this.panelY + this.panelH - 26;
        this.buttonList.add(new GuiStyledButton(ID_APPLY, this.panelX + this.panelW - 200, by, 60, 18, tr("gui.ayanamicosmetics.apply", "Apply"), GuiStyledButton.Style.PRIMARY));
        this.buttonList.add(new GuiStyledButton(ID_UPDATE, this.panelX + this.panelW - 134, by, 60, 18, tr("gui.ayanamicosmetics.update", "Update"), GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_ADVANCED, this.panelX + 10, by, 70, 18, tr("gui.ayanamicosmetics.advanced", "Advanced"), GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_CLOSE, this.panelX + this.panelW - 68, by, 58, 18, tr("gui.ayanamicosmetics.done", "Close"), GuiStyledButton.Style.SECONDARY));

        rebuildVisible();
        if (this.selected == null && !this.visible.isEmpty()) {
            this.selected = this.visible.get(0);
        }
    }

    private void rebuildVisible() {
        this.visible = new ArrayList<CatalogManager.CatalogEntry>(CatalogManager.getEntriesByCategory(this.selectedCategory));
        this.scroll = Math.min(this.scroll, Math.max(0, this.visible.size() - 1));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_CLOSE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (button.id == ID_ADVANCED) {
            this.mc.displayGuiScreen(new GuiAyanamiCosmetics(this));
            return;
        }
        if (button.id == ID_APPLY) {
            if (this.selected != null) {
                ModelApplier.apply(this.selected);
            }
            return;
        }
        if (button.id == ID_UPDATE) {
            UpdateManager.checkAndUpdateAsync(new Runnable() {
                @Override
                public void run() {
                    CatalogManager.reload();
                    GuiCosmeticsMenu.this.initGui();
                }
            });
            return;
        }
        if (button.id >= ID_CAT_BASE && button.id < ID_CAT_BASE + 20) {
            List<String> cats = CatalogManager.listCategories();
            int idx = button.id - ID_CAT_BASE;
            if (idx >= 0 && idx < cats.size()) {
                this.selectedCategory = cats.get(idx);
                this.scroll = 0;
                this.initGui();
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            if (wheel > 0) {
                this.scroll = Math.max(0, this.scroll - 1);
            } else {
                this.scroll = Math.min(Math.max(0, this.visible.size() - 6), this.scroll + 1);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int listX = this.panelX + 90;
            int listY = this.panelY + 30;
            int listW = this.panelW - 260;
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + this.panelH - 70) {
                int row = (mouseY - listY) / 22;
                int index = this.scroll + row;
                if (index >= 0 && index < this.visible.size()) {
                    this.selected = this.visible.get(index);
                    return;
                }
            }
            // Start drag on preview
            int prevX = this.panelX + this.panelW - 150;
            int prevY = this.panelY + 40;
            if (mouseX >= prevX && mouseX <= prevX + 140 && mouseY >= prevY && mouseY <= prevY + 160) {
                this.dragging = true;
                this.lastMouseX = mouseX;
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.dragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.dragging) {
            this.rotateYaw += (mouseX - this.lastMouseX) * 1.5F;
            this.lastMouseX = mouseX;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(0, 0, this.width, this.height, 0xB008080C);

        // Main panel
        drawRect(this.panelX + 1, this.panelY, this.panelX + this.panelW - 1, this.panelY + this.panelH, 0xFF2A2A32);
        drawRect(this.panelX, this.panelY + 1, this.panelX + this.panelW, this.panelY + this.panelH - 1, 0xFF2A2A32);
        drawRect(this.panelX + 1, this.panelY + 1, this.panelX + this.panelW - 1, this.panelY + this.panelH - 1, 0xF0141418);

        this.fontRenderer.drawString(tr("gui.ayanamicosmetics.cosmetics_title", "Cosmetics"), this.panelX + 12, this.panelY + 10, 0xFFE8EAF0, false);
        this.fontRenderer.drawString(UpdateManager.getLastStatus(), this.panelX + 120, this.panelY + 10, 0xFF8B8E98, false);

        // Category header
        this.fontRenderer.drawString(tr("gui.ayanamicosmetics.categories", "Categories"), this.panelX + 12, this.panelY + 30 - 12, 0xFF8B8E98, false);

        // List panel
        int listX = this.panelX + 90;
        int listY = this.panelY + 28;
        int listW = this.panelW - 260;
        int listH = this.panelH - 64;
        drawRect(listX, listY, listX + listW, listY + listH, 0xFF101014);
        drawRect(listX, listY, listX + listW, listY + 1, 0xFF3A3A44);

        if (this.visible.isEmpty()) {
            this.fontRenderer.drawString(tr("gui.ayanamicosmetics.empty_catalog", "Catalog is empty. Use Update or add folders."), listX + 8, listY + 20, 0xFFFF8E8E, false);
        } else {
            int end = Math.min(this.visible.size(), this.scroll + listH / 22);
            for (int i = this.scroll; i < end; i++) {
                CatalogManager.CatalogEntry e = this.visible.get(i);
                int y = listY + 4 + (i - this.scroll) * 22;
                boolean sel = this.selected != null && this.selected.id.equals(e.id);
                boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY < y + 20;
                if (sel) {
                    drawRect(listX + 2, y, listX + listW - 2, y + 20, 0xFF2A3F66);
                } else if (hover) {
                    drawRect(listX + 2, y, listX + listW - 2, y + 20, 0xFF22222A);
                }
                drawItemIcon(e, listX + 6, y + 2);
                this.fontRenderer.drawString(e.name, listX + 28, y + 6, sel ? 0xFFB8CCFF : 0xFFE6E8F0, false);
            }
        }

        // Preview panel
        int prevX = this.panelX + this.panelW - 155;
        int prevY = this.panelY + 28;
        int prevW = 145;
        int prevH = this.panelH - 64;
        drawRect(prevX, prevY, prevX + prevW, prevY + prevH, 0xFF101014);
        this.fontRenderer.drawString(tr("gui.ayanamicosmetics.preview", "Preview"), prevX + 8, prevY + 6, 0xFF8B8E98, false);

        // 3D player
        if (this.mc.player != null) {
            int entX = prevX + prevW / 2;
            int entY = prevY + prevH - 36;
            GuiInventory.drawEntityOnScreen(entX, entY, 48, this.rotateYaw - 180.0F, this.rotatePitch, (EntityLivingBase) this.mc.player);
        }

        if (this.selected != null) {
            this.fontRenderer.drawString(this.selected.name, prevX + 8, prevY + prevH - 28, 0xFFF0F2F8, false);
            String path = this.selected.replacePath;
            if (path.length() > 28) {
                path = "..." + path.substring(path.length() - 25);
            }
            this.fontRenderer.drawString(path, prevX + 8, prevY + prevH - 16, 0xFF8B8E98, false);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawItemIcon(CatalogManager.CatalogEntry entry, int x, int y) {
        ItemStack stack = resolveIcon(entry);
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        this.itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private ItemStack resolveIcon(CatalogManager.CatalogEntry entry) {
        try {
            if (entry.iconItem != null && entry.iconItem.contains(":")) {
                String[] p = entry.iconItem.split(":", 2);
                Item item = Item.REGISTRY.getObject(new ResourceLocation(p[0], p[1]));
                if (item != null) {
                    return new ItemStack(item);
                }
            }
        } catch (Exception ignored) {
        }
        return new ItemStack(Items.PAPER);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
