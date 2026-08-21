package ru.ayanami.cosmetics;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Drag-to-reorder pack priority. Higher = more important.
 * Special rows: work_pack (TweakOS clone), __server__ (server RP).
 */
@SideOnly(Side.CLIENT)
public class GuiPackPriority extends GuiScreen {

    private static final int ID_BACK = 1;
    private static final int ID_UP = 2;
    private static final int ID_DOWN = 3;
    private static final int ID_CLONE = 4;
    private static final int ID_RELOAD = 5;

    private final GuiScreen parent;
    private int panelX, panelY, panelW, panelH;
    private int selected = 0;
    private List<String> order = new ArrayList<String>();

    public GuiPackPriority(GuiScreen parent) {
        this.parent = parent;
    }

    private static String tr(String key, String fb) {
        String v = I18n.format(key);
        return v == null || v.equals(key) ? fb : v;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.panelW = Math.min(340, this.width - 20);
        this.panelH = Math.min(260, this.height - 20);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
        refresh();

        int by = this.panelY + this.panelH - 24;
        this.buttonList.add(new GuiStyledButton(ID_UP, this.panelX + 10, by, 50, 16, "Up", GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_DOWN, this.panelX + 64, by, 50, 16, "Down", GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_CLONE, this.panelX + 118, by, 70, 16, tr("gui.tweakos.clone_rp", "Clone RP"), GuiStyledButton.Style.PRIMARY));
        this.buttonList.add(new GuiStyledButton(ID_RELOAD, this.panelX + 192, by, 55, 16, tr("gui.tweakos.reload_short", "Reload"), GuiStyledButton.Style.SECONDARY));
        this.buttonList.add(new GuiStyledButton(ID_BACK, this.panelX + this.panelW - 58, by, 48, 16, tr("gui.tweakos.done", "Back"), GuiStyledButton.Style.SECONDARY));
    }

    private void refresh() {
        this.order = new ArrayList<String>(Config.getPackPriority());
        // Offer resourcepacks not yet in list (below server by default)
        List<String> available = ResourcePackManager.listAvailablePackNames();
        for (int i = 0; i < available.size(); i++) {
            String n = available.get(i);
            if (!this.order.contains(n)) {
                this.order.add(n);
            }
        }
        if (this.selected >= this.order.size()) {
            this.selected = Math.max(0, this.order.size() - 1);
        }
    }

    private void persist() {
        Config.setPackPriority(this.order);
        ResourcePackManager.ensureOverridePackInStack();
        ResourcePackManager.reloadResourcesFromGui();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_BACK) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (button.id == ID_UP) {
            if (this.selected > 0) {
                String a = this.order.remove(this.selected);
                this.order.add(this.selected - 1, a);
                this.selected--;
                persist();
            }
            return;
        }
        if (button.id == ID_DOWN) {
            if (this.selected < this.order.size() - 1) {
                String a = this.order.remove(this.selected);
                this.order.add(this.selected + 1, a);
                this.selected++;
                persist();
            }
            return;
        }
        if (button.id == ID_CLONE) {
            ru.ayanami.cosmetics.catalog.ServerPackClone.forceCloneFromServer();
            return;
        }
        if (button.id == ID_RELOAD) {
            persist();
            refresh();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int listY = this.panelY + 36;
            int rowH = 16;
            int max = Math.min(this.order.size(), 11);
            for (int i = 0; i < max; i++) {
                int y = listY + i * rowH;
                if (mouseX >= this.panelX + 10 && mouseX <= this.panelX + this.panelW - 10
                        && mouseY >= y && mouseY < y + rowH) {
                    this.selected = i;
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(0, 0, this.width, this.height, 0xC0101014);
        drawRect(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, 0xF016161C);
        drawRect(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + 1, 0xFF3A3A46);
        drawRect(this.panelX, this.panelY + this.panelH - 1, this.panelX + this.panelW, this.panelY + this.panelH, 0xFF3A3A46);

        this.fontRenderer.drawString(tr("gui.tweakos.pack_priority", "Pack priority"), this.panelX + 12, this.panelY + 10, 0xFFF2F4FA, false);
        this.fontRenderer.drawString(tr("gui.tweakos.pack_priority_hint", "Higher = stronger. Move Server up/down."), this.panelX + 12, this.panelY + 22, 0xFF8B8E98, false);

        int listY = this.panelY + 36;
        int max = Math.min(this.order.size(), 11);
        for (int i = 0; i < max; i++) {
            int y = listY + i * 16;
            boolean sel = i == this.selected;
            String name = this.order.get(i);
            String label = displayName(name);
            drawRect(this.panelX + 10, y, this.panelX + this.panelW - 10, y + 15, sel ? 0xFF2A3348 : 0xFF1A1A22);
            int color = Config.SERVER_TOKEN.equals(name) ? 0xFF8DFFB0
                    : Config.WORK_PACK_TOKEN.equals(name) ? 0xFFFFD76A : 0xFFE6E8F0;
            this.fontRenderer.drawString((i + 1) + ". " + label, this.panelX + 14, y + 3, color, false);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String displayName(String name) {
        if (Config.SERVER_TOKEN.equals(name)) {
            return tr("gui.tweakos.server_pack", "Server resource pack");
        }
        if (Config.WORK_PACK_TOKEN.equals(name)) {
            return "TweakOS work_pack";
        }
        return name;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
