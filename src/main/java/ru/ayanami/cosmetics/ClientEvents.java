package ru.ayanami.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientEvents {

    private int tickCounter;
    private int profileDelayTicks;

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

        if (profileDelayTicks > 0) {
            profileDelayTicks--;
            if (profileDelayTicks == 0) {
                ResourcePackManager.applyServerProfileIfAny();
            }
        }

        tickCounter++;
        if (tickCounter < 10) {
            return;
        }
        tickCounter = 0;
        ResourcePackManager.syncOverrideState(true);
    }

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        TweakOs.LOGGER.info("[TweakOs] Client connected; waiting for server resource pack if any");
        // Delay so ServerData / server RP handshake can settle.
        profileDelayTicks = 40;
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ResourcePackManager.ensureSelectedPackExists();
                ResourcePackManager.applyServerProfileIfAny();
                ResourcePackManager.syncOverrideState(true);
            }
        });
    }

    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ResourcePackManager.onClientDisconnect();
        profileDelayTicks = 0;
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
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
