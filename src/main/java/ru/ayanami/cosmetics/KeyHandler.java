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

    public static void init() {
        openGuiKey = new KeyBinding(
                "key.ayanamicosmetics.opengui",
                Keyboard.KEY_O,
                "key.categories.ayanamicosmetics"
        );
        ClientRegistry.registerKeyBinding(openGuiKey);
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen != null) {
            return;
        }
        if (openGuiKey != null && openGuiKey.isPressed()) {
            mc.displayGuiScreen(new GuiAyanamiCosmetics(mc.currentScreen));
        }
    }
}
