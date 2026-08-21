package ru.ayanami.cosmetics.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.ayanami.cosmetics.TweakOS;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Loads cosmetics catalog entries from config/tweakos/catalog/
 * Each entry is a folder with meta.json (+ optional model.json / textures).
 */
public final class CatalogManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<CatalogEntry> ENTRIES = new ArrayList<CatalogEntry>();

    private CatalogManager() {
    }

    public static File getModConfigDir() {
        return new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "config/tweakos");
    }

    public static File getCatalogDir() {
        return new File(getModConfigDir(), "catalog");
    }

    public static File getOverridePackDir() {
        return new File(getModConfigDir(), "override_pack");
    }

    public static File getBasePackFile() {
        return new File(getModConfigDir(), "base_pack.zip");
    }

    public static void initFolders() {
        File root = getModConfigDir();
        if (!root.exists()) {
            root.mkdirs();
        }
        File catalog = getCatalogDir();
        if (!catalog.exists()) {
            catalog.mkdirs();
        }
        File override = getOverridePackDir();
        if (!override.exists()) {
            override.mkdirs();
            // Minimal pack.mcmeta so FolderResourcePack is valid.
            try {
                java.io.FileWriter w = new java.io.FileWriter(new File(override, "pack.mcmeta"));
                w.write("{\"pack\":{\"pack_format\":3,\"description\":\"TweakOS local overrides\"}}");
                w.close();
            } catch (Exception ignored) {
            }
        }
        ensureExampleEntry();
        reload();
    }

    private static void ensureExampleEntry() {
        File example = new File(getCatalogDir(), "demo_diamond_sword");
        File meta = new File(example, "meta.json");
        if (meta.exists()) {
            return;
        }
        example.mkdirs();
        try {
            java.io.FileWriter w = new java.io.FileWriter(meta);
            w.write("{\n");
            w.write("  \"id\": \"demo_diamond_sword\",\n");
            w.write("  \"name\": \"Demo Diamond Sword\",\n");
            w.write("  \"category\": \"weapons\",\n");
            w.write("  \"replacePath\": \"assets/minecraft/models/item/diamond_sword.json\",\n");
            w.write("  \"iconItem\": \"minecraft:diamond_sword\",\n");
            w.write("  \"description\": \"Example catalog entry. Replace model.json to customize.\"\n");
            w.write("}\n");
            w.close();
        } catch (Exception e) {
            TweakOS.LOGGER.warn("[TweakOS] Could not create example catalog entry: {}", e.toString());
        }
    }

    public static synchronized void reload() {
        ENTRIES.clear();
        File catalog = getCatalogDir();
        File[] folders = catalog.listFiles();
        if (folders == null) {
            return;
        }
        for (int i = 0; i < folders.length; i++) {
            File folder = folders[i];
            if (!folder.isDirectory()) {
                continue;
            }
            File metaFile = new File(folder, "meta.json");
            if (!metaFile.isFile()) {
                continue;
            }
            CatalogEntry entry = readEntry(folder, metaFile);
            if (entry != null) {
                ENTRIES.add(entry);
            }
        }
        Collections.sort(ENTRIES, new Comparator<CatalogEntry>() {
            @Override
            public int compare(CatalogEntry a, CatalogEntry b) {
                int c = a.category.compareToIgnoreCase(b.category);
                if (c != 0) {
                    return c;
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        TweakOS.LOGGER.info("[TweakOS] Catalog loaded: {} entries", ENTRIES.size());
    }

    @Nullable
    private static CatalogEntry readEntry(File folder, File metaFile) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(metaFile), StandardCharsets.UTF_8));
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            CatalogEntry entry = new CatalogEntry();
            entry.id = json.has("id") ? json.get("id").getAsString() : folder.getName();
            entry.name = json.has("name") ? json.get("name").getAsString() : folder.getName();
            entry.category = json.has("category") ? json.get("category").getAsString() : "misc";
            entry.replacePath = json.has("replacePath") ? json.get("replacePath").getAsString() : "";
            entry.iconItem = json.has("iconItem") ? json.get("iconItem").getAsString() : "minecraft:paper";
            entry.description = json.has("description") ? json.get("description").getAsString() : "";
            entry.folder = folder;
            entry.modelFile = new File(folder, "model.json");
            return entry;
        } catch (Exception e) {
            TweakOS.LOGGER.warn("[TweakOS] Bad catalog meta in {}: {}", folder.getName(), e.toString());
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static List<CatalogEntry> getEntries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static List<CatalogEntry> getEntriesByCategory(String category) {
        if (category == null || "all".equalsIgnoreCase(category)) {
            return getEntries();
        }
        List<CatalogEntry> list = new ArrayList<CatalogEntry>();
        for (int i = 0; i < ENTRIES.size(); i++) {
            CatalogEntry e = ENTRIES.get(i);
            if (e.category.equalsIgnoreCase(category)) {
                list.add(e);
            }
        }
        return list;
    }

    public static List<String> listCategories() {
        List<String> cats = new ArrayList<String>();
        cats.add("all");
        for (int i = 0; i < ENTRIES.size(); i++) {
            String c = ENTRIES.get(i).category.toLowerCase(Locale.ROOT);
            if (!cats.contains(c)) {
                cats.add(c);
            }
        }
        return cats;
    }

    public static final class CatalogEntry {
        public String id;
        public String name;
        public String category;
        public String replacePath;
        public String iconItem;
        public String description;
        public File folder;
        public File modelFile;
    }
}
