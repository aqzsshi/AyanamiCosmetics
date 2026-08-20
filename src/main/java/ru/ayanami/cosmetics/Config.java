package ru.ayanami.cosmetics;

import net.minecraftforge.common.config.Configuration;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Client config: multi-pack stack, favorites and per-server IP profiles.
 */
public final class Config {

    private static final String CAT_GENERAL = Configuration.CATEGORY_GENERAL;
    private static final String CAT_SERVERS = "server_profiles";

    private static Configuration configuration;

    private static boolean overrideEnabled = false;
    /** Highest priority first. */
    private static final List<String> activePacks = new ArrayList<String>();
    private static final List<String> favorites = new ArrayList<String>();
    /** Entries: host|pack1,pack2|true/false */
    private static final List<String> serverProfiles = new ArrayList<String>();
    private static boolean autoApplyServerProfile = true;

    private Config() {
    }

    public static void init(File configFile) {
        configuration = new Configuration(configFile);
        load();
    }

    public static void load() {
        if (configuration == null) {
            return;
        }
        try {
            configuration.load();
            overrideEnabled = configuration.getBoolean(
                    "overrideEnabled", CAT_GENERAL, false,
                    "Master switch for resource pack override.");
            autoApplyServerProfile = configuration.getBoolean(
                    "autoApplyServerProfile", CAT_GENERAL, true,
                    "Automatically apply the saved pack stack when joining a known server IP.");

            String[] packs = configuration.getStringList(
                    "activePacks", CAT_GENERAL, new String[0],
                    "Override packs in priority order (first = highest). Any ZIP/folder in resourcepacks/.");
            activePacks.clear();
            activePacks.addAll(sanitizeNames(packs));

            // Migrate legacy single pack setting.
            String legacy = configuration.getString(
                    "selectedPackName", CAT_GENERAL, "",
                    "Legacy single pack name (migrated into activePacks).");
            if (activePacks.isEmpty() && legacy != null && !legacy.trim().isEmpty()) {
                activePacks.add(legacy.trim());
            }

            String[] favs = configuration.getStringList(
                    "favorites", CAT_GENERAL, new String[0],
                    "Pinned favorite pack names shown at the top of the menu.");
            favorites.clear();
            favorites.addAll(sanitizeNames(favs));

            String[] profiles = configuration.getStringList(
                    "profiles", CAT_SERVERS, new String[0],
                    "Per-server profiles: host|pack1,pack2|true  (true/false = override enabled)");
            serverProfiles.clear();
            for (int i = 0; i < profiles.length; i++) {
                if (profiles[i] != null && profiles[i].trim().length() > 0) {
                    serverProfiles.add(profiles[i].trim());
                }
            }
        } catch (Exception e) {
            AyanamiCosmetics.LOGGER.warn("[AyanamiCosmetics] Failed to load config: {}", e.toString());
            overrideEnabled = false;
            activePacks.clear();
            favorites.clear();
            serverProfiles.clear();
            autoApplyServerProfile = true;
        } finally {
            save();
        }
    }

    public static void save() {
        if (configuration == null) {
            return;
        }
        try {
            configuration.get(CAT_GENERAL, "overrideEnabled", false).set(overrideEnabled);
            configuration.get(CAT_GENERAL, "autoApplyServerProfile", true).set(autoApplyServerProfile);
            configuration.get(CAT_GENERAL, "activePacks", new String[0]).set(activePacks.toArray(new String[0]));
            configuration.get(CAT_GENERAL, "favorites", new String[0]).set(favorites.toArray(new String[0]));
            configuration.get(CAT_GENERAL, "selectedPackName", "").set(activePacks.isEmpty() ? "" : activePacks.get(0));
            configuration.get(CAT_SERVERS, "profiles", new String[0]).set(serverProfiles.toArray(new String[0]));
            configuration.save();
        } catch (Exception e) {
            AyanamiCosmetics.LOGGER.warn("[AyanamiCosmetics] Failed to save config: {}", e.toString());
        }
    }

    private static List<String> sanitizeNames(String[] names) {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                if (names[i] != null) {
                    String n = names[i].trim();
                    if (!n.isEmpty()) {
                        set.add(n);
                    }
                }
            }
        }
        return new ArrayList<String>(set);
    }

    public static boolean isOverrideEnabled() {
        return overrideEnabled;
    }

    public static void setOverrideEnabled(boolean enabled) {
        overrideEnabled = enabled;
        save();
    }

    public static boolean isAutoApplyServerProfile() {
        return autoApplyServerProfile;
    }

    public static void setAutoApplyServerProfile(boolean value) {
        autoApplyServerProfile = value;
        save();
    }

    public static List<String> getActivePacks() {
        return Collections.unmodifiableList(activePacks);
    }

    public static void setActivePacks(List<String> packs) {
        activePacks.clear();
        if (packs != null) {
            activePacks.addAll(sanitizeNames(packs.toArray(new String[0])));
        }
        save();
    }

    public static void addActivePack(String packName) {
        if (packName == null || packName.trim().isEmpty()) {
            return;
        }
        String name = packName.trim();
        activePacks.remove(name);
        // Add to end (lower priority) by default; GUI can move up.
        activePacks.add(name);
        save();
    }

    public static void removeActivePack(String packName) {
        if (packName == null) {
            return;
        }
        activePacks.remove(packName.trim());
        // also try case-insensitive
        for (int i = activePacks.size() - 1; i >= 0; i--) {
            if (activePacks.get(i).equalsIgnoreCase(packName.trim())) {
                activePacks.remove(i);
            }
        }
        save();
    }

    public static boolean isActivePack(String packName) {
        if (packName == null) {
            return false;
        }
        for (int i = 0; i < activePacks.size(); i++) {
            if (activePacks.get(i).equalsIgnoreCase(packName)) {
                return true;
            }
        }
        return false;
    }

    public static void moveActivePack(String packName, int delta) {
        if (packName == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < activePacks.size(); i++) {
            if (activePacks.get(i).equalsIgnoreCase(packName)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        int target = index + delta;
        if (target < 0 || target >= activePacks.size()) {
            return;
        }
        String item = activePacks.remove(index);
        activePacks.add(target, item);
        save();
    }

    /** Compatibility: first active pack, or empty. */
    public static String getSelectedPackName() {
        return activePacks.isEmpty() ? "" : activePacks.get(0);
    }

    public static void setSelectedPackName(String packName) {
        if (packName == null || packName.trim().isEmpty()) {
            return;
        }
        String name = packName.trim();
        activePacks.remove(name);
        for (int i = activePacks.size() - 1; i >= 0; i--) {
            if (activePacks.get(i).equalsIgnoreCase(name)) {
                activePacks.remove(i);
            }
        }
        activePacks.add(0, name);
        save();
    }

    public static List<String> getFavorites() {
        return Collections.unmodifiableList(favorites);
    }

    public static boolean isFavorite(String packName) {
        if (packName == null) {
            return false;
        }
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).equalsIgnoreCase(packName)) {
                return true;
            }
        }
        return false;
    }

    public static void toggleFavorite(String packName) {
        if (packName == null || packName.trim().isEmpty()) {
            return;
        }
        String name = packName.trim();
        if (isFavorite(name)) {
            for (int i = favorites.size() - 1; i >= 0; i--) {
                if (favorites.get(i).equalsIgnoreCase(name)) {
                    favorites.remove(i);
                }
            }
        } else {
            favorites.add(0, name);
        }
        save();
    }

    public static String normalizeHost(String raw) {
        if (raw == null) {
            return "";
        }
        String host = raw.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("localhost") || host.equals("127.0.0.1")) {
            return "localhost";
        }
        // strip trailing port for matching: example.com:25565 -> example.com
        // but keep if it's IPv6 later — for 1.12 servers usually host:port
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(']') < 0) {
            String maybePort = host.substring(colon + 1);
            boolean digits = true;
            for (int i = 0; i < maybePort.length(); i++) {
                if (!Character.isDigit(maybePort.charAt(i))) {
                    digits = false;
                    break;
                }
            }
            if (digits) {
                host = host.substring(0, colon);
            }
        }
        return host;
    }

    public static ServerProfile getServerProfile(String hostRaw) {
        String host = normalizeHost(hostRaw);
        if (host.isEmpty()) {
            return null;
        }
        for (int i = 0; i < serverProfiles.size(); i++) {
            ServerProfile profile = ServerProfile.parse(serverProfiles.get(i));
            if (profile != null && profile.host.equals(host)) {
                return profile;
            }
        }
        return null;
    }

    public static void saveServerProfile(String hostRaw, List<String> packs, boolean enabled) {
        String host = normalizeHost(hostRaw);
        if (host.isEmpty()) {
            return;
        }
        ServerProfile profile = new ServerProfile(host, packs, enabled);
        for (int i = serverProfiles.size() - 1; i >= 0; i--) {
            ServerProfile existing = ServerProfile.parse(serverProfiles.get(i));
            if (existing != null && existing.host.equals(host)) {
                serverProfiles.remove(i);
            }
        }
        serverProfiles.add(profile.serialize());
        save();
        AyanamiCosmetics.LOGGER.info("[AyanamiCosmetics] Saved server profile for {}", host);
    }

    public static void deleteServerProfile(String hostRaw) {
        String host = normalizeHost(hostRaw);
        for (int i = serverProfiles.size() - 1; i >= 0; i--) {
            ServerProfile existing = ServerProfile.parse(serverProfiles.get(i));
            if (existing != null && existing.host.equals(host)) {
                serverProfiles.remove(i);
            }
        }
        save();
    }

    public static final class ServerProfile {
        public final String host;
        public final List<String> packs;
        public final boolean overrideEnabled;

        public ServerProfile(String host, List<String> packs, boolean overrideEnabled) {
            this.host = host;
            this.packs = packs == null ? Collections.<String>emptyList() : new ArrayList<String>(packs);
            this.overrideEnabled = overrideEnabled;
        }

        public String serialize() {
            StringBuilder sb = new StringBuilder();
            sb.append(host).append('|');
            for (int i = 0; i < packs.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(packs.get(i));
            }
            sb.append('|').append(overrideEnabled ? "true" : "false");
            return sb.toString();
        }

        @Nullable
        public static ServerProfile parse(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            String[] parts = raw.split("\\|", -1);
            if (parts.length < 2) {
                return null;
            }
            String host = normalizeHost(parts[0]);
            List<String> packs = new ArrayList<String>();
            if (parts[1] != null && !parts[1].trim().isEmpty()) {
                String[] split = parts[1].split(",");
                packs.addAll(sanitizeNames(split));
            }
            boolean enabled = parts.length < 3 || !"false".equalsIgnoreCase(parts[2].trim());
            if (host.isEmpty()) {
                return null;
            }
            return new ServerProfile(host, packs, enabled);
        }
    }
}
