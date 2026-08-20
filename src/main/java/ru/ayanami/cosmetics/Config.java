package ru.ayanami.cosmetics;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * Persistent client config. Selected pack is any ZIP/folder from resourcepacks/ — no fixed name.
 */
public final class Config {

    private static Configuration configuration;

    private static boolean overrideEnabled = false;
    /** Empty means "not chosen yet" — resolved to any available pack at runtime. */
    private static String selectedPackName = "";

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
                    "overrideEnabled",
                    Configuration.CATEGORY_GENERAL,
                    false,
                    "If true, the selected local resource pack overrides matching files from the server resource pack."
            );
            selectedPackName = configuration.getString(
                    "selectedPackName",
                    Configuration.CATEGORY_GENERAL,
                    "",
                    "Any ZIP or folder name inside .minecraft/resourcepacks used as the override pack. Leave empty to auto-pick the first available pack."
            );
            if (selectedPackName == null) {
                selectedPackName = "";
            } else {
                selectedPackName = selectedPackName.trim();
            }
        } catch (Exception e) {
            AyanamiCosmetics.LOGGER.warn("[AyanamiCosmetics] Failed to load config, using defaults: {}", e.toString());
            overrideEnabled = false;
            selectedPackName = "";
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }

    public static void save() {
        if (configuration == null) {
            return;
        }
        try {
            configuration.get(
                    Configuration.CATEGORY_GENERAL,
                    "overrideEnabled",
                    false
            ).set(overrideEnabled);
            configuration.get(
                    Configuration.CATEGORY_GENERAL,
                    "selectedPackName",
                    ""
            ).set(selectedPackName == null ? "" : selectedPackName);
            configuration.save();
        } catch (Exception e) {
            AyanamiCosmetics.LOGGER.warn("[AyanamiCosmetics] Failed to save config: {}", e.toString());
        }
    }

    public static boolean isOverrideEnabled() {
        return overrideEnabled;
    }

    public static void setOverrideEnabled(boolean enabled) {
        overrideEnabled = enabled;
        save();
    }

    public static String getSelectedPackName() {
        return selectedPackName == null ? "" : selectedPackName;
    }

    public static void setSelectedPackName(String packName) {
        if (packName == null) {
            selectedPackName = "";
        } else {
            selectedPackName = packName.trim();
        }
        save();
    }
}
