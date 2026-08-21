package ru.ayanami.cosmetics;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
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
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import ru.ayanami.cosmetics.catalog.CatalogManager;
import ru.ayanami.cosmetics.catalog.ModelApplier;
import ru.ayanami.cosmetics.update.UpdateManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Essential Wardrobe-style cosmetics screen:
 * left nav + subcategory list, center card grid, right 3D player preview.
 */
@SideOnly(Side.CLIENT)
public class GuiCosmeticsMenu extends GuiScreen {

    private static final int ID_CLOSE = 1;
    private static final int ID_APPLY = 2;
    private static final int ID_UPDATE = 3;
    private static final int ID_ADVANCED = 4;
    private static final int ID_FRONT = 5;
    private static final int ID_BACK = 6;
    private static final int ID_TAB_COSMETICS = 10;
    private static final int ID_SUB_BASE = 100;

    private static final String[] DEFAULT_SUBS = new String[] {
            "cape", "wings", "back", "particles", "pets", "hat", "hair",
            "face", "ears", "head", "outerwear", "top", "pants", "arms", "shoes", "weapons", "misc"
    };

    private final GuiScreen parent;

    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, gridW, gridH;
    private int previewX, previewY, previewW, previewH;

    private GuiTextField searchField;
    private String selectedSub = "all";
    private CatalogManager.CatalogEntry selected;
    private List<CatalogManager.CatalogEntry> visible = new ArrayList<CatalogManager.CatalogEntry>();
    private int scrollRow;

    private float rotateYaw = 20.0F;
    private boolean dragging;
    private int dragLastX;
    private String outfitName = "Default";

    private static final int CARD_W = 78;
    private static final int CARD_H = 86;
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

        this.panelW = Math.min(560, this.width - 12);
        this.panelH = Math.min(300, this.height - 12);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int leftW = 110;
        this.previewW = 160;
        this.previewH = this.panelH - 50;
        this.previewX = this.panelX + this.panelW - this.previewW - 10;
        this.previewY = this.panelY + 36;

        this.gridX = this.panelX + leftW + 12;
        this.gridY = this.panelY + 52;
        this.gridW = this.previewX - this.gridX - 10;
        this.gridH = this.panelH - 80;

        this.searchField = new GuiTextField(20, this.fontRenderer, this.gridX + this.gridW - 110, this.panelY + 30, 100, 14);
        this.searchField.setMaxStringLength(40);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setTextColor(0xFFE8EAF0);

        // Subcategory chips on left under Cosmetics
        List<String> subs = buildSubList();
        int sy = this.panelY + 92;
        for (int i = 0; i < subs.size() && i < 12; i++) {
            String sub = subs.get(i);
            boolean on = sub.equalsIgnoreCase(this.selectedSub);
            this.buttonList.add(new GuiStyledButton(
                    ID_SUB_BASE + i,
                    this.panelX + 10,
                    sy + i * 15,
                    96,
                    13,
                    prettySub(sub),
                    on ? GuiStyledButton.Style.CHIP_ACTIVE : GuiStyledButton.Style.CHIP
            ));
        }

        // Preview controls
        this.buttonList.add(new GuiStyledButton(ID_FRONT, this.previewX + 18, this.previewY + this.previewH - 22, 50, 14, tr("gui.tweakos.front", "Front"), GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_BACK, this.previewX + 78, this.previewY + this.previewH - 22, 50, 14, tr("gui.tweakos.back", "Back"), GuiStyledButton.Style.SECONDARY));

        int by = this.panelY + this.panelH - 24;
        this.buttonList.add(new GuiStyledButton(ID_ADVANCED, this.panelX + 10, by, 70, 16, tr("gui.tweakos.advanced", "Advanced"), GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_UPDATE, this.panelX + 86, by, 60, 16, tr("gui.tweakos.update", "Update"), GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_APPLY, this.previewX + 20, by, 55, 16, tr("gui.tweakos.apply", "Apply"), GuiStyledButton.Style.PRIMARY));
        this.buttonList.add(new GuiStyledButton(ID_CLOSE, this.previewX + 85, by, 55, 16, tr("gui.tweakos.done", "Close"), GuiStyledButton.Style.SECONDARY));

        rebuildVisible();
        if (this.selected == null && !this.visible.isEmpty()) {
            this.selected = this.visible.get(0);
        }
    }

    private List<String> buildSubList() {
        List<String> list = new ArrayList<String>();
        list.add("all");
        for (int i = 0; i < DEFAULT_SUBS.length; i++) {
            list.add(DEFAULT_SUBS[i]);
        }
        // Also include any custom categories from catalog.
        List<String> fromCatalog = CatalogManager.listCategories();
        for (int i = 0; i < fromCatalog.size(); i++) {
            String c = fromCatalog.get(i).toLowerCase(Locale.ROOT);
            if (!list.contains(c)) {
                list.add(c);
            }
        }
        return list;
    }

    private String prettySub(String sub) {
        if (sub == null || sub.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(sub.charAt(0)) + sub.substring(1);
    }

    private void rebuildVisible() {
        String q = this.searchField != null ? this.searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<CatalogManager.CatalogEntry> src = CatalogManager.getEntriesByCategory(
                "all".equalsIgnoreCase(this.selectedSub) ? "all" : this.selectedSub
        );
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
        if (button.id == ID_CLOSE) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (button.id == ID_ADVANCED) {
            this.mc.displayGuiScreen(new GuiTweakOS(this));
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
        if (button.id == ID_FRONT) {
            this.rotateYaw = 20.0F;
            return;
        }
        if (button.id == ID_BACK) {
            this.rotateYaw = 200.0F;
            return;
        }
        if (button.id >= ID_SUB_BASE && button.id < ID_SUB_BASE + 40) {
            List<String> subs = buildSubList();
            int idx = button.id - ID_SUB_BASE;
            if (idx >= 0 && idx < subs.size()) {
                this.selectedSub = subs.get(idx);
                this.scrollRow = 0;
                this.initGui();
            }
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
            int idx = cardIndexAt(mouseX, mouseY);
            if (idx >= 0 && idx < this.visible.size()) {
                this.selected = this.visible.get(idx);
                return;
            }
            if (mouseX >= this.previewX && mouseX <= this.previewX + this.previewW
                    && mouseY >= this.previewY && mouseY <= this.previewY + this.previewH - 28) {
                this.dragging = true;
                this.dragLastX = mouseX;
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
            this.rotateYaw += (mouseX - this.dragLastX) * 1.8F;
            this.dragLastX = mouseX;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
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
        drawRect(0, 0, this.width, this.height, 0xC0101014);

        // Outer wardrobe frame
        drawPanel(this.panelX, this.panelY, this.panelW, this.panelH, 0xF018181E, 0xFF3A3A46);

        // Left sidebar
        drawPanel(this.panelX + 6, this.panelY + 6, 108, this.panelH - 36, 0xFF14141A, 0xFF2E2E38);
        this.fontRenderer.drawString(tr("gui.tweakos.wardrobe", "Wardrobe"), this.panelX + 14, this.panelY + 14, 0xFFE8EAF0, false);
        this.fontRenderer.drawString("TweakOS", this.panelX + 14, this.panelY + this.panelH - 28, 0xFF8B8E98, false);
        this.fontRenderer.drawString(tr("gui.tweakos.author", "Created by AyanamiKaede"), this.panelX + 14, this.panelY + this.panelH - 18, 0xFF6E7180, false);

        drawNavIconRow(this.panelX + 12, this.panelY + 32, tr("gui.tweakos.tab_outfits", "Outfits"), 0xFF5BD67A);
        drawNavIconRow(this.panelX + 12, this.panelY + 48, tr("gui.tweakos.tab_skins", "Skins"), 0xFFFF7AB8);
        drawNavIconRow(this.panelX + 12, this.panelY + 64, tr("gui.tweakos.tab_emotes", "Emotes"), 0xFFFF9A4A);
        // Active Cosmetics tab
        drawRect(this.panelX + 8, this.panelY + 78, this.panelX + 110, this.panelY + 92, 0xFF2A3348);
        this.fontRenderer.drawString(tr("gui.tweakos.tab_cosmetics", "Cosmetics"), this.panelX + 14, this.panelY + 82, 0xFFFFD76A, false);

        // Center header
        this.fontRenderer.drawString(tr("gui.tweakos.cosmetics_title", "Cosmetics"), this.gridX, this.panelY + 14, 0xFFF2F4FA, false);
        drawPanel(this.gridX + this.gridW - 114, this.panelY + 28, 108, 18, 0xFF101016, 0xFF3A3A46);
        if (this.searchField != null) {
            if (this.searchField.getText().isEmpty() && !this.searchField.isFocused()) {
                this.fontRenderer.drawString(tr("gui.tweakos.search", "Search..."), this.gridX + this.gridW - 108, this.panelY + 33, 0xFF6E7180, false);
            }
            this.searchField.drawTextBox();
        }
        this.fontRenderer.drawString(UpdateManager.getLastStatus(), this.gridX + 90, this.panelY + 14, 0xFF8B8E98, false);

        // Card grid background
        drawPanel(this.gridX - 2, this.gridY - 2, this.gridW + 4, this.gridH + 4, 0xFF101014, 0xFF2C2C36);
        drawCardGrid(mouseX, mouseY);

        // Preview
        drawPreview(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private void drawNavIconRow(int x, int y, String label, int color) {
        drawRect(x, y, x + 8, y + 8, color);
        this.fontRenderer.drawString(label, x + 12, y, 0xFFC8CAD2, false);
    }

    private void drawCardGrid(int mouseX, int mouseY) {
        if (this.visible.isEmpty()) {
            String empty = tr("gui.tweakos.empty_catalog", "Catalog is empty");
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
        int border = sel ? 0xFFFFD76A : (hovered ? 0xFF8A8A98 : 0xFF3A3A46);
        drawPanel(x, y, CARD_W, CARD_H, 0xFF1A1A22, border);

        // Pedestal / icon well
        drawRect(x + 10, y + 10, x + CARD_W - 10, y + 58, 0xFF0C0C12);
        drawRect(x + 18, y + 50, x + CARD_W - 18, y + 56, 0xFF2A2A34);

        drawItemIcon(entry, x + CARD_W / 2 - 8, y + 22);

        if ("weapons".equalsIgnoreCase(entry.category) || "hat".equalsIgnoreCase(entry.category)) {
            drawRect(x + 4, y + 4, x + 28, y + 12, 0xFF2E8B57);
            this.fontRenderer.drawString("NEW", x + 6, y + 5, 0xFFE8FFE8, false);
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

    private void drawPreview(int mouseX, int mouseY) {
        drawPanel(this.previewX, this.previewY, this.previewW, this.previewH, 0xFF121218, 0xFF3A3A46);

        // Outfit name bar
        drawPanel(this.previewX + 20, this.previewY + 8, this.previewW - 40, 14, 0xFF1A1A22, 0xFF3A3A46);
        int nw = this.fontRenderer.getStringWidth(this.outfitName);
        this.fontRenderer.drawString(this.outfitName, this.previewX + (this.previewW - nw) / 2, this.previewY + 11, 0xFFE8EAF0, false);

        if (this.mc.player != null) {
            int entX = this.previewX + this.previewW / 2;
            int entY = this.previewY + this.previewH - 48;
            GuiInventory.drawEntityOnScreen(entX, entY, 55, this.rotateYaw, -10.0F, (EntityLivingBase) this.mc.player);
        }

        // Fake color swatches (visual Essential-like; wiring later)
        int[] colors = new int[] {0xFF4A7CFF, 0xFF5BD67A, 0xFFFF9A4A, 0xFFFF7AB8};
        int sx = this.previewX + this.previewW / 2 - 34;
        int sy = this.previewY + this.previewH - 40;
        for (int i = 0; i < colors.length; i++) {
            drawRect(sx + i * 18, sy, sx + i * 18 + 14, sy + 10, colors[i]);
        }

        if (this.selected != null) {
            String n = this.selected.name;
            if (this.fontRenderer.getStringWidth(n) > this.previewW - 16) {
                while (this.fontRenderer.getStringWidth(n + "..") > this.previewW - 16 && n.length() > 3) {
                    n = n.substring(0, n.length() - 1);
                }
                n = n + "..";
            }
            this.fontRenderer.drawString(n, this.previewX + 8, this.previewY + this.previewH - 54, 0xFFB8CCFF, false);
        }
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
