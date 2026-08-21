package ru.ayanami.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class KeyHandler {

    public static KeyBinding openGuiKey;
    public static KeyBinding toggleOverrideKey;

    public static void init() {
        openGuiKey = new KeyBinding(
                "key.ayanamicosmetics.opengui",
                Keyboard.KEY_O,
                "key.categories.ayanamicosmetics"
        );
        toggleOverrideKey = new KeyBinding(
                "key.ayanamicosmetics.toggle",
                Keyboard.KEY_P,
                "key.categories.ayanamicosmetics"
        );
        ClientRegistry.registerKeyBinding(openGuiKey);
        ClientRegistry.registerKeyBinding(toggleOverrideKey);
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }

        if (toggleOverrideKey != null && toggleOverrideKey.isPressed() && mc.currentScreen == null) {
            ResourcePackManager.toggleOverride();
            return;
        }

        if (openGuiKey == null || !openGuiKey.isPressed()) {
            return;
        }
        if (mc.currentScreen == null) {
            mc.displayGuiScreen(new GuiCosmeticsMenu(null));
        } else if (mc.currentScreen instanceof GuiCosmeticsMenu) {
            mc.displayGuiScreen(null);
        } else if (mc.currentScreen instanceof GuiAyanamiCosmetics) {
            mc.displayGuiScreen(null);
        }
    }
}
