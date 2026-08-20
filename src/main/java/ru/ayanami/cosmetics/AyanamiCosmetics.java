package ru.ayanami.cosmetics;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * AyanamiCosmetics — client-only Forge 1.12.2 mod.
 * <p>
 * Provides a local cosmetic resource-pack override that sits logically above the
 * server resource pack inside the vanilla ResourceManager, without render patches
 * and without replacing/removing the server pack.
 */
@Mod(
        modid = AyanamiCosmetics.MODID,
        name = AyanamiCosmetics.NAME,
        version = AyanamiCosmetics.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        clientSideOnly = true,
        acceptableRemoteVersions = "*"
)
public class AyanamiCosmetics {

    public static final String MODID = "ayanamicosmetics";
    public static final String NAME = "AyanamiCosmetics";
    public static final String VERSION = "1.1.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static AyanamiCosmetics instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (event.getSide() != Side.CLIENT) {
            return;
        }
        File configFile = new File(event.getModConfigurationDirectory(), MODID + ".cfg");
        Config.init(configFile);
        LOGGER.info("[AyanamiCosmetics] Mod initialized");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() != Side.CLIENT) {
            return;
        }
        KeyHandler.init();
        ClientEvents.init();
    }
}
