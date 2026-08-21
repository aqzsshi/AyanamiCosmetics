package ru.ayanami.cosmetics;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import ru.ayanami.cosmetics.catalog.CatalogManager;
import ru.ayanami.cosmetics.catalog.ModelApplier;
import ru.ayanami.cosmetics.catalog.PreviewTextureCache;
import ru.ayanami.cosmetics.catalog.ServerPackClone;
import ru.ayanami.cosmetics.update.UpdateManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Essential Wardrobe-style screen: Hats / Hand cosmetics, render previews, color variants.
 * Right panel shows selected model preview (not the player).
 */
@SideOnly(Side.CLIENT)
public class GuiCosmeticsMenu extends GuiScreen {

    private static final int ID_BACK = 1;
    private static final int ID_APPLY = 2;
    private static final int ID_PRIORITY = 3;
    private static final int ID_ADD = 4;
    private static final int ID_UPDATE = 5;
    private static final int ID_CLONE = 6;
    private static final int ID_CAT_HATS = 10;
    private static final int ID_CAT_HAND = 11;

    private static final ResourceLocation TEX_PLUS = new ResourceLocation(TweakOS.MODID, "textures/gui/btn_plus.png");
    private static final ResourceLocation TEX_GEAR = new ResourceLocation(TweakOS.MODID, "textures/gui/icon_gear.png");

    private final GuiScreen parent;

    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, gridW, gridH;
    private int previewX, previewY, previewW, previewH;
    private int sideX, sideW;

    private GuiTextField searchField;
    private String selectedCat = CatalogManager.CAT_HATS;
    private CatalogManager.CatalogEntry selected;
    private List<CatalogManager.CatalogEntry> visible = new ArrayList<CatalogManager.CatalogEntry>();
    private int scrollRow;
    private String status = "";

    private static final int CARD_W = 84;
    private static final int CARD_H = 96;
    private static final int CARD_GAP = 8;
    private static final int COLS = 3;

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
        Keyboard.enableRepeatEvents(true);
        CatalogManager.reload();
        ServerPackClone.ensureClonedFromServer();

        this.panelW = Math.min(620, this.width - 8);
        this.panelH = Math.min(320, this.height - 8);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        this.sideW = 118;
        this.sideX = this.panelX + 6;
        this.previewW = 170;
        this.previewH = this.panelH - 56;
        this.previewX = this.panelX + this.panelW - this.previewW - 8;
        this.previewY = this.panelY + 40;

        this.gridX = this.sideX + this.sideW + 10;
        this.gridY = this.panelY + 56;
        this.gridW = this.previewX - this.gridX - 10;
        this.gridH = this.panelH - 88;

        this.searchField = new GuiTextField(20, this.fontRenderer, this.gridX + this.gridW - 118, this.panelY + 28, 110, 14);
        this.searchField.setMaxStringLength(40);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setTextColor(0xFFE8EAF0);

        // Categories
        this.buttonList.add(new GuiStyledButton(ID_CAT_HATS, this.sideX + 6, this.panelY + 40, 106, 16,
                tr("gui.tweakos.cat_hats", "Hats"),
                CatalogManager.CAT_HATS.equals(this.selectedCat) ? GuiStyledButton.Style.CHIP_ACTIVE : GuiStyledButton.Style.CHIP));
        this.buttonList.add(new GuiStyledButton(ID_CAT_HAND, this.sideX + 6, this.panelY + 60, 106, 16,
                tr("gui.tweakos.cat_hand", "Hand cosmetics"),
                CatalogManager.CAT_HAND.equals(this.selectedCat) ? GuiStyledButton.Style.CHIP_ACTIVE : GuiStyledButton.Style.CHIP));

        // Header actions
        this.buttonList.add(new GuiStyledButton(ID_BACK, this.panelX + 8, this.panelY + 8, 18, 16, "<", GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_PRIORITY, this.previewX, this.panelY + 8, 18, 16, "S", GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_ADD, this.previewX + this.previewW - 20, this.panelY + 8, 18, 16, "+", GuiStyledButton.Style.PRIMARY));
        this.buttonList.add(new GuiStyledButton(ID_CLONE, this.previewX + 22, this.panelY + 8, 48, 16, tr("gui.tweakos.clone_rp", "Clone"), GuiStyledButton.Style.CHIP));
        this.buttonList.add(new GuiStyledButton(ID_UPDATE, this.previewX + 74, this.panelY + 8, 48, 16, tr("gui.tweakos.update", "Update"), GuiStyledButton.Style.CHIP));

        int by = this.panelY + this.panelH - 24;
        this.buttonList.add(new GuiStyledButton(ID_APPLY, this.previewX + 20, by, 70, 16, tr("gui.tweakos.apply", "Apply"), GuiStyledButton.Style.PRIMARY));
        this.buttonList.add(new GuiStyledButton(ID_BACK + 100, this.previewX + 96, by, 55, 16, tr("gui.tweakos.done", "Close"), GuiStyledButton.Style.SECONDARY));

        rebuildVisible();
        if (this.selected == null && !this.visible.isEmpty()) {
            this.selected = this.visible.get(0);
        }
    }

    private void rebuildVisible() {
        String q = this.searchField != null ? this.searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<CatalogManager.CatalogEntry> src = CatalogManager.getEntriesByCategory(this.selectedCat);
        this.visible = new ArrayList<CatalogManager.CatalogEntry>();
        for (int i = 0; i < src.size(); i++) {
            CatalogManager.CatalogEntry e = src.get(i);
            if (!q.isEmpty() && !e.name.toLowerCase(Locale.ROOT).contains(q) && !e.id.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            this.visible.add(e);
        }
        int maxRow = Math.max(0, rows() - visibleRows());
        this.scrollRow = Math.min(this.scrollRow, maxRow);
    }

    private int rows() {
        return this.visible.isEmpty() ? 0 : (this.visible.size() + COLS - 1) / COLS;
    }

    private int visibleRows() {
        return Math.max(1, this.gridH / (CARD_H + CARD_GAP));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        if (this.searchField != null) {
            this.searchField.updateCursorCounter();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_BACK || button.id == ID_BACK + 100) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (button.id == ID_PRIORITY) {
            this.mc.displayGuiScreen(new GuiPackPriority(this));
            return;
        }
        if (button.id == ID_ADD) {
            this.mc.displayGuiScreen(new GuiAddModel(this));
            return;
        }
        if (button.id == ID_CLONE) {
            boolean ok = ServerPackClone.forceCloneFromServer();
            this.status = ok ? tr("gui.tweakos.clone_ok", "Server RP cloned") : tr("gui.tweakos.clone_fail", "No server RP");
            return;
        }
        if (button.id == ID_APPLY) {
            if (this.selected != null) {
                boolean ok = ModelApplier.apply(this.selected);
                this.status = ok ? tr("gui.tweakos.applied", "Applied") : tr("gui.tweakos.apply_fail", "Apply failed");
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
        if (button.id == ID_CAT_HATS) {
            this.selectedCat = CatalogManager.CAT_HATS;
            this.scrollRow = 0;
            this.selected = null;
            this.initGui();
            return;
        }
        if (button.id == ID_CAT_HAND) {
            this.selectedCat = CatalogManager.CAT_HAND;
            this.scrollRow = 0;
            this.selected = null;
            this.initGui();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (this.searchField != null && this.searchField.textboxKeyTyped(typedChar, keyCode)) {
            this.scrollRow = 0;
            rebuildVisible();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int max = Math.max(0, rows() - visibleRows());
            this.scrollRow = wheel > 0 ? Math.max(0, this.scrollRow - 1) : Math.min(max, this.scrollRow + 1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.searchField != null) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (mouseButton == 0) {
            // Color swatches on selected card
            if (this.selected != null && clickVariantOnCard(mouseX, mouseY)) {
                return;
            }
            // Big color swatches under preview
            if (clickPreviewSwatch(mouseX, mouseY)) {
                return;
            }
            int idx = cardIndexAt(mouseX, mouseY);
            if (idx >= 0 && idx < this.visible.size()) {
                this.selected = this.visible.get(idx);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean clickPreviewSwatch(int mouseX, int mouseY) {
        if (this.selected == null || this.selected.variants == null) {
            return false;
        }
        int sx = this.previewX + this.previewW / 2 - (this.selected.variants.size() * 18) / 2;
        int sy = this.previewY + this.previewH - 36;
        for (int i = 0; i < this.selected.variants.size(); i++) {
            int x = sx + i * 18;
            if (mouseX >= x && mouseX < x + 14 && mouseY >= sy && mouseY < sy + 14) {
                this.selected.selectedVariantId = this.selected.variants.get(i).id;
                return true;
            }
        }
        return false;
    }

    private boolean clickVariantOnCard(int mouseX, int mouseY) {
        int start = this.scrollRow;
        int end = Math.min(rows(), start + visibleRows());
        for (int row = start; row < end; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                if (index >= this.visible.size()) {
                    break;
                }
                CatalogManager.CatalogEntry entry = this.visible.get(index);
                if (this.selected == null || !this.selected.id.equals(entry.id)) {
                    continue;
                }
                int x = this.gridX + col * (CARD_W + CARD_GAP);
                int y = this.gridY + (row - start) * (CARD_H + CARD_GAP);
                int vx = x + CARD_W - 14;
                int vy = y + 16;
                for (int i = 0; i < entry.variants.size() && i < 5; i++) {
                    int cy = vy + i * 12;
                    if (mouseX >= vx && mouseX < vx + 10 && mouseY >= cy && mouseY < cy + 10) {
                        entry.selectedVariantId = entry.variants.get(i).id;
                        this.selected = entry;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int cardIndexAt(int mouseX, int mouseY) {
        if (mouseX < this.gridX || mouseY < this.gridY || mouseX >= this.gridX + this.gridW || mouseY >= this.gridY + this.gridH) {
            return -1;
        }
        int lx = mouseX - this.gridX;
        int ly = mouseY - this.gridY;
        int col = lx / (CARD_W + CARD_GAP);
        int row = ly / (CARD_H + CARD_GAP);
        if (col < 0 || col >= COLS) {
            return -1;
        }
        int cx = col * (CARD_W + CARD_GAP);
        int cy = row * (CARD_H + CARD_GAP);
        if (lx < cx || lx >= cx + CARD_W || ly < cy || ly >= cy + CARD_H) {
            return -1;
        }
        return (this.scrollRow + row) * COLS + col;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(0, 0, this.width, this.height, 0xD00C0C10);

        // Main frame
        drawPanel(this.panelX, this.panelY, this.panelW, this.panelH, 0xF0121218, 0xFF2E2E38);

        // Header
        this.fontRenderer.drawString(tr("gui.tweakos.wardrobe", "Wardrobe"), this.panelX + 32, this.panelY + 12, 0xFFE8EAF0, false);
        String catTitle = CatalogManager.CAT_HATS.equals(this.selectedCat)
                ? tr("gui.tweakos.cat_hats", "Hats")
                : tr("gui.tweakos.cat_hand", "Hand cosmetics");
        this.fontRenderer.drawString(catTitle, this.panelX + 100, this.panelY + 12, 0xFFFFD76A, false);

        // Left sidebar
        drawPanel(this.sideX, this.panelY + 32, this.sideW, this.panelH - 60, 0xFF101016, 0xFF2A2A34);
        this.fontRenderer.drawString(tr("gui.tweakos.categories", "Categories"), this.sideX + 8, this.panelY + 36, 0xFF8B8E98, false);
        this.fontRenderer.drawString("TweakOS", this.sideX + 8, this.panelY + this.panelH - 40, 0xFF6E7180, false);
        this.fontRenderer.drawString(tr("gui.tweakos.author", "Created by AyanamiKaede"), this.sideX + 8, this.panelY + this.panelH - 28, 0xFF555860, false);

        // Search
        drawPanel(this.gridX + this.gridW - 122, this.panelY + 26, 118, 18, 0xFF0C0C12, 0xFF3A3A46);
        if (this.searchField != null) {
            if (this.searchField.getText().isEmpty() && !this.searchField.isFocused()) {
                this.fontRenderer.drawString(tr("gui.tweakos.search", "Search..."), this.gridX + this.gridW - 116, this.panelY + 31, 0xFF6E7180, false);
            }
            this.searchField.drawTextBox();
        }

        String st = this.status != null && !this.status.isEmpty() ? this.status : UpdateManager.getLastStatus();
        this.fontRenderer.drawString(st, this.gridX, this.panelY + 30, 0xFF8B8E98, false);

        // Grid
        drawPanel(this.gridX - 2, this.gridY - 2, this.gridW + 4, this.gridH + 4, 0xFF0A0A10, 0xFF2C2C36);
        drawCardGrid(mouseX, mouseY);

        // Right model preview (NOT player)
        drawModelPreview();

        // Draw gear/plus icons over buttons
        GlStateManager.color(1F, 1F, 1F, 1F);
        this.mc.getTextureManager().bindTexture(TEX_GEAR);
        drawModalRectWithCustomSizedTexture(this.previewX + 1, this.panelY + 9, 0, 0, 14, 14, 32, 32);
        this.mc.getTextureManager().bindTexture(TEX_PLUS);
        drawModalRectWithCustomSizedTexture(this.previewX + this.previewW - 19, this.panelY + 9, 0, 0, 16, 16, 64, 64);

        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private void drawCardGrid(int mouseX, int mouseY) {
        if (this.visible.isEmpty()) {
            String empty = tr("gui.tweakos.empty_catalog", "Catalog empty — press + to add");
            int w = this.fontRenderer.getStringWidth(empty);
            this.fontRenderer.drawString(empty, this.gridX + (this.gridW - w) / 2, this.gridY + this.gridH / 2, 0xFFFF8E8E, false);
            return;
        }
        int start = this.scrollRow;
        int end = Math.min(rows(), start + visibleRows());
        int hover = cardIndexAt(mouseX, mouseY);
        for (int row = start; row < end; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                if (index >= this.visible.size()) {
                    break;
                }
                int x = this.gridX + col * (CARD_W + CARD_GAP);
                int y = this.gridY + (row - start) * (CARD_H + CARD_GAP);
                drawCosmeticCard(x, y, this.visible.get(index), index == hover);
            }
        }
    }

    private void drawCosmeticCard(int x, int y, CatalogManager.CatalogEntry entry, boolean hovered) {
        boolean sel = this.selected != null && this.selected.id.equals(entry.id);
        int border = sel ? 0xFFFFF0C0 : (hovered ? 0xFF8A8A98 : 0xFF3A3A46);
        drawPanel(x, y, CARD_W, CARD_H, 0xFF1A1A20, border);

        // Preview well
        drawRect(x + 6, y + 8, x + CARD_W - 18, y + 68, 0xFF0C0C12);
        File preview = entry.resolvePreviewFile();
        ResourceLocation loc = PreviewTextureCache.getOrLoad(preview);
        if (loc != null) {
            PreviewTextureCache.draw(loc, x + 10, y + 12, 48, 48);
        } else {
            // Pedestal placeholder
            drawRect(x + 22, y + 48, x + CARD_W - 34, y + 56, 0xFF2A2A34);
            this.fontRenderer.drawString("?", x + 28, y + 28, 0xFF5A5A68, false);
        }

        // Vertical color swatches (Essential-like)
        int vx = x + CARD_W - 14;
        int vy = y + 16;
        for (int i = 0; i < entry.variants.size() && i < 5; i++) {
            CatalogManager.ColorVariant v = entry.variants.get(i);
            int cy = vy + i * 12;
            boolean on = v.id.equalsIgnoreCase(entry.selectedVariantId);
            drawRect(vx - 1, cy - 1, vx + 11, cy + 11, on ? 0xFFFFFFFF : 0xFF000000);
            drawRect(vx, cy, vx + 10, cy + 10, v.parseColorArgb());
        }

        if (sel) {
            // checkmark
            this.fontRenderer.drawString("v", x + CARD_W - 12, y + CARD_H - 14, 0xFF5BD67A, false);
        }

        String label = entry.name;
        if (this.fontRenderer.getStringWidth(label) > CARD_W - 8) {
            while (this.fontRenderer.getStringWidth(label + "..") > CARD_W - 8 && label.length() > 3) {
                label = label.substring(0, label.length() - 1);
            }
            label = label + "..";
        }
        this.fontRenderer.drawString(label, x + 5, y + CARD_H - 12, sel ? 0xFFFFF0C0 : 0xFFE6E8F0, false);
    }

    private void drawModelPreview() {
        drawPanel(this.previewX, this.previewY, this.previewW, this.previewH, 0xFF101016, 0xFF3A3A46);
        this.fontRenderer.drawString(tr("gui.tweakos.preview", "Preview"), this.previewX + 8, this.previewY + 6, 0xFF8B8E98, false);

        if (this.selected == null) {
            String none = tr("gui.tweakos.no_selection", "Select a model");
            int w = this.fontRenderer.getStringWidth(none);
            this.fontRenderer.drawString(none, this.previewX + (this.previewW - w) / 2, this.previewY + this.previewH / 2, 0xFF6E7180, false);
            return;
        }

        File preview = this.selected.resolvePreviewFile();
        ResourceLocation loc = PreviewTextureCache.getOrLoad(preview);
        int imgW = this.previewW - 24;
        int imgH = this.previewH - 70;
        int ix = this.previewX + 12;
        int iy = this.previewY + 22;
        drawRect(ix, iy, ix + imgW, iy + imgH, 0xFF0A0A10);
        if (loc != null) {
            PreviewTextureCache.draw(loc, ix + 8, iy + 8, imgW - 16, imgH - 16);
        } else {
            String miss = tr("gui.tweakos.no_preview", "Add preview.png");
            int w = this.fontRenderer.getStringWidth(miss);
            this.fontRenderer.drawString(miss, this.previewX + (this.previewW - w) / 2, iy + imgH / 2, 0xFF6E7180, false);
        }

        String n = this.selected.name;
        int nw = this.fontRenderer.getStringWidth(n);
        this.fontRenderer.drawString(n, this.previewX + (this.previewW - nw) / 2, this.previewY + this.previewH - 52, 0xFFE8EAF0, false);

        // Color bar
        if (this.selected.variants != null) {
            int sx = this.previewX + this.previewW / 2 - (this.selected.variants.size() * 18) / 2;
            int sy = this.previewY + this.previewH - 36;
            for (int i = 0; i < this.selected.variants.size(); i++) {
                CatalogManager.ColorVariant v = this.selected.variants.get(i);
                int x = sx + i * 18;
                boolean on = v.id.equalsIgnoreCase(this.selected.selectedVariantId);
                drawRect(x - 1, sy - 1, x + 15, sy + 15, on ? 0xFFFFFFFF : 0xFF222228);
                drawRect(x, sy, x + 14, sy + 14, v.parseColorArgb());
            }
        }
    }

    private void drawPanel(int x, int y, int w, int h, int fill, int border) {
        drawRect(x + 1, y, x + w - 1, y + h, border);
        drawRect(x, y + 1, x + w, y + h - 1, border);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
