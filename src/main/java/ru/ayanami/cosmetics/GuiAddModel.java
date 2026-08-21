package ru.ayanami.cosmetics;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import ru.ayanami.cosmetics.catalog.CatalogManager;
import ru.ayanami.cosmetics.catalog.ModelApplier;
import ru.ayanami.cosmetics.catalog.ServerPackClone;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Wizard: add custom model/texture that replaces a path from the server (work_pack) RP.
 * Drop files into config/tweakos/inbox/ then fill fields — or type paths absolute.
 */
@SideOnly(Side.CLIENT)
public class GuiAddModel extends GuiScreen {

    private static final int ID_BACK = 1;
    private static final int ID_SAVE = 2;
    private static final int ID_CAT_HATS = 3;
    private static final int ID_CAT_HAND = 4;
    private static final int ID_OPEN_FOLDER = 5;

    private final GuiScreen parent;
    private GuiTextField idField;
    private GuiTextField nameField;
    private GuiTextField replaceField;
    private GuiTextField modelField;
    private GuiTextField textureField;
    private GuiTextField previewField;
    private String category = CatalogManager.CAT_HATS;
    private String status = "";
    private int panelX, panelY, panelW, panelH;

    public GuiAddModel(GuiScreen parent) {
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
        CatalogManager.initFolders();
        ensureInbox();

        this.panelW = Math.min(420, this.width - 16);
        this.panelH = Math.min(250, this.height - 16);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int fx = this.panelX + 120;
        int fw = this.panelW - 130;
        this.idField = field(0, fx, this.panelY + 28, fw);
        this.nameField = field(1, fx, this.panelY + 48, fw);
        this.replaceField = field(2, fx, this.panelY + 68, fw);
        this.modelField = field(3, fx, this.panelY + 88, fw);
        this.textureField = field(4, fx, this.panelY + 108, fw);
        this.previewField = field(5, fx, this.panelY + 128, fw);

        this.idField.setText("my_model");
        this.nameField.setText("My Model");
        this.replaceField.setText("assets/minecraft/models/item/diamond_helmet.json");
        this.modelField.setText(inboxHint("model.json"));
        this.textureField.setText(inboxHint("texture.png"));
        this.previewField.setText(inboxHint("preview.png"));

        this.buttonList.add(new GuiStyledButton(ID_CAT_HATS, this.panelX + 10, this.panelY + 150, 70, 16,
                tr("gui.tweakos.cat_hats", "Hats"),
                CatalogManager.CAT_HATS.equals(this.category) ? GuiStyledButton.Style.CHIP_ACTIVE : GuiStyledButton.Style.CHIP));
        this.buttonList.add(new GuiStyledButton(ID_CAT_HAND, this.panelX + 84, this.panelY + 150, 90, 16,
                tr("gui.tweakos.cat_hand", "Hand"),
                CatalogManager.CAT_HAND.equals(this.category) ? GuiStyledButton.Style.CHIP_ACTIVE : GuiStyledButton.Style.CHIP));
        this.buttonList.add(new GuiStyledButton(ID_OPEN_FOLDER, this.panelX + 180, this.panelY + 150, 90, 16,
                tr("gui.tweakos.open_inbox", "Open inbox"), GuiStyledButton.Style.SECONDARY));

        int by = this.panelY + this.panelH - 24;
        this.buttonList.add(new GuiStyledButton(ID_SAVE, this.panelX + this.panelW - 120, by, 55, 16,
                tr("gui.tweakos.apply", "Save"), GuiStyledButton.Style.PRIMARY));
        this.buttonList.add(new GuiStyledButton(ID_BACK, this.panelX + this.panelW - 60, by, 50, 16,
                tr("gui.tweakos.done", "Back"), GuiStyledButton.Style.SECONDARY));
    }

    private GuiTextField field(int id, int x, int y, int w) {
        GuiTextField f = new GuiTextField(id, this.fontRenderer, x, y, w, 14);
        f.setMaxStringLength(260);
        f.setEnableBackgroundDrawing(true);
        f.setTextColor(0xFFE8EAF0);
        return f;
    }

    private File inboxDir() {
        return new File(CatalogManager.getModConfigDir(), "inbox");
    }

    private void ensureInbox() {
        File d = inboxDir();
        if (!d.exists()) {
            d.mkdirs();
        }
    }

    private String inboxHint(String file) {
        return new File(inboxDir(), file).getAbsolutePath();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        this.idField.updateCursorCounter();
        this.nameField.updateCursorCounter();
        this.replaceField.updateCursorCounter();
        this.modelField.updateCursorCounter();
        this.textureField.updateCursorCounter();
        this.previewField.updateCursorCounter();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_BACK) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        if (button.id == ID_CAT_HATS) {
            this.category = CatalogManager.CAT_HATS;
            this.initGui();
            return;
        }
        if (button.id == ID_CAT_HAND) {
            this.category = CatalogManager.CAT_HAND;
            this.initGui();
            return;
        }
        if (button.id == ID_OPEN_FOLDER) {
            ensureInbox();
            try {
                java.awt.Desktop.getDesktop().open(inboxDir());
            } catch (Exception e) {
                this.status = inboxDir().getAbsolutePath();
            }
            return;
        }
        if (button.id == ID_SAVE) {
            saveEntry();
        }
    }

    private void saveEntry() {
        String id = this.idField.getText().trim().toLowerCase(Locale.ROOT);
        String name = this.nameField.getText().trim();
        String replace = this.replaceField.getText().trim();
        File model = pathOrNull(this.modelField.getText());
        File tex = pathOrNull(this.textureField.getText());
        File preview = pathOrNull(this.previewField.getText());
        if (id.isEmpty() || replace.isEmpty()) {
            this.status = tr("gui.tweakos.add_need_fields", "Need id + replacePath");
            return;
        }
        ServerPackClone.ensureClonedFromServer();
        boolean ok = CatalogManager.createUserEntry(id, name.isEmpty() ? id : name, this.category, replace,
                model, tex, preview, "#4A7CFF");
        if (!ok) {
            this.status = tr("gui.tweakos.add_failed", "Failed (id exists?)");
            return;
        }
        CatalogManager.reload();
        for (CatalogManager.CatalogEntry e : CatalogManager.getEntries()) {
            if (e.id.equals(id)) {
                ModelApplier.apply(e);
                break;
            }
        }
        this.status = tr("gui.tweakos.add_ok", "Saved to catalog + work_pack");
        this.mc.displayGuiScreen(this.parent);
    }

    private File pathOrNull(String p) {
        if (p == null || p.trim().isEmpty()) {
            return null;
        }
        File f = new File(p.trim());
        return f.isFile() ? f : null;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        this.idField.textboxKeyTyped(typedChar, keyCode);
        this.nameField.textboxKeyTyped(typedChar, keyCode);
        this.replaceField.textboxKeyTyped(typedChar, keyCode);
        this.modelField.textboxKeyTyped(typedChar, keyCode);
        this.textureField.textboxKeyTyped(typedChar, keyCode);
        this.previewField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.idField.mouseClicked(mouseX, mouseY, mouseButton);
        this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
        this.replaceField.mouseClicked(mouseX, mouseY, mouseButton);
        this.modelField.mouseClicked(mouseX, mouseY, mouseButton);
        this.textureField.mouseClicked(mouseX, mouseY, mouseButton);
        this.previewField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(0, 0, this.width, this.height, 0xC0101014);
        drawRect(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, 0xF016161C);

        this.fontRenderer.drawString(tr("gui.tweakos.add_model", "Add model"), this.panelX + 12, this.panelY + 10, 0xFFF2F4FA, false);
        label("ID", this.panelY + 30);
        label(tr("gui.tweakos.name", "Name"), this.panelY + 50);
        label("replacePath", this.panelY + 70);
        label("model.json", this.panelY + 90);
        label("texture.png", this.panelY + 110);
        label("preview.png", this.panelY + 130);

        this.idField.drawTextBox();
        this.nameField.drawTextBox();
        this.replaceField.drawTextBox();
        this.modelField.drawTextBox();
        this.textureField.drawTextBox();
        this.previewField.drawTextBox();

        this.fontRenderer.drawString(tr("gui.tweakos.add_hint", "Put files into config/tweakos/inbox/"), this.panelX + 12, this.panelY + 172, 0xFF8B8E98, false);
        if (this.status != null && !this.status.isEmpty()) {
            this.fontRenderer.drawString(this.status, this.panelX + 12, this.panelY + this.panelH - 40, 0xFF8DFFB0, false);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void label(String t, int y) {
        this.fontRenderer.drawString(t, this.panelX + 12, y, 0xFFB0B3BE, false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
