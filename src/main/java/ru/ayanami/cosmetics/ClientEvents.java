package ru.ayanami.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Keeps the override wrapper installed across reconnects, F3+T / F3+A reloads,
 * and server resource-pack re-downloads without blocking vanilla pack confirmation.
 */
@SideOnly(Side.CLIENT)
public class ClientEvents {

    private int tickCounter;

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ResourcePackManager.isApplying()) {
            return;
        }
        // Throttle reflection checks a bit; still responsive after pack changes.
        tickCounter++;
        if (tickCounter < 10) {
            return;
        }
        tickCounter = 0;
        ResourcePackManager.syncOverrideState(true);
    }

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        // Server may send its RP shortly after; sync will pick it up on tick.
        AyanamiCosmetics.LOGGER.info("[AyanamiCosmetics] Client connected; waiting for server resource pack if any");
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ResourcePackManager.onClientDisconnect();
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        // After working-resource-pack / download GUIs close, re-assert override.
        if (event.getGui() == null && Config.isOverrideEnabled()) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ResourcePackManager.syncOverrideState(true);
                }
            });
        }
    }
}
