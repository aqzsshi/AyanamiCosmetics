package ru.ayanami.cosmetics;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * Persistent client config for AyanamiCosmetics.
 */
public final class Config {

    public static final String DEFAULT_PACK_NAME = "ayanacosmetics";

    private static Configuration configuration;

    private static boolean overrideEnabled = false;
    private static String selectedPackName = DEFAULT_PACK_NAME;

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
                    DEFAULT_PACK_NAME,
                    "ZIP file name inside .minecraft/resourcepacks used as the override pack."
            );
            if (selectedPackName == null || selectedPackName.trim().isEmpty()) {
                selectedPackName = DEFAULT_PACK_NAME;
            }
        } catch (Exception e) {
            AyanamiCosmetics.LOGGER.warn("[AyanamiCosmetics] Failed to load config, using defaults: {}", e.toString());
            overrideEnabled = false;
            selectedPackName = DEFAULT_PACK_NAME;
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
                    DEFAULT_PACK_NAME
            ).set(selectedPackName);
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
        return selectedPackName;
    }

    public static void setSelectedPackName(String packName) {
        if (packName == null || packName.trim().isEmpty()) {
            selectedPackName = DEFAULT_PACK_NAME;
        } else {
            selectedPackName = packName.trim();
        }
        save();
    }
}
