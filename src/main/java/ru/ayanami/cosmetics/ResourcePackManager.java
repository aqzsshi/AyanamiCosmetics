package ru.ayanami.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/**
 * Loads one or more local packs and stacks them over the server resource pack.
 * Also applies per-server IP profiles on connect.
 */
public final class ResourcePackManager {

    private static final Logger LOGGER = TweakOs.LOGGER;

    private static final String[] SERVER_PACK_FIELD_NAMES = new String[] {
            "serverResourcePack",
            "resourcePackInstance",
            "field_148532_f"
    };

    private static Field serverPackField;
    private static boolean fieldLookupFailed;

    private static IResourcePack originalServerPack;
    private static List<IResourcePack> cachedOverridePacks = new ArrayList<IResourcePack>();
    private static List<String> cachedOverrideNames = new ArrayList<String>();
    private static String cachedStackKey = "";

    private static boolean applying;
    private static boolean serverPackSeen;
    private static boolean serverPackLoadedLogged;
    private static String lastAppliedHost = "";

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

    public static boolean isOverrideApplied() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourcePackRepository() == null) {
            return false;
        }
        return mc.getResourcePackRepository().getServerResourcePack() instanceof ResourcePackOverride;
    }

    public static File getResourcePacksDirectory() {
        return new File(Minecraft.getMinecraft().mcDataDir, "resourcepacks");
    }

    @Nullable
    public static String getCurrentServerHost() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return null;
        }
        ServerData data = mc.getCurrentServerData();
        if (data != null && data.serverIP != null && !data.serverIP.trim().isEmpty()) {
            return Config.normalizeHost(data.serverIP);
        }
        if (mc.isSingleplayer()) {
            return "singleplayer";
        }
        return null;
    }

    /**
     * Apply saved profile for the current server IP (packs + override flag).
     */
    public static synchronized void applyServerProfileIfAny() {
        if (!Config.isAutoApplyServerProfile()) {
            return;
        }
        String host = getCurrentServerHost();
        if (host == null || host.isEmpty()) {
            return;
        }
        Config.ServerProfile profile = Config.getServerProfile(host);
        if (profile == null) {
            LOGGER.info("[TweakOs] No saved profile for server {}", host);
            return;
        }
        if (host.equals(lastAppliedHost)
                && profile.packs.equals(Config.getActivePacks())
                && profile.overrideEnabled == Config.isOverrideEnabled()) {
            return;
        }
        LOGGER.info("[TweakOs] Applying server profile for {}: packs={}, override={}",
                host, profile.packs, profile.overrideEnabled);
        Config.setActivePacks(profile.packs);
        Config.setOverrideEnabled(profile.overrideEnabled);
        lastAppliedHost = host;
        clearPackCache();
        syncOverrideState(true);
    }

    public static synchronized void saveProfileForCurrentServer() {
        String host = getCurrentServerHost();
        if (host == null || host.isEmpty()) {
            LOGGER.warn("[TweakOs] Cannot save profile: not connected to a server");
            return;
        }
        Config.saveServerProfile(host, Config.getActivePacks(), Config.isOverrideEnabled());
    }

    @Nullable
    public static File resolvePackFileByName(@Nullable String name) {
        return findPackFile(name);
    }

    @Nullable
    public static IResourcePack createPackInstance(@Nullable String name) {
        File packFile = findPackFile(name);
        if (packFile == null) {
            return null;
        }
        try {
            if (packFile.isDirectory()) {
                return new FolderResourcePack(packFile);
            }
            return new FileResourcePack(packFile);
        } catch (Exception e) {
            LOGGER.warn("[TweakOs] Failed to open pack for preview: {}", e.toString());
            return null;
        }
    }

    @Nullable
    public static File resolveSelectedPackFile() {
        ensureSelectedPackExists();
        return findPackFile(Config.getSelectedPackName());
    }

    @Nullable
    private static File findPackFile(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        File dir = getResourcePacksDirectory();
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        File direct = new File(dir, name);
        if (isValidPackPath(direct)) {
            return direct;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        String wanted = name.trim();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.getName().equalsIgnoreCase(wanted) && isValidPackPath(file)) {
                return file;
            }
        }

        if (!wanted.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            File zip = new File(dir, wanted + ".zip");
            if (isValidPackPath(zip)) {
                return zip;
            }
        } else {
            String withoutZip = wanted.substring(0, wanted.length() - 4);
            File folder = new File(dir, withoutZip);
            if (isValidPackPath(folder)) {
                return folder;
            }
        }
        return null;
    }

    private static boolean isValidPackPath(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        if (file.isFile()) {
            return file.getName().toLowerCase(Locale.ROOT).endsWith(".zip");
        }
        return file.isDirectory();
    }

    public static void ensureSelectedPackExists() {
        List<String> active = new ArrayList<String>(Config.getActivePacks());
        List<String> valid = new ArrayList<String>();
        for (int i = 0; i < active.size(); i++) {
            File file = findPackFile(active.get(i));
            if (file != null) {
                valid.add(file.getName());
            }
        }
        if (!valid.equals(active)) {
            Config.setActivePacks(valid);
        }
        if (!Config.getActivePacks().isEmpty()) {
            return;
        }
        List<String> available = listAvailablePackNames();
        if (!available.isEmpty()) {
            Config.setActivePacks(Collections.singletonList(available.get(0)));
            LOGGER.info("[TweakOs] Auto-selected available pack: {}", available.get(0));
        }
    }

    /** Favorites first, then alphabetical. */
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
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                names.add(file.getName());
            } else if (file.isDirectory() && !file.getName().startsWith(".")) {
                names.add(file.getName());
            }
        }
        Collections.sort(names, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                boolean fa = Config.isFavorite(a);
                boolean fb = Config.isFavorite(b);
                if (fa != fb) {
                    return fa ? -1 : 1;
                }
                return String.CASE_INSENSITIVE_ORDER.compare(a, b);
            }
        });
        return names;
    }

    private static void clearPackCache() {
        cachedOverridePacks = new ArrayList<IResourcePack>();
        cachedOverrideNames = new ArrayList<String>();
        cachedStackKey = "";
    }

    private static String buildStackKey(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            File f = findPackFile(names.get(i));
            sb.append(names.get(i));
            if (f != null) {
                sb.append('@').append(f.lastModified());
            }
        }
        return sb.toString();
    }

    /**
     * Ensures local config/tweakos/override_pack is applied as highest-priority override layer.
     */
    public static synchronized void ensureOverridePackInStack() {
        // Actual injection happens in loadActiveOverridePacks via alwaysIncludeOverrideDir().
        clearPackCache();
        if (!Config.isOverrideEnabled()) {
            Config.setOverrideEnabled(true);
        }
    }

    private static void alwaysIncludeOverrideDir(List<IResourcePack> packs, List<String> names) {
        try {
            File overrideDir = ru.ayanami.cosmetics.catalog.CatalogManager.getOverridePackDir();
            if (overrideDir.isDirectory()) {
                File assets = new File(overrideDir, "assets");
                if (assets.isDirectory() || new File(overrideDir, "pack.mcmeta").isFile()) {
                    packs.add(0, new FolderResourcePack(overrideDir));
                    names.add(0, "override_pack");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[TweakOs] Could not attach override_pack: {}", e.toString());
        }
    }

    /**
     * Loads all active packs in priority order. Returns empty list if none valid.
     */
    public static synchronized List<IResourcePack> loadActiveOverridePacks(boolean forceReload) {
        ensureSelectedPackExists();
        List<String> names = new ArrayList<String>(Config.getActivePacks());
        String key = "ovr|" + buildStackKey(names);
        if (!forceReload && key.equals(cachedStackKey) && !cachedOverridePacks.isEmpty()) {
            return cachedOverridePacks;
        }

        List<IResourcePack> packs = new ArrayList<IResourcePack>();
        List<String> resolvedNames = new ArrayList<String>();
        for (int i = 0; i < names.size(); i++) {
            // skip reserved name
            if ("override_pack".equalsIgnoreCase(names.get(i))) {
                continue;
            }
            File packFile = findPackFile(names.get(i));
            if (packFile == null) {
                LOGGER.warn("[TweakOs] Failed to load resource pack: file not found ({})", names.get(i));
                continue;
            }
            String validationError = validatePack(packFile);
            if (validationError != null) {
                LOGGER.warn("[TweakOs] Failed to load resource pack: {}", validationError);
                continue;
            }
            try {
                IResourcePack pack = packFile.isDirectory()
                        ? new FolderResourcePack(packFile)
                        : new FileResourcePack(packFile);
                pack.getResourceDomains();
                packs.add(pack);
                resolvedNames.add(packFile.getName());
                LOGGER.info("[TweakOs] User resource pack selected: {}", packFile.getName());
            } catch (Exception e) {
                LOGGER.warn("[TweakOs] Failed to load resource pack: {}", e.toString());
            }
        }

        alwaysIncludeOverrideDir(packs, resolvedNames);

        cachedOverridePacks = packs;
        cachedOverrideNames = resolvedNames;
        cachedStackKey = key;
        return packs;
    }

    @Nullable
    private static String validatePack(File packFile) {
        if (packFile.isDirectory()) {
            File assets = new File(packFile, "assets");
            File mcmeta = new File(packFile, "pack.mcmeta");
            if (!assets.isDirectory() && !mcmeta.isFile()) {
                return "invalid folder structure (missing assets/ and pack.mcmeta) (" + packFile.getName() + ")";
            }
            if (!mcmeta.isFile()) {
                LOGGER.warn("[TweakOs] Resource pack {} has no pack.mcmeta; continuing anyway", packFile.getName());
            }
            return null;
        }

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
                LOGGER.info("[TweakOs] Server resource pack detected");
                serverPackSeen = true;
            }
            originalServerPack = current;
            if (!serverPackLoadedLogged) {
                LOGGER.info("[TweakOs] Server resource pack loaded");
                serverPackLoadedLogged = true;
            }
        } else if (current instanceof ResourcePackOverride) {
            ResourcePackOverride wrapped = (ResourcePackOverride) current;
            originalServerPack = wrapped.getBasePack();
            serverPackSeen = true;
            if (!serverPackLoadedLogged) {
                LOGGER.info("[TweakOs] Server resource pack loaded");
                serverPackLoadedLogged = true;
            }
        } else if (current == null) {
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

        List<IResourcePack> userPacks = loadActiveOverridePacks(false);
        if (userPacks.isEmpty()) {
            if (current instanceof ResourcePackOverride) {
                restoreOriginalServerPack(repository, reloadResources);
            }
            return;
        }

        IResourcePack base = unwrapBase(current);
        if (base == null) {
            return;
        }

        boolean needsApply = !(current instanceof ResourcePackOverride);
        if (current instanceof ResourcePackOverride) {
            ResourcePackOverride existing = (ResourcePackOverride) current;
            if (existing.getBasePack() != base
                    || !existing.getOverrideNames().equals(cachedOverrideNames)
                    || existing.getOverridePacks().size() != userPacks.size()) {
                needsApply = true;
            }
        }

        if (needsApply) {
            applyOverride(repository, userPacks, cachedOverrideNames, base, reloadResources);
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

    private static void applyOverride(ResourcePackRepository repository, List<IResourcePack> userPacks, List<String> names, IResourcePack basePack, boolean reloadResources) {
        ResourcePackOverride wrapper = new ResourcePackOverride(userPacks, names, basePack);
        LOGGER.info("[TweakOs] Applying user override");
        if (!setServerPackInstance(repository, wrapper)) {
            LOGGER.warn("[TweakOs] Failed to load resource pack: could not install override wrapper via reflection");
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
            LOGGER.warn("[TweakOs] Failed to restore original server resource pack");
            return;
        }
        if (reloadResources) {
            reloadMinecraftResources();
        }
    }

    public static synchronized void setOverrideEnabled(boolean enabled) {
        Config.setOverrideEnabled(enabled);
        if (enabled) {
            LOGGER.info("[TweakOs] Override enabled");
        } else {
            LOGGER.info("[TweakOs] Override disabled");
        }
        syncOverrideState(true);
    }

    public static synchronized void toggleOverride() {
        setOverrideEnabled(!Config.isOverrideEnabled());
    }

    public static synchronized void selectPack(String packName) {
        Config.setSelectedPackName(packName);
        clearPackCache();
        LOGGER.info("[TweakOs] User resource pack selected: {}", packName);
        if (Config.isOverrideEnabled()) {
            syncOverrideState(true);
        }
    }

    public static synchronized void addPackToStack(String packName) {
        Config.addActivePack(packName);
        clearPackCache();
        LOGGER.info("[TweakOs] Added pack to stack: {}", packName);
        if (Config.isOverrideEnabled()) {
            syncOverrideState(true);
        }
    }

    public static synchronized void removePackFromStack(String packName) {
        Config.removeActivePack(packName);
        clearPackCache();
        LOGGER.info("[TweakOs] Removed pack from stack: {}", packName);
        if (Config.isOverrideEnabled()) {
            syncOverrideState(true);
        }
    }

    public static synchronized void reloadResourcesFromGui() {
        clearPackCache();
        loadActiveOverridePacks(true);
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
            LOGGER.info("[TweakOs] Resource manager reloaded");
        } catch (Exception e) {
            LOGGER.warn("[TweakOs] Failed to reload resource manager: {}", e.toString());
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
            LOGGER.warn("[TweakOs] Reflection set failed for server resource pack slot: {}", e.toString());
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
            serverPackField = ReflectionHelper.findField(
                    ResourcePackRepository.class,
                    "serverResourcePack",
                    "field_148532_f"
            );
            serverPackField.setAccessible(true);
            return serverPackField;
        } catch (Exception primary) {
            for (int i = 0; i < SERVER_PACK_FIELD_NAMES.length; i++) {
                try {
                    Field field = ResourcePackRepository.class.getDeclaredField(SERVER_PACK_FIELD_NAMES[i]);
                    field.setAccessible(true);
                    serverPackField = field;
                    return serverPackField;
                } catch (Exception ignored) {
                }
            }
            fieldLookupFailed = true;
            LOGGER.warn("[TweakOs] Failed to load resource pack: cannot resolve server pack field ({})", primary.toString());
            return null;
        }
    }

    public static void onClientDisconnect() {
        serverPackSeen = false;
        serverPackLoadedLogged = false;
        lastAppliedHost = "";
    }
}
