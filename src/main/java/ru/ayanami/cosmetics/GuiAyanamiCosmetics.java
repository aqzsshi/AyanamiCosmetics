package ru.ayanami.cosmetics;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Redesigned settings screen: single panel with override toggle, scrollable pack list,
 * live status and keybind shortcut to Minecraft Controls.
 */
@SideOnly(Side.CLIENT)
public class GuiAyanamiCosmetics extends GuiScreen {

    private static final int ID_TOGGLE = 1;
    private static final int ID_RELOAD = 2;
    private static final int ID_KEYBINDS = 3;
    private static final int ID_DONE = 4;
    private static final int ID_SCROLL_UP = 5;
    private static final int ID_SCROLL_DOWN = 6;

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 220;
    private static final int PACK_ROW_HEIGHT = 18;
    private static final int PACK_VISIBLE_ROWS = 6;

    private final GuiScreen parent;

    private int panelX;
    private int panelY;
    private int packListX;
    private int packListY;
    private int packListW;
    private int packListH;

    private List<String> packs = new ArrayList<String>();
    private int scrollOffset;
    private int hoveredPackIndex = -1;

    public GuiAyanamiCosmetics(GuiScreen parent) {
        this.parent = parent;
    }

    private static String tr(String key, String fallback) {
        String value = I18n.format(key);
        if (value == null || value.equals(key)) {
            return fallback;
        }
        return value;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        ResourcePackManager.ensureSelectedPackExists();
        this.packs = ResourcePackManager.listAvailablePackNames();
        this.scrollOffset = Math.min(this.scrollOffset, Math.max(0, this.packs.size() - PACK_VISIBLE_ROWS));

        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;

        this.packListX = this.panelX + 14;
        this.packListY = this.panelY + 78;
        this.packListW = PANEL_WIDTH - 28;
        this.packListH = PACK_VISIBLE_ROWS * PACK_ROW_HEIGHT + 4;

        int btnY = this.panelY + PANEL_HEIGHT - 48;
        int btnW = 70;
        int gap = 6;
        int total = btnW * 4 + gap * 3;
        int startX = this.panelX + (PANEL_WIDTH - total) / 2;

        String toggleLabel = Config.isOverrideEnabled()
                ? tr("gui.ayanamicosmetics.disable", "Override OFF")
                : tr("gui.ayanamicosmetics.enable", "Override ON");

        this.buttonList.add(new GuiStyledButton(ID_TOGGLE, startX, btnY, btnW, 20, toggleLabel, true));
        this.buttonList.add(new GuiStyledButton(ID_RELOAD, startX + (btnW + gap), btnY, btnW, 20, tr("gui.ayanamicosmetics.reload_short", "Reload"), false));
        this.buttonList.add(new GuiStyledButton(ID_KEYBINDS, startX + 2 * (btnW + gap), btnY, btnW, 20, tr("gui.ayanamicosmetics.keybinds", "Keys"), false));
        this.buttonList.add(new GuiStyledButton(ID_DONE, startX + 3 * (btnW + gap), btnY, btnW, 20, tr("gui.ayanamicosmetics.done", "Done"), false));

        // Tiny scroll buttons on the right of the pack list
        int scrollX = this.packListX + this.packListW - 14;
        this.buttonList.add(new GuiStyledButton(ID_SCROLL_UP, scrollX, this.packListY + 2, 12, 12, "^", false));
        this.buttonList.add(new GuiStyledButton(ID_SCROLL_DOWN, scrollX, this.packListY + this.packListH - 14, 12, 12, "v", false));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }
        switch (button.id) {
            case ID_TOGGLE:
                ResourcePackManager.setOverrideEnabled(!Config.isOverrideEnabled());
                this.initGui();
                break;
            case ID_RELOAD:
                ResourcePackManager.reloadResourcesFromGui();
                this.initGui();
                break;
            case ID_KEYBINDS:
                this.mc.displayGuiScreen(new GuiControls(this, this.mc.gameSettings));
                break;
            case ID_DONE:
                this.mc.displayGuiScreen(this.parent);
                break;
            case ID_SCROLL_UP:
                this.scrollOffset = Math.max(0, this.scrollOffset - 1);
                break;
            case ID_SCROLL_DOWN:
                int max = Math.max(0, this.packs.size() - PACK_VISIBLE_ROWS);
                this.scrollOffset = Math.min(max, this.scrollOffset + 1);
                break;
            default:
                break;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            if (isInsidePackList(mouseX, mouseY) || isInsidePanel(mouseX, mouseY)) {
                if (wheel > 0) {
                    this.scrollOffset = Math.max(0, this.scrollOffset - 1);
                } else {
                    int max = Math.max(0, this.packs.size() - PACK_VISIBLE_ROWS);
                    this.scrollOffset = Math.min(max, this.scrollOffset + 1);
                }
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && isInsidePackList(mouseX, mouseY)) {
            int index = getPackIndexAt(mouseX, mouseY);
            if (index >= 0 && index < this.packs.size()) {
                ResourcePackManager.selectPack(this.packs.get(index));
                this.initGui();
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean isInsidePanel(int mouseX, int mouseY) {
        return mouseX >= this.panelX && mouseX <= this.panelX + PANEL_WIDTH
                && mouseY >= this.panelY && mouseY <= this.panelY + PANEL_HEIGHT;
    }

    private boolean isInsidePackList(int mouseX, int mouseY) {
        return mouseX >= this.packListX && mouseX < this.packListX + this.packListW - 16
                && mouseY >= this.packListY + 2 && mouseY < this.packListY + this.packListH - 2;
    }

    private int getPackIndexAt(int mouseX, int mouseY) {
        if (!isInsidePackList(mouseX, mouseY)) {
            return -1;
        }
        int relY = mouseY - (this.packListY + 2);
        int row = relY / PACK_ROW_HEIGHT;
        if (row < 0 || row >= PACK_VISIBLE_ROWS) {
            return -1;
        }
        return this.scrollOffset + row;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.hoveredPackIndex = getPackIndexAt(mouseX, mouseY);

        // Outer glow / frame
        drawRect(this.panelX - 2, this.panelY - 2, this.panelX + PANEL_WIDTH + 2, this.panelY + PANEL_HEIGHT + 2, 0x88202A36);
        drawRect(this.panelX - 1, this.panelY - 1, this.panelX + PANEL_WIDTH + 1, this.panelY + PANEL_HEIGHT + 1, 0xFF5EB7D4);

        // Main panel
        drawGradientRect(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + PANEL_HEIGHT, 0xF0181E28, 0xF012161E);

        // Header bar
        drawGradientRect(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + 28, 0xFF243040, 0xFF1A222E);
        drawRect(this.panelX, this.panelY + 28, this.panelX + PANEL_WIDTH, this.panelY + 29, 0xFF5EB7D4);

        String title = tr("gui.ayanamicosmetics.title", "AyanamiCosmetics");
        this.fontRenderer.drawString(title, this.panelX + 12, this.panelY + 10, 0xFFE8F6FC, false);

        String keyName = KeyHandler.openGuiKey != null ? KeyHandler.openGuiKey.getDisplayName() : "O";
        String keyHint = tr("gui.ayanamicosmetics.key_hint", "Menu key:") + " " + keyName;
        int keyWidth = this.fontRenderer.getStringWidth(keyHint);
        this.fontRenderer.drawString(keyHint, this.panelX + PANEL_WIDTH - 12 - keyWidth, this.panelY + 10, 0xFF9BB4C4, false);

        // Override status strip
        boolean overrideOn = Config.isOverrideEnabled();
        boolean applied = ResourcePackManager.isOverrideApplied();
        boolean serverLoaded = ResourcePackManager.isServerResourcePackLoaded();

        int chipY = this.panelY + 36;
        drawStatusChip(this.panelX + 14, chipY, overrideOn ? "OVERRIDE ON" : "OVERRIDE OFF", overrideOn ? 0xFF2E8B57 : 0xFF8B3A3A, overrideOn);
        drawStatusChip(this.panelX + 118, chipY, serverLoaded ? "SERVER RP" : "NO SERVER RP", serverLoaded ? 0xFF2F6F8F : 0xFF6A5A2A, serverLoaded);
        drawStatusChip(this.panelX + 222, chipY, applied ? "APPLIED" : "NOT APPLIED", applied ? 0xFF2E8B57 : 0xFF6A4040, applied);

        // Pack section label
        this.fontRenderer.drawString(tr("gui.ayanamicosmetics.pack_list", "Cosmetic packs"), this.panelX + 14, this.panelY + 66, 0xFFB7CBD8, false);

        String selected = Config.getSelectedPackName();
        if (selected == null || selected.isEmpty()) {
            selected = tr("gui.ayanamicosmetics.pack_missing", "<none>");
        }
        String selectedLine = tr("gui.ayanamicosmetics.current_pack", "Current:") + " " + selected;
        this.fontRenderer.drawString(selectedLine, this.panelX + 14, this.panelY + PANEL_HEIGHT - 62, 0xFF8FD6EC, false);

        drawPackList(mouseX, mouseY);

        // Footer hint
        String hint = tr("gui.ayanamicosmetics.footer_hint", "Click a pack to select · Keys opens Controls to rebind");
        int hintW = this.fontRenderer.getStringWidth(hint);
        this.fontRenderer.drawString(hint, this.panelX + (PANEL_WIDTH - hintW) / 2, this.panelY + PANEL_HEIGHT - 22, 0xFF6E7F8C, false);

        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawStatusChip(int x, int y, String text, int color, boolean active) {
        int w = this.fontRenderer.getStringWidth(text) + 10;
        int h = 12;
        drawRect(x, y, x + w, y + h, 0xFF10151C);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, color);
        if (active) {
            drawRect(x + 1, y + 1, x + w - 1, y + 2, 0x55FFFFFF);
        }
        this.fontRenderer.drawString(text, x + 5, y + 2, 0xFFF5FBFF, false);
    }

    private void drawPackList(int mouseX, int mouseY) {
        // List background
        drawRect(this.packListX, this.packListY, this.packListX + this.packListW, this.packListY + this.packListH, 0xFF0D1218);
        drawRect(this.packListX, this.packListY, this.packListX + this.packListW, this.packListY + 1, 0xFF3A5160);
        drawRect(this.packListX, this.packListY + this.packListH - 1, this.packListX + this.packListW, this.packListY + this.packListH, 0xFF3A5160);

        if (this.packs == null || this.packs.isEmpty()) {
            String empty = tr("gui.ayanamicosmetics.no_packs", "No packs in resourcepacks/");
            int w = this.fontRenderer.getStringWidth(empty);
            this.fontRenderer.drawString(empty, this.packListX + (this.packListW - w) / 2, this.packListY + this.packListH / 2 - 4, 0xFFFF8A8A, false);
            return;
        }

        int end = Math.min(this.packs.size(), this.scrollOffset + PACK_VISIBLE_ROWS);
        for (int i = this.scrollOffset; i < end; i++) {
            int row = i - this.scrollOffset;
            int y = this.packListY + 2 + row * PACK_ROW_HEIGHT;
            boolean selected = this.packs.get(i).equalsIgnoreCase(Config.getSelectedPackName());
            boolean hovered = i == this.hoveredPackIndex;

            int rowRight = this.packListX + this.packListW - 16;
            if (selected) {
                drawGradientRect(this.packListX + 2, y, rowRight, y + PACK_ROW_HEIGHT - 1, 0xFF2A5A6E, 0xFF1E3F4E);
                drawRect(this.packListX + 2, y, this.packListX + 4, y + PACK_ROW_HEIGHT - 1, 0xFF7AD0EA);
            } else if (hovered) {
                drawRect(this.packListX + 2, y, rowRight, y + PACK_ROW_HEIGHT - 1, 0xFF243040);
            }

            String name = this.packs.get(i);
            int color = selected ? 0xFFE8F7FC : (hovered ? 0xFFD0DEE8 : 0xFFA9B8C4);
            String prefix = selected ? "> " : "  ";
            this.fontRenderer.drawString(prefix + name, this.packListX + 8, y + 5, color, false);
        }

        // Scrollbar
        if (this.packs.size() > PACK_VISIBLE_ROWS) {
            int trackX = this.packListX + this.packListW - 13;
            int trackY = this.packListY + 16;
            int trackH = this.packListH - 32;
            drawRect(trackX, trackY, trackX + 8, trackY + trackH, 0xFF1A222C);

            int maxScroll = this.packs.size() - PACK_VISIBLE_ROWS;
            int thumbH = Math.max(12, trackH * PACK_VISIBLE_ROWS / this.packs.size());
            int thumbY = trackY;
            if (maxScroll > 0) {
                thumbY = trackY + (trackH - thumbH) * this.scrollOffset / maxScroll;
            }
            drawRect(trackX + 1, thumbY, trackX + 7, thumbY + thumbH, 0xFF5EB7D4);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Close with Escape or the same open-menu keybind
        if (keyCode == 1) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (KeyHandler.openGuiKey != null && keyCode == KeyHandler.openGuiKey.getKeyCode()) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
}
