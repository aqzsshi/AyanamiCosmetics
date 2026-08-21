package ru.ayanami.cosmetics;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.ayanami.cosmetics.catalog.CatalogManager;
import ru.ayanami.cosmetics.update.UpdateManager;

import java.io.File;

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
    public static final String VERSION = "1.2.1";

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
        CatalogManager.initFolders();
        UpdateManager.loadLocalVersions();
        KeyHandler.init();
        ClientEvents.init();
        InventoryCosmeticsButton.init();

        // Background update check (free GitHub hosting).
        UpdateManager.checkAndUpdateAsync(new Runnable() {
            @Override
            public void run() {
                CatalogManager.reload();
            }
        });
    }
}
