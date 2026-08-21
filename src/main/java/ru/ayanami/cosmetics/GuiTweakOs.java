package ru.ayanami.cosmetics;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dark card-grid menu inspired by modern pack browsers:
 * search + filters on top, pack cards on the left, preview/details on the right.
 */
@SideOnly(Side.CLIENT)
public class GuiTweakOs extends GuiScreen {

    private static final int ID_TOGGLE = 1;
    private static final int ID_RELOAD = 2;
    private static final int ID_KEYBINDS = 3;
    private static final int ID_DONE = 4;
    private static final int ID_APPLY = 5;
    private static final int ID_ADD_STACK = 6;
    private static final int ID_REMOVE_STACK = 7;
    private static final int ID_FAVORITE = 8;
    private static final int ID_MOVE_UP = 9;
    private static final int ID_MOVE_DOWN = 13;
    private static final int ID_SAVE_SERVER = 14;
    private static final int ID_FILTER_ALL = 10;
    private static final int ID_FILTER_ZIP = 11;
    private static final int ID_FILTER_FOLDER = 12;

    private static final int CARD_W = 92;
    private static final int CARD_H = 98;
    private static final int CARD_GAP = 8;
    private static final int GRID_COLS = 3;

    private enum Filter {
        ALL, ZIP, FOLDER
    }

    private final GuiScreen parent;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;

    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    private GuiTextField searchField;
    private Filter filter = Filter.ALL;

    private List<String> allPacks = new ArrayList<String>();
    private List<String> filteredPacks = new ArrayList<String>();
    private int scrollOffset;
    private int hoveredCard = -1;
    private String previewPackName = "";

    private DynamicTexture previewTexture;
    private ResourceLocation previewLocation;
    private String previewTexturePack;

    public GuiTweakOs(GuiScreen parent) {
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
        Keyboard.enableRepeatEvents(true);

        ResourcePackManager.ensureSelectedPackExists();
        this.allPacks = ResourcePackManager.listAvailablePackNames();
        if (this.previewPackName == null || this.previewPackName.isEmpty()) {
            this.previewPackName = Config.getSelectedPackName();
        }
        if ((this.previewPackName == null || this.previewPackName.isEmpty()) && !this.allPacks.isEmpty()) {
            this.previewPackName = this.allPacks.get(0);
        }

        this.panelW = Math.min(460, this.width - 20);
        this.panelH = Math.min(260, this.height - 20);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        this.previewW = 150;
        this.previewH = this.panelH - 70;
        this.previewX = this.panelX + this.panelW - this.previewW - 12;
        this.previewY = this.panelY + 58;

        this.gridX = this.panelX + 12;
        this.gridY = this.panelY + 58;
        this.gridW = this.previewX - this.gridX - 10;
        this.gridH = this.panelH - 100;

        this.searchField = new GuiTextField(0, this.fontRenderer, this.panelX + 12, this.panelY + 12, this.panelW - 24, 16);
        this.searchField.setMaxStringLength(64);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setTextColor(0xFFE8EAF0);
        if (this.searchField.getText() == null) {
            this.searchField.setText("");
        }

        int chipY = this.panelY + 34;
        this.buttonList.add(new GuiStyledButton(ID_FILTER_ALL, this.panelX + 12, chipY, 36, 14, "All", GuiStyledButton.Style.CHIP));
        this.buttonList.add(new GuiStyledButton(ID_FILTER_ZIP, this.panelX + 52, chipY, 36, 14, "ZIP", GuiStyledButton.Style.CHIP));
        this.buttonList.add(new GuiStyledButton(ID_FILTER_FOLDER, this.panelX + 92, chipY, 50, 14, "Folder", GuiStyledButton.Style.CHIP));

        int btnY = this.panelY + this.panelH - 28;
        boolean on = Config.isOverrideEnabled();
        this.buttonList.add(new GuiStyledButton(
                ID_TOGGLE,
                this.panelX + 12,
                btnY,
                78,
                18,
                on ? tr("gui.tweakos.disable", "Override OFF") : tr("gui.tweakos.enable", "Override ON"),
                on ? GuiStyledButton.Style.DANGER : GuiStyledButton.Style.PRIMARY
        ));
        this.buttonList.add(new GuiStyledButton(ID_SAVE_SERVER, this.panelX + 96, btnY, 78, 18, tr("gui.tweakos.save_server", "Save IP"), GuiStyledButton.Style.PRIMARY));
        this.buttonList.add(new GuiStyledButton(ID_RELOAD, this.panelX + 180, btnY, 54, 18, tr("gui.tweakos.reload_short", "Reload"), GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_KEYBINDS, this.panelX + 240, btnY, 50, 18, tr("gui.tweakos.keybinds", "Keys"), GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_DONE, this.panelX + this.panelW - 62, btnY, 50, 18, tr("gui.tweakos.done", "Done"), GuiStyledButton.Style.SECONDARY));

        // Preview action buttons
        int px = this.previewX + 8;
        int py = this.previewY + this.previewH - 52;
        this.buttonList.add(new GuiStyledButton(ID_ADD_STACK, px, py, 64, 14, tr("gui.tweakos.add_stack", "+ Stack"), GuiStyledButton.Style.PRIMARY));
        this.buttonList.add(new GuiStyledButton(ID_REMOVE_STACK, px + 68, py, 64, 14, tr("gui.tweakos.rem_stack", "- Stack"), GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_FAVORITE, px, py + 16, 42, 14, tr("gui.tweakos.favorite", "Star"), GuiStyledButton.Style.CHIP));
        this.buttonList.add(new GuiStyledButton(ID_MOVE_UP, px + 46, py + 16, 28, 14, "Up", GuiStyledButton.Style.CHIP));
        this.buttonList.add(new GuiStyledButton(ID_MOVE_DOWN, px + 78, py + 16, 28, 14, "Dn", GuiStyledButton.Style.CHIP));
        this.buttonList.add(new GuiStyledButton(ID_APPLY, px + 110, py + 16, 32, 14, "OK", GuiStyledButton.Style.PRIMARY));

        refreshFilterChips();
        rebuildFilteredList();
        refreshPreviewTexture();
    }

    private void refreshFilterChips() {
        for (GuiButton button : this.buttonList) {
            if (!(button instanceof GuiStyledButton)) {
                continue;
            }
            GuiStyledButton styled = (GuiStyledButton) button;
            if (button.id == ID_FILTER_ALL) {
                styled.setStyle(this.filter == Filter.ALL ? GuiStyledButton.Style.CHIP_ACTIVE : GuiStyledButton.Style.CHIP);
            } else if (button.id == ID_FILTER_ZIP) {
                styled.setStyle(this.filter == Filter.ZIP ? GuiStyledButton.Style.CHIP_ACTIVE : GuiStyledButton.Style.CHIP);
            } else if (button.id == ID_FILTER_FOLDER) {
                styled.setStyle(this.filter == Filter.FOLDER ? GuiStyledButton.Style.CHIP_ACTIVE : GuiStyledButton.Style.CHIP);
            }
        }
    }

    private void rebuildFilteredList() {
        String query = this.searchField != null ? this.searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        this.filteredPacks = new ArrayList<String>();
        for (int i = 0; i < this.allPacks.size(); i++) {
            String name = this.allPacks.get(i);
            File file = ResourcePackManager.resolvePackFileByName(name);
            if (file == null) {
                continue;
            }
            boolean isZip = file.isFile();
            if (this.filter == Filter.ZIP && !isZip) {
                continue;
            }
            if (this.filter == Filter.FOLDER && isZip) {
                continue;
            }
            if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            this.filteredPacks.add(name);
        }
        int maxScroll = Math.max(0, rowsCount() - visibleRows());
        this.scrollOffset = Math.min(this.scrollOffset, maxScroll);
    }

    private int rowsCount() {
        if (this.filteredPacks.isEmpty()) {
            return 0;
        }
        return (this.filteredPacks.size() + GRID_COLS - 1) / GRID_COLS;
    }

    private int visibleRows() {
        return Math.max(1, this.gridH / (CARD_H + CARD_GAP));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        clearPreviewTexture();
        super.onGuiClosed();
    }

    private void clearPreviewTexture() {
        if (this.previewLocation != null) {
            try {
                this.mc.getTextureManager().deleteTexture(this.previewLocation);
            } catch (Exception ignored) {
            }
        }
        this.previewTexture = null;
        this.previewLocation = null;
        this.previewTexturePack = null;
    }

    private void refreshPreviewTexture() {
        if (this.previewPackName == null || this.previewPackName.isEmpty()) {
            clearPreviewTexture();
            return;
        }
        if (this.previewPackName.equals(this.previewTexturePack) && this.previewLocation != null) {
            return;
        }
        clearPreviewTexture();
        try {
            IResourcePack pack = ResourcePackManager.createPackInstance(this.previewPackName);
            if (pack == null) {
                return;
            }
            BufferedImage image = pack.getPackImage();
            if (image == null) {
                return;
            }
            this.previewTexture = new DynamicTexture(image);
            this.previewLocation = this.mc.getTextureManager().getDynamicTextureLocation("ayanami_pack_preview", this.previewTexture);
            this.previewTexturePack = this.previewPackName;
        } catch (Exception e) {
            clearPreviewTexture();
        }
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
            case ID_APPLY:
                if (this.previewPackName != null && !this.previewPackName.isEmpty()) {
                    ResourcePackManager.selectPack(this.previewPackName);
                    if (!Config.isOverrideEnabled()) {
                        ResourcePackManager.setOverrideEnabled(true);
                    } else {
                        ResourcePackManager.reloadResourcesFromGui();
                    }
                }
                this.initGui();
                break;
            case ID_ADD_STACK:
                if (this.previewPackName != null && !this.previewPackName.isEmpty()) {
                    ResourcePackManager.addPackToStack(this.previewPackName);
                }
                this.initGui();
                break;
            case ID_REMOVE_STACK:
                if (this.previewPackName != null && !this.previewPackName.isEmpty()) {
                    ResourcePackManager.removePackFromStack(this.previewPackName);
                }
                this.initGui();
                break;
            case ID_FAVORITE:
                if (this.previewPackName != null && !this.previewPackName.isEmpty()) {
                    Config.toggleFavorite(this.previewPackName);
                    this.allPacks = ResourcePackManager.listAvailablePackNames();
                    rebuildFilteredList();
                }
                this.initGui();
                break;
            case ID_MOVE_UP:
                if (this.previewPackName != null) {
                    Config.moveActivePack(this.previewPackName, -1);
                    ResourcePackManager.reloadResourcesFromGui();
                }
                this.initGui();
                break;
            case ID_MOVE_DOWN:
                if (this.previewPackName != null) {
                    Config.moveActivePack(this.previewPackName, 1);
                    ResourcePackManager.reloadResourcesFromGui();
                }
                this.initGui();
                break;
            case ID_SAVE_SERVER:
                ResourcePackManager.saveProfileForCurrentServer();
                break;
            case ID_RELOAD:
                ResourcePackManager.reloadResourcesFromGui();
                this.allPacks = ResourcePackManager.listAvailablePackNames();
                rebuildFilteredList();
                refreshPreviewTexture();
                break;
            case ID_KEYBINDS:
                this.mc.displayGuiScreen(new GuiControls(this, this.mc.gameSettings));
                break;
            case ID_DONE:
                this.mc.displayGuiScreen(this.parent);
                break;
            case ID_FILTER_ALL:
                this.filter = Filter.ALL;
                this.scrollOffset = 0;
                refreshFilterChips();
                rebuildFilteredList();
                break;
            case ID_FILTER_ZIP:
                this.filter = Filter.ZIP;
                this.scrollOffset = 0;
                refreshFilterChips();
                rebuildFilteredList();
                break;
            case ID_FILTER_FOLDER:
                this.filter = Filter.FOLDER;
                this.scrollOffset = 0;
                refreshFilterChips();
                rebuildFilteredList();
                break;
            default:
                break;
        }
    }

    @Override
    public void updateScreen() {
        if (this.searchField != null) {
            this.searchField.updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (KeyHandler.openGuiKey != null && keyCode == KeyHandler.openGuiKey.getKeyCode() && (this.searchField == null || !this.searchField.isFocused())) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (this.searchField != null && this.searchField.textboxKeyTyped(typedChar, keyCode)) {
            this.scrollOffset = 0;
            rebuildFilteredList();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int max = Math.max(0, rowsCount() - visibleRows());
            if (wheel > 0) {
                this.scrollOffset = Math.max(0, this.scrollOffset - 1);
            } else {
                this.scrollOffset = Math.min(max, this.scrollOffset + 1);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.searchField != null) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (mouseButton == 0) {
            int index = getCardIndexAt(mouseX, mouseY);
            if (index >= 0 && index < this.filteredPacks.size()) {
                this.previewPackName = this.filteredPacks.get(index);
                refreshPreviewTexture();
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private int getCardIndexAt(int mouseX, int mouseY) {
        if (mouseX < this.gridX || mouseY < this.gridY || mouseX >= this.gridX + this.gridW || mouseY >= this.gridY + this.gridH) {
            return -1;
        }
        int localX = mouseX - this.gridX;
        int localY = mouseY - this.gridY;
        int col = localX / (CARD_W + CARD_GAP);
        int row = localY / (CARD_H + CARD_GAP);
        if (col < 0 || col >= GRID_COLS) {
            return -1;
        }
        int cardX = col * (CARD_W + CARD_GAP);
        int cardY = row * (CARD_H + CARD_GAP);
        if (localX < cardX || localX >= cardX + CARD_W || localY < cardY || localY >= cardY + CARD_H) {
            return -1;
        }
        return (this.scrollOffset + row) * GRID_COLS + col;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.hoveredCard = getCardIndexAt(mouseX, mouseY);

        // Dim backdrop
        drawRect(0, 0, this.width, this.height, 0xA008080C);

        // Main panel
        drawSoftPanel(this.panelX, this.panelY, this.panelW, this.panelH, 0xF0141418, 0xFF2A2A32);

        // Title
        this.fontRenderer.drawString(tr("gui.tweakos.title", "TweakOs"), this.panelX + 14, this.panelY - 12, 0xFFE8EAF0, false);
        String author = tr("gui.tweakos.author", "Created by AyanamiKaede");
        this.fontRenderer.drawString(author, this.panelX + 14 + this.fontRenderer.getStringWidth(tr("gui.tweakos.title", "TweakOs")) + 8, this.panelY - 12, 0xFF6E7180, false);
        String keyName = KeyHandler.openGuiKey != null ? KeyHandler.openGuiKey.getDisplayName() : "O";
        String keyHint = tr("gui.tweakos.key_hint", "Menu key:") + " " + keyName;
        this.fontRenderer.drawString(keyHint, this.panelX + this.panelW - 8 - this.fontRenderer.getStringWidth(keyHint), this.panelY - 12, 0xFF8B8E98, false);

        // Search field background
        drawSoftPanel(this.panelX + 10, this.panelY + 10, this.panelW - 20, 20, 0xFF1C1C22, 0xFF3A3A44);
        String placeholder = tr("gui.tweakos.search", "Search packs...");
        if (this.searchField != null) {
            if (this.searchField.getText().isEmpty() && !this.searchField.isFocused()) {
                this.fontRenderer.drawString(placeholder, this.panelX + 16, this.panelY + 16, 0xFF6E7180, false);
            }
            this.searchField.drawTextBox();
        }

        drawCardGrid();
        drawPreviewPanel();

        // Status line under filters
        boolean applied = ResourcePackManager.isOverrideApplied();
        boolean server = ResourcePackManager.isServerResourcePackLoaded();
        String status = (Config.isOverrideEnabled() ? "Override ON" : "Override OFF")
                + "  ·  "
                + (server ? "Server RP" : "No Server RP")
                + "  ·  "
                + (applied ? "Applied" : "Not applied");
        this.fontRenderer.drawString(status, this.panelX + 150, this.panelY + 37, applied ? 0xFF8DFFB0 : 0xFFB0B3BE, false);

        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawCardGrid() {
        drawSoftPanel(this.gridX - 2, this.gridY - 2, this.gridW + 4, this.gridH + 4, 0xFF101014, 0xFF2C2C34);

        if (this.filteredPacks.isEmpty()) {
            String empty = tr("gui.tweakos.no_packs", "Put any ZIP or folder into resourcepacks/");
            int w = this.fontRenderer.getStringWidth(empty);
            this.fontRenderer.drawString(empty, this.gridX + (this.gridW - w) / 2, this.gridY + this.gridH / 2 - 4, 0xFFFF8E8E, false);
            return;
        }

        int startRow = this.scrollOffset;
        int endRow = Math.min(rowsCount(), startRow + visibleRows());
        for (int row = startRow; row < endRow; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int index = row * GRID_COLS + col;
                if (index >= this.filteredPacks.size()) {
                    break;
                }
                int x = this.gridX + col * (CARD_W + CARD_GAP);
                int y = this.gridY + (row - startRow) * (CARD_H + CARD_GAP);
                drawPackCard(x, y, this.filteredPacks.get(index), index == this.hoveredCard);
            }
        }

        // Scrollbar
        if (rowsCount() > visibleRows()) {
            int trackX = this.gridX + this.gridW - 5;
            int trackY = this.gridY + 4;
            int trackH = this.gridH - 8;
            drawRect(trackX, trackY, trackX + 3, trackY + trackH, 0xFF2A2A30);
            int max = Math.max(1, rowsCount() - visibleRows());
            int thumbH = Math.max(14, trackH * visibleRows() / rowsCount());
            int thumbY = trackY + (trackH - thumbH) * this.scrollOffset / max;
            drawRect(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF6F98FF);
        }
    }

    private void drawPackCard(int x, int y, String name, boolean hovered) {
        boolean selected = name.equalsIgnoreCase(Config.getSelectedPackName()) || Config.isActivePack(name);
        boolean previewing = name.equalsIgnoreCase(this.previewPackName);
        boolean favorite = Config.isFavorite(name);
        int border;
        if (selected) {
            border = 0xFF6F98FF;
        } else if (previewing || hovered) {
            border = 0xFF8A8A98;
        } else {
            border = 0xFF3A3A44;
        }

        drawSoftPanel(x, y, CARD_W, CARD_H, 0xFF1A1A20, border);

        drawRect(x + 8, y + 8, x + CARD_W - 8, y + 62, 0xFF0E0E12);
        File file = ResourcePackManager.resolvePackFileByName(name);
        String badge = (file != null && file.isDirectory()) ? "DIR" : "ZIP";
        drawRect(x + CARD_W - 28, y + 10, x + CARD_W - 10, y + 20, selected ? 0xFF355FD4 : 0xFF2F2F38);
        this.fontRenderer.drawString(badge, x + CARD_W - 26, y + 12, 0xFFD0D4DE, false);
        if (favorite) {
            this.fontRenderer.drawString("*", x + 10, y + 10, 0xFFFFD76A, false);
        }

        // Mini pack icon if this is the preview pack and texture exists
        if (previewing && this.previewLocation != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.mc.getTextureManager().bindTexture(this.previewLocation);
            drawModalRectWithCustomSizedTexture(x + 22, y + 16, 0, 0, 48, 40, 48, 40);
        } else {
            this.fontRenderer.drawString("RP", x + CARD_W / 2 - 6, y + 30, 0xFF5A5A68, false);
        }

        String label = name;
        if (this.fontRenderer.getStringWidth(label) > CARD_W - 10) {
            while (label.length() > 3 && this.fontRenderer.getStringWidth(label + "..") > CARD_W - 10) {
                label = label.substring(0, label.length() - 1);
            }
            label = label + "..";
        }
        int color = selected ? 0xFFB8CCFF : 0xFFE6E8F0;
        this.fontRenderer.drawString(label, x + 6, y + CARD_H - 14, color, false);
    }

    private void drawPreviewPanel() {
        drawSoftPanel(this.previewX, this.previewY, this.previewW, this.previewH, 0xFF121218, 0xFF34343E);

        String title = this.previewPackName == null || this.previewPackName.isEmpty()
                ? tr("gui.tweakos.pack_missing", "<not selected>")
                : this.previewPackName;
        this.fontRenderer.drawString(tr("gui.tweakos.preview", "Preview"), this.previewX + 8, this.previewY + 6, 0xFF8B8E98, false);

        drawRect(this.previewX + 28, this.previewY + 18, this.previewX + this.previewW - 28, this.previewY + 70, 0xFF0C0C10);
        if (this.previewLocation != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.mc.getTextureManager().bindTexture(this.previewLocation);
            drawModalRectWithCustomSizedTexture(this.previewX + 43, this.previewY + 24, 0, 0, 64, 40, 64, 40);
        } else {
            String noIcon = tr("gui.tweakos.no_icon", "No pack.png");
            int w = this.fontRenderer.getStringWidth(noIcon);
            this.fontRenderer.drawString(noIcon, this.previewX + (this.previewW - w) / 2, this.previewY + 38, 0xFF6A6A76, false);
        }

        String shortTitle = title;
        if (this.fontRenderer.getStringWidth(shortTitle) > this.previewW - 16) {
            while (this.fontRenderer.getStringWidth(shortTitle + "..") > this.previewW - 16 && shortTitle.length() > 4) {
                shortTitle = shortTitle.substring(0, shortTitle.length() - 1);
            }
            shortTitle = shortTitle + "..";
        }
        this.fontRenderer.drawString(shortTitle, this.previewX + 8, this.previewY + 76, 0xFFF0F2F8, false);

        File file = ResourcePackManager.resolvePackFileByName(this.previewPackName);
        String type = file == null ? "?" : (file.isDirectory() ? "Folder" : "ZIP");
        boolean fav = this.previewPackName != null && Config.isFavorite(this.previewPackName);
        this.fontRenderer.drawString(tr("gui.tweakos.type", "Type:") + " " + type + (fav ? " *" : ""), this.previewX + 8, this.previewY + 88, 0xFFA0A4B0, false);

        String host = ResourcePackManager.getCurrentServerHost();
        String hostLine = host == null ? tr("gui.tweakos.no_server", "Not on a server") : ("IP: " + host);
        this.fontRenderer.drawString(hostLine, this.previewX + 8, this.previewY + 100, 0xFF8DFFB0, false);

        java.util.List<String> stack = Config.getActivePacks();
        String stackLabel = tr("gui.tweakos.stack", "Stack:") + " " + stack.size();
        this.fontRenderer.drawString(stackLabel, this.previewX + 8, this.previewY + 112, 0xFFB8CCFF, false);
        int sy = this.previewY + 124;
        for (int i = 0; i < Math.min(stack.size(), 2); i++) {
            String n = (i + 1) + ". " + stack.get(i);
            if (this.fontRenderer.getStringWidth(n) > this.previewW - 16) {
                while (this.fontRenderer.getStringWidth(n + "..") > this.previewW - 16 && n.length() > 4) {
                    n = n.substring(0, n.length() - 1);
                }
                n = n + "..";
            }
            this.fontRenderer.drawString(n, this.previewX + 8, sy + i * 10, 0xFFC8CAD2, false);
        }
    }

    private void drawSoftPanel(int x, int y, int w, int h, int fill, int border) {
        drawRect(x + 1, y, x + w - 1, y + h, border);
        drawRect(x, y + 1, x + w, y + h - 1, border);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        drawRect(x + 2, y + 2, x + w - 2, y + 3, 0x18FFFFFF);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
