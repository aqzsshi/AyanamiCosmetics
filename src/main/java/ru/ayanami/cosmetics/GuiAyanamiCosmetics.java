package ru.ayanami.cosmetics;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiAyanamiCosmetics extends GuiScreen {

    private static final int ID_TOGGLE = 1;
    private static final int ID_SELECT = 2;
    private static final int ID_RELOAD = 3;
    private static final int ID_DONE = 4;

    private final GuiScreen parent;

    public GuiAyanamiCosmetics(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int centerX = this.width / 2;
        int startY = this.height / 4 + 24;

        String toggleLabel = Config.isOverrideEnabled()
                ? I18n.format("gui.ayanamicosmetics.disable")
                : I18n.format("gui.ayanamicosmetics.enable");

        this.buttonList.add(new GuiButton(ID_TOGGLE, centerX - 100, startY, 200, 20, toggleLabel));
        this.buttonList.add(new GuiButton(ID_SELECT, centerX - 100, startY + 48, 200, 20, I18n.format("gui.ayanamicosmetics.select_pack")));
        this.buttonList.add(new GuiButton(ID_RELOAD, centerX - 100, startY + 72, 200, 20, I18n.format("gui.ayanamicosmetics.reload")));
        this.buttonList.add(new GuiButton(ID_DONE, centerX - 100, startY + 120, 200, 20, I18n.format("gui.ayanamicosmetics.done")));
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
            case ID_SELECT:
                this.mc.displayGuiScreen(new GuiPackSelector(this));
                break;
            case ID_RELOAD:
                ResourcePackManager.reloadResourcesFromGui();
                break;
            case ID_DONE:
                this.mc.displayGuiScreen(this.parent);
                break;
            default:
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer, I18n.format("gui.ayanamicosmetics.title"), this.width / 2, 20, 0xFFFFFF);

        int centerX = this.width / 2;
        int infoY = this.height / 4;

        String packName = Config.getSelectedPackName();
        if (packName == null || packName.isEmpty()) {
            packName = I18n.format("gui.ayanamicosmetics.pack_missing");
        }

        this.drawCenteredString(
                this.fontRenderer,
                I18n.format("gui.ayanamicosmetics.current_pack") + " " + packName,
                centerX,
                infoY,
                0xA0A0A0
        );

        String overrideStatus = Config.isOverrideEnabled()
                ? I18n.format("gui.ayanamicosmetics.override_on")
                : I18n.format("gui.ayanamicosmetics.override_off");
        String serverStatus = ResourcePackManager.isServerResourcePackLoaded()
                ? I18n.format("gui.ayanamicosmetics.server_loaded")
                : I18n.format("gui.ayanamicosmetics.server_not_loaded");

        int statusY = this.height / 4 + 100;
        this.drawCenteredString(this.fontRenderer, I18n.format("gui.ayanamicosmetics.status"), centerX, statusY, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, overrideStatus, centerX, statusY + 12, Config.isOverrideEnabled() ? 0x55FF55 : 0xFF5555);
        this.drawCenteredString(this.fontRenderer, serverStatus, centerX, statusY + 24, ResourcePackManager.isServerResourcePackLoaded() ? 0x55FF55 : 0xFFAA00);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    public static class GuiPackSelector extends GuiScreen {

        private static final int ID_BACK = 0;
        private static final int ID_PACK_BASE = 100;

        private final GuiAyanamiCosmetics parent;
        private List<String> packs;

        public GuiPackSelector(GuiAyanamiCosmetics parent) {
            this.parent = parent;
        }

        @Override
        public void initGui() {
            this.buttonList.clear();
            this.packs = ResourcePackManager.listAvailablePackNames();

            int centerX = this.width / 2;
            int y = 40;
            int maxVisible = Math.max(1, (this.height - 80) / 24);
            int count = Math.min(this.packs.size(), maxVisible);

            for (int i = 0; i < count; i++) {
                String name = this.packs.get(i);
                GuiButton button = new GuiButton(ID_PACK_BASE + i, centerX - 120, y + i * 24, 240, 20, name);
                if (name.equals(Config.getSelectedPackName())) {
                    button.displayString = "> " + name + " <";
                }
                this.buttonList.add(button);
            }

            if (this.packs.isEmpty()) {
                // No clickable packs; back only.
            }

            this.buttonList.add(new GuiButton(ID_BACK, centerX - 100, this.height - 28, 200, 20, I18n.format("gui.ayanamicosmetics.done")));
        }

        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            if (button.id == ID_BACK) {
                this.mc.displayGuiScreen(this.parent);
                return;
            }
            if (button.id >= ID_PACK_BASE && this.packs != null) {
                int index = button.id - ID_PACK_BASE;
                if (index >= 0 && index < this.packs.size()) {
                    ResourcePackManager.selectPack(this.packs.get(index));
                    this.mc.displayGuiScreen(this.parent);
                }
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            this.drawDefaultBackground();
            this.drawCenteredString(this.fontRenderer, I18n.format("gui.ayanamicosmetics.pack_select_title"), this.width / 2, 15, 0xFFFFFF);
            if (this.packs == null || this.packs.isEmpty()) {
                this.drawCenteredString(this.fontRenderer, I18n.format("gui.ayanamicosmetics.no_packs"), this.width / 2, this.height / 2, 0xFF5555);
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
