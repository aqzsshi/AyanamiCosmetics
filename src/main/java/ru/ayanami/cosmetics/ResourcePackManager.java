package ru.ayanami.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Loads the selected local cosmetic ZIP and applies it as a higher-priority layer
 * over the server resource pack via the standard ResourceManager pipeline.
 * <p>
 * In Minecraft 1.12.2 {@code Minecraft.refreshResources()} builds the pack list as:
 * default packs → selected repository entries → server resource pack (last = highest priority).
 * Because the server pack is last, a normal selected pack cannot override it.
 * <p>
 * Reflection is used only to read/write {@code ResourcePackRepository.serverResourcePack}
 * so we can install a {@link ResourcePackOverride} wrapper into that same slot without
 * touching server-pack download confirmation or removing the original server pack.
 */
public final class ResourcePackManager {

    private static final Logger LOGGER = AyanamiCosmetics.LOGGER;

    /**
     * MCP / SRG names for ResourcePackRepository.serverResourcePack (1.12.2).
     * Snapshot mappings use {@code serverResourcePack}; stable_39 often uses {@code resourcePackInstance}.
     */
    private static final String[] SERVER_PACK_FIELD_NAMES = new String[] {
            "serverResourcePack",
            "resourcePackInstance",
            "field_148532_f"
    };

    private static Field serverPackField;
    private static boolean fieldLookupFailed;

    private static IResourcePack originalServerPack;
    private static IResourcePack cachedUserPack;
    private static String cachedUserPackName;
    private static long cachedUserPackModified = Long.MIN_VALUE;

    private static boolean applying;
    private static boolean serverPackSeen;
    private static boolean serverPackLoadedLogged;

    private ResourcePackManager() {
    }

    public static boolean isApplying() {
        return applying;
    }

    public static boolean isServerResourcePackLoaded() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourcePackRepository() == null) {
            return false;
        }
        IResourcePack pack = mc.getResourcePackRepository().getServerResourcePack();
        if (pack == null) {
            return false;
        }
        if (pack instanceof ResourcePackOverride) {
            return ((ResourcePackOverride) pack).getBasePack() != null;
        }
        return true;
    }

    public static File getResourcePacksDirectory() {
        return new File(Minecraft.getMinecraft().mcDataDir, "resourcepacks");
    }

    public static File getSelectedPackFile() {
        return new File(getResourcePacksDirectory(), Config.getSelectedPackName());
    }

    public static List<String> listAvailablePackNames() {
        File dir = getResourcePacksDirectory();
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".zip")) {
                names.add(file.getName());
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Nullable
    public static IResourcePack getActiveUserPack() {
        return loadUserPack(false);
    }

    @Nullable
    public static synchronized IResourcePack loadUserPack(boolean forceReload) {
        File packFile = getSelectedPackFile();
        String name = Config.getSelectedPackName();

        if (!packFile.exists() || !packFile.isFile()) {
            cachedUserPack = null;
            cachedUserPackName = null;
            cachedUserPackModified = Long.MIN_VALUE;
            LOGGER.warn("[AyanamiCosmetics] Failed to load resource pack: file not found ({})", name);
            return null;
        }

        long modified = packFile.lastModified();
        if (!forceReload
                && cachedUserPack != null
                && name.equals(cachedUserPackName)
                && modified == cachedUserPackModified) {
            return cachedUserPack;
        }

        String validationError = validatePackZip(packFile);
        if (validationError != null) {
            cachedUserPack = null;
            cachedUserPackName = null;
            cachedUserPackModified = Long.MIN_VALUE;
            LOGGER.warn("[AyanamiCosmetics] Failed to load resource pack: {}", validationError);
            return null;
        }

        try {
            FileResourcePack pack = new FileResourcePack(packFile);
            // Touch resource domains early to catch structural problems.
            pack.getResourceDomains();
            cachedUserPack = pack;
            cachedUserPackName = name;
            cachedUserPackModified = modified;
            LOGGER.info("[AyanamiCosmetics] User resource pack selected: {}", name);
            return pack;
        } catch (Exception e) {
            cachedUserPack = null;
            cachedUserPackName = null;
            cachedUserPackModified = Long.MIN_VALUE;
            LOGGER.warn("[AyanamiCosmetics] Failed to load resource pack: {}", e.toString());
            return null;
        }
    }

    @Nullable
    private static String validatePackZip(File packFile) {
        ZipFile zip = null;
        try {
            zip = new ZipFile(packFile);
            if (zip.size() <= 0) {
                return "archive is empty (" + packFile.getName() + ")";
            }
            boolean hasAssets = false;
            boolean hasMcmeta = zip.getEntry("pack.mcmeta") != null;
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith("assets/") || entryName.startsWith("/assets/")) {
                    hasAssets = true;
                    break;
                }
            }
            if (!hasAssets && !hasMcmeta) {
                return "invalid structure (missing assets/ and pack.mcmeta) (" + packFile.getName() + ")";
            }
            if (!hasMcmeta) {
                LOGGER.warn("[AyanamiCosmetics] Resource pack {} has no pack.mcmeta; continuing anyway", packFile.getName());
            }
            return null;
        } catch (IOException e) {
            return "corrupted or unreadable zip (" + packFile.getName() + "): " + e.getMessage();
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Ensures override state matches config. Safe to call frequently from the client tick.
     */
    public static synchronized void syncOverrideState(boolean reloadResources) {
        if (applying) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourcePackRepository() == null) {
            return;
        }

        ResourcePackRepository repository = mc.getResourcePackRepository();
        IResourcePack current = repository.getServerResourcePack();

        if (current != null && !(current instanceof ResourcePackOverride)) {
            if (!serverPackSeen) {
                LOGGER.info("[AyanamiCosmetics] Server resource pack detected");
                serverPackSeen = true;
            }
            originalServerPack = current;
            if (!serverPackLoadedLogged) {
                LOGGER.info("[AyanamiCosmetics] Server resource pack loaded");
                serverPackLoadedLogged = true;
            }
        } else if (current instanceof ResourcePackOverride) {
            ResourcePackOverride wrapped = (ResourcePackOverride) current;
            originalServerPack = wrapped.getBasePack();
            serverPackSeen = true;
            if (!serverPackLoadedLogged) {
                LOGGER.info("[AyanamiCosmetics] Server resource pack loaded");
                serverPackLoadedLogged = true;
            }
        } else if (current == null) {
            // Disconnected / cleared. Keep selected user pack config, drop live wrapper state.
            if (originalServerPack != null || serverPackSeen) {
                originalServerPack = null;
                serverPackSeen = false;
                serverPackLoadedLogged = false;
            }
        }

        if (!Config.isOverrideEnabled()) {
            if (current instanceof ResourcePackOverride) {
                restoreOriginalServerPack(repository, reloadResources);
            }
            return;
        }

        IResourcePack userPack = loadUserPack(false);
        if (userPack == null) {
            // Missing/broken override pack: keep vanilla behaviour.
            if (current instanceof ResourcePackOverride) {
                restoreOriginalServerPack(repository, reloadResources);
            }
            return;
        }

        IResourcePack base = unwrapBase(current);
        if (base == null) {
            // No server pack currently — nothing to wrap; vanilla order remains.
            return;
        }

        boolean needsApply = !(current instanceof ResourcePackOverride);
        if (current instanceof ResourcePackOverride) {
            ResourcePackOverride existing = (ResourcePackOverride) current;
            if (existing.getOverridePack() != userPack
                    || existing.getBasePack() != base
                    || !Config.getSelectedPackName().equals(existing.getOverrideName())) {
                needsApply = true;
            }
        }

        if (needsApply) {
            applyOverride(repository, userPack, base, reloadResources);
        }
    }

    private static IResourcePack unwrapBase(@Nullable IResourcePack pack) {
        if (pack == null) {
            return originalServerPack;
        }
        if (pack instanceof ResourcePackOverride) {
            return ((ResourcePackOverride) pack).getBasePack();
        }
        return pack;
    }

    private static void applyOverride(ResourcePackRepository repository, IResourcePack userPack, IResourcePack basePack, boolean reloadResources) {
        ResourcePackOverride wrapper = new ResourcePackOverride(userPack, basePack, Config.getSelectedPackName());
        LOGGER.info("[AyanamiCosmetics] Applying user override");
        if (!setServerPackInstance(repository, wrapper)) {
            LOGGER.warn("[AyanamiCosmetics] Failed to load resource pack: could not install override wrapper via reflection");
            return;
        }
        if (reloadResources) {
            reloadMinecraftResources();
        }
    }

    private static void restoreOriginalServerPack(ResourcePackRepository repository, boolean reloadResources) {
        IResourcePack restore = originalServerPack;
        if (restore instanceof ResourcePackOverride) {
            restore = ((ResourcePackOverride) restore).getBasePack();
        }
        if (!setServerPackInstance(repository, restore)) {
            LOGGER.warn("[AyanamiCosmetics] Failed to restore original server resource pack");
            return;
        }
        if (reloadResources) {
            reloadMinecraftResources();
        }
    }

    public static synchronized void setOverrideEnabled(boolean enabled) {
        Config.setOverrideEnabled(enabled);
        if (enabled) {
            LOGGER.info("[AyanamiCosmetics] Override enabled");
        } else {
            LOGGER.info("[AyanamiCosmetics] Override disabled");
        }
        syncOverrideState(true);
    }

    public static synchronized void selectPack(String packName) {
        Config.setSelectedPackName(packName);
        cachedUserPack = null;
        cachedUserPackName = null;
        cachedUserPackModified = Long.MIN_VALUE;
        LOGGER.info("[AyanamiCosmetics] User resource pack selected: {}", Config.getSelectedPackName());
        if (Config.isOverrideEnabled()) {
            syncOverrideState(true);
        }
    }

    public static synchronized void reloadResourcesFromGui() {
        cachedUserPack = null;
        cachedUserPackName = null;
        cachedUserPackModified = Long.MIN_VALUE;
        loadUserPack(true);
        syncOverrideState(true);
    }

    public static void reloadMinecraftResources() {
        if (applying) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        applying = true;
        try {
            mc.refreshResources();
            LOGGER.info("[AyanamiCosmetics] Resource manager reloaded");
        } catch (Exception e) {
            LOGGER.warn("[AyanamiCosmetics] Failed to reload resource manager: {}", e.toString());
        } finally {
            applying = false;
        }
    }

    private static boolean setServerPackInstance(ResourcePackRepository repository, @Nullable IResourcePack pack) {
        Field field = resolveServerPackField();
        if (field == null) {
            return false;
        }
        try {
            field.set(repository, pack);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[AyanamiCosmetics] Reflection set failed for server resource pack slot: {}", e.toString());
            return false;
        }
    }

    @Nullable
    private static Field resolveServerPackField() {
        if (serverPackField != null) {
            return serverPackField;
        }
        if (fieldLookupFailed) {
            return null;
        }
        try {
            // MCP / common names for ResourcePackRepository.serverResourcePack
            serverPackField = ReflectionHelper.findField(
                    ResourcePackRepository.class,
                    "serverResourcePack",
                    "field_148532_f"
            );
            serverPackField.setAccessible(true);
            return serverPackField;
        } catch (Exception primary) {
            for (int i = 0; i < SERVER_PACK_FIELD_NAMES.length; i++) {
                String name = SERVER_PACK_FIELD_NAMES[i];
                try {
                    Field field = ResourcePackRepository.class.getDeclaredField(name);
                    field.setAccessible(true);
                    serverPackField = field;
                    return serverPackField;
                } catch (Exception ignored) {
                }
            }
            // Fallback: locate the IResourcePack instance field that getResourcePackInstance returns.
            try {
                ResourcePackRepository sampleRepo = Minecraft.getMinecraft().getResourcePackRepository();
                IResourcePack current = sampleRepo.getServerResourcePack();
                Field[] fields = ResourcePackRepository.class.getDeclaredFields();
                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
                    if (IResourcePack.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object value = field.get(sampleRepo);
                        if (current == null || value == current) {
                            serverPackField = field;
                            return serverPackField;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            fieldLookupFailed = true;
            LOGGER.warn("[AyanamiCosmetics] Failed to load resource pack: cannot resolve ResourcePackRepository server pack field ({})", primary.toString());
            return null;
        }
    }

    public static void onClientDisconnect() {
        serverPackSeen = false;
        serverPackLoadedLogged = false;
        // Do not clear originalServerPack aggressively here; repository clear happens by vanilla.
    }
}
